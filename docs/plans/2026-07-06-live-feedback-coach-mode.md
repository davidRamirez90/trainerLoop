# Live Feedback Coach Mode — Implementation Plan

**Date:** 2026-07-06
**Status:** Phase 1 core implemented 2026-07-06 (see §17 for what shipped and what's pending)
**Target platform:** Kotlin Android app (per `docs/plans/2026-06-16-kotlin-android-migration.md`)
**Builds on:** existing v1 Kotlin coach engine (`android/.../domain/CoachEngine.kt`), workout clock (`domain/WorkoutClock.kt`), summary math (`domain/WorkoutSummaryMath.kt`), data models (`data/model/*`), coach profiles (`docs/coach-profiles.md`), coach rules (`docs/coach-rules.md`), data model docs (`docs/data-model.md`)

---

## 1. Overview

Live Feedback Coach Mode turns the existing suggestion engine into a full virtual
coach: a streaming decision engine that understands the workout's structure and
intent, models the athlete's expected physiological response, continuously scores
execution and fatigue, and delivers timely, non-repetitive coaching feedback
during the session.

### Relationship to the existing system

Trainer Loop already has:

- **Telemetry pipeline** — 1 Hz `TelemetrySample` (power, cadence, HR, dropout /
  lag-compensation flags) from FTMS/HR BLE managers. Speed is parsed from FTMS
  (`IndoorBikeData.speedKph`) but not carried into `TelemetrySample`.
- **Workout model** — `WorkoutSegment` (Step/Ramp/FreeRide) with `phase`
  (WARMUP/WORK/RECOVERY/COOLDOWN), `isWork`, `targetRange`, and optional
  `targetCadence: IntRange` on Step/Ramp.
- **v1 coach engine** — `CoachEngine.kt`: rule-based *actionable suggestions*
  (adjust intensity, extend recovery, skip intervals) with accept/reject,
  cooldowns, pending-suggestion gating, and coach profiles that define
  thresholds (`CoachRules`), interventions, and voice.
- **Session persistence** — Room `SessionData` stores the full 1 Hz sample
  array (`samplesJson`) per session, plus FIT export via `FitEncoder`.
- **Foreground service** — `app/WorkoutForegroundService.kt` already exists;
  the coach pipeline rides it.

Live Feedback Coach Mode **subsumes** the v1 engine rather than replacing it:

- The v1 actionable suggestions become one feedback category
  (`WORKOUT_MODIFICATION`) inside a broader taxonomy.
- The v1 threshold config in `CoachProfile` is retained and extended.
- Everything new (workout interpreter, expectation engine, fatigue model,
  feedback arbiter) wraps around the existing rule logic.

### Goals

1. Feedback that reflects *workout intent*, not just instantaneous numbers.
2. Expected-vs-actual physiological comparison (HR, power, cadence).
3. Session-level fatigue and recovery tracking that shapes later feedback.
4. A disciplined delivery layer: right message, right moment, never spammy.
5. All of it on-device, offline, at negligible battery cost.

### Non-goals (this feature)

- Multi-day training-load planning (CTL/ATL/TSB) — session history feeds
  personalization later, but plan-level periodization is out of scope.
- Outdoor rides. ERG/indoor only; the interpreter assumes a prescription exists.
- Voice *input* / conversational coaching (Phase 4 explores output only).

---

## 2. High-Level Architecture

Seven layers, unidirectional data flow, all pure Kotlin except the edges:

```
 BLE (FTMS/HR)          Workout file / library
       │                          │
       ▼                          ▼
┌────────────────┐      ┌───────────────────┐
│ 1. Data        │      │ 2. Workout        │
│    Ingestion   │      │    Interpreter    │
│ (normalize,    │      │ (structure, sets, │
│  gap-fill,     │      │  intent, context) │
│  quality flags)│      └─────────┬─────────┘
└───────┬────────┘                │  WorkoutPlanModel (static, computed once)
        │ TelemetrySample (1 Hz)  │
        ▼                         ▼
┌─────────────────────────────────────────────┐
│ 3. Athlete State Model                      │
│ (rolling windows, derived metrics,          │
│  interval ledger, fatigue score,            │
│  data-confidence score)                     │
└───────────────────┬─────────────────────────┘
                    │ AthleteState (per tick)
                    ▼
┌─────────────────────────────────────────────┐
│ 4. Physiological Expectation Engine         │
│ (expected HR / power / cadence envelopes    │
│  for current + upcoming intervals)          │
└───────────────────┬─────────────────────────┘
                    │ ExpectationEnvelope
                    ▼
┌─────────────────────────────────────────────┐
│ 5. Real-Time Analytics Engine               │
│ (deviation detection, execution scoring,    │
│  trend detection, event derivation)         │
└───────────────────┬─────────────────────────┘
                    │ AnalysisEvent candidates
                    ▼
┌─────────────────────────────────────────────┐
│ 6. Feedback Decision Engine                 │
│ (candidate generation, ranking, cooldowns,  │
│  escalation, suppression, budget)           │
└───────────────────┬─────────────────────────┘
                    │ FeedbackItem (≤1 per arbitration cycle)
                    ▼
┌─────────────────────────────────────────────┐
│ 7. Feedback Delivery Layer                  │
│ (card UI, TTS, haptics, event log, Room)    │
└─────────────────────────────────────────────┘
```

**Threading model:** layers 3–6 run as a single deterministic pipeline invoked
once per clock tick (1 Hz) on a background dispatcher; output is published via
`StateFlow`/`SharedFlow` to the UI. No layer holds Android dependencies except
1 (BLE) and 7 (UI/TTS/Room), which keeps 2–6 unit-testable as pure Kotlin.

**Key interfaces between layers** (contract sketch, mirrors the style of
`docs/data-model.md`):

```kotlin
interface WorkoutInterpreter {
    fun interpret(workout: Workout, athlete: AthleteProfile): WorkoutPlanModel
}

interface AthleteStateModel {
    fun onSample(sample: TelemetrySample, ctx: IntervalContext): AthleteState
    fun onIntervalComplete(summary: IntervalSummary)
}

interface ExpectationEngine {
    fun expectationFor(ctx: IntervalContext, state: AthleteState): ExpectationEnvelope
}

interface AnalyticsEngine {
    fun analyze(state: AthleteState, envelope: ExpectationEnvelope,
                ctx: IntervalContext): List<AnalysisEvent>
}

interface FeedbackDecisionEngine {
    fun arbitrate(events: List<AnalysisEvent>, ledger: FeedbackLedger,
                  profile: CoachProfile): FeedbackItem?
}
```

---

## 3. Workout Interpreter

Runs **once at workout load** (plus re-runs on mid-session modification, e.g. an
accepted "extend recovery"). Produces a static `WorkoutPlanModel` that everything
downstream reads.

### 3.1 Segment classification

Each segment gets a `SegmentClass` inferred from `%FTP` (target mid ÷ athlete
FTP) and duration. Classification thresholds (Coggan-based, already consistent
with `trainingMetrics.ts` zones):

| Class | %FTP | Typical duration | Notes |
|---|---|---|---|
| WARMUP | any, position-based | first ramps/steps | first contiguous low-intensity block or explicit ramp up |
| RECOVERY | < 55% | between work | `isWork == false`, follows work |
| ENDURANCE | 56–75% | > 5 min | Z2 steady state |
| TEMPO | 76–87% | 8–30 min | Z3 |
| SWEET_SPOT | 88–94% | 8–30 min | upper Z3/lower Z4 |
| THRESHOLD | 95–105% | 5–30 min | Z4 |
| VO2MAX | 106–120% | 1–8 min | Z5 |
| ANAEROBIC | 121–150% | 30 s–2 min | Z6 |
| SPRINT | > 150% | < 30 s | Z7 / neuromuscular |
| COOLDOWN | any, position-based | last low block | trailing low-intensity block |
| FREE_RIDE | n/a | n/a | no expectations, minimal feedback |

Ramps classify by their *average* target; a ramp spanning classes is split at
zone boundaries internally for expectation purposes but stays one UI segment.

### 3.2 Set/block detection

Group segments into `IntervalSet`s: consecutive `(work, recovery)` pairs with
matching class and duration (± 10%) form one set (e.g. "5 × 4 min VO2max / 4 min
recovery"). This gives the engine:

- `blockNumber` / `blocksRemaining` within the set ("interval 3 of 5"),
- set-level comparisons ("cadence dropped over the last two intervals"),
- correct anchoring for "final interval" motivation and between-set feedback.

Detection is a simple linear pass with tolerance matching; unmatched work
segments become singleton sets.

### 3.3 Workout intent inference

Score-based classification over the whole plan (works for any imported
ERG/MRC/ZWO/JSON file, not just app-built workouts):

1. Compute planned time-in-class distribution, planned IF, planned TSS, and the
   dominant work class by weighted work time (weight = %FTP so short hard work
   counts).
2. Map to intent:

| Intent | Signal |
|---|---|
| RECOVERY | IF < 0.60, no work segments above Z2 |
| AEROBIC_ENDURANCE | ≥ 70% of time in ENDURANCE, IF 0.60–0.75 |
| TEMPO_SS | dominant work class TEMPO/SWEET_SPOT |
| THRESHOLD_DEV | dominant work class THRESHOLD |
| VO2_DEV | dominant work class VO2MAX |
| ANAEROBIC_CAP | dominant work class ANAEROBIC |
| NEUROMUSCULAR | dominant work class SPRINT |
| MIXED | no dominant class ≥ 50% of work time |

3. Intent drives feedback emphasis (see §8.4): e.g. for AEROBIC_ENDURANCE the
   coach cares about HR decoupling and steadiness and *discourages* surges; for
   VO2_DEV, moderate HR drift is *expected* and should not trigger fatigue
   warnings; for RECOVERY the coach polices *over*-riding, not under-riding.

### 3.4 IntervalContext (continuously updated, cheap)

Maintained per tick by joining clock position against `WorkoutPlanModel`:

```kotlin
data class IntervalContext(
    val segment: WorkoutSegment,
    val segmentClass: SegmentClass,
    val set: IntervalSet?,           // null for singletons
    val blockNumber: Int,            // 1-based within set
    val blocksRemaining: Int,
    val elapsedInSegmentSec: Int,
    val remainingInSegmentSec: Int,
    val workoutProgressPct: Double,  // by planned TSS, not just time
    val cumulativePlannedTss: Double,
    val cumulativeActualTss: Double,
    val isFinalWorkInterval: Boolean,
    val nextSegmentClass: SegmentClass?,
    val intent: WorkoutIntent,
)
```

`workoutProgressPct` weighted by planned TSS (already computable via
`trainingMetrics.ts` port) so "you're 60% through the *work*" is honest for
back-loaded workouts.

---

## 4. Athlete State Model

The continuously maintained session state. Everything here is incremental
(O(1) per sample) — no re-scanning the full sample array as the current
`CoachEngine.computeMetrics` does (it filters the whole sample list per window
per tick; fine at v1 scale, wrong foundation for a per-second pipeline).

### 4.1 Inputs

| Input | Source | Rate | Required? |
|---|---|---|---|
| Power (W) | FTMS Indoor Bike Data | 1 Hz | yes |
| Cadence (rpm) | FTMS | 1 Hz | yes (feedback degrades gracefully) |
| Heart rate (bpm) | HR BLE profile | 1 Hz | no (HR features disabled without it) |
| Speed (km/h) | FTMS (`IndoorBikeData.speedKph`, currently dropped before `TelemetrySample`) | 1 Hz | no (unused by coach; plumb through only if wanted for recording) |
| Trainer state | FTMS control status | event | yes (ERG vs resistance changes adherence semantics) |
| Target power / intensity offset | workout clock + user | event | yes |
| Clock (elapsed/active/segment position) | `WorkoutClock` | 1 Hz | yes |
| Pause/resume/seek events | `WorkoutClock` | event | yes (invalidate windows across seeks) |
| Athlete profile | `UserProfile` (has `ftp`, `weightKg`, `maxHr`, `restingHr`; **add `lthr: Int?`**, optionally `age`) | static | FTP yes; LTHR estimated from maxHR if absent |
| Sensor connection state | BLE managers | event | yes (drives confidence + dropout handling) |

### 4.2 Rolling windows

Ring-buffer backed rolling aggregates, updated per sample:

- **Power:** 3 s (instantaneous display / sprint detection), 30 s (adherence,
  NP rolling term), full-segment running mean.
- **HR:** 10 s smoothed value, 60 s slope (bpm/min), segment-start anchor,
  session HR baseline (median HR during final 2 min of warmup).
- **Cadence:** 10 s mean, 60 s slope, segment mean, per-interval means (for
  cross-interval trend).
- **NP accumulator:** 30 s rolling average → 4th-power running sum, maintained
  incrementally. `WorkoutSummaryMath.normalizedPower` already computes true NP
  but as an O(n·w) batch pass over all samples — reuse its math, not its shape;
  the state model needs the ring-buffer incremental form.

Windows are keyed to `activeSec` and flushed on pause > 30 s or any seek.

### 4.3 Interval ledger

On every work-segment completion, append an `IntervalRecord`:

```kotlin
data class IntervalRecord(
    val setId: String?, val blockNumber: Int, val segmentClass: SegmentClass,
    val targetMidWatts: Int, val avgPower: Double, val npPower: Double,
    val adherencePct: Double, val timeInTargetPct: Double,
    val powerCv: Double,                    // coefficient of variation
    val avgHr: Double?, val endHr: Double?, val hrDriftPct: Double?,
    val avgCadence: Double?, val cadenceSlope: Double?,
    val executionScore: Double,             // §6
    val recoveryAfter: RecoveryRecord?,     // filled when following recovery ends
)
```

`RecoveryRecord` captures HR at recovery start, HR after 60 s (→ HRR60), HR at
recovery end vs session baseline, and cadence behavior. The ledger (capped ~64)
is the substrate for all cross-interval feedback ("last two intervals…") and
for post-session persistence.

### 4.4 Fatigue score

A 0–100 composite updated per tick, blending (weights configurable per coach
profile; defaults shown):

| Component | Weight | Signal |
|---|---|---|
| HR drift | 0.30 | within-interval drift beyond expected (§5.1), EWMA across intervals |
| Aerobic decoupling | 0.25 | Pw:Hr — % change of (power/HR) first half vs second half of steady work; > 5% notable, > 8% high (endurance/tempo intents only) |
| Recovery deficit | 0.20 | HR at end of recoveries failing to return within expected band; trend across ledger |
| Cadence decline | 0.15 | negative cadence slope across same-class intervals (self-selected cadence drop is an RPE proxy) |
| Power instability | 0.10 | rising power CV at constant ERG target in same-class intervals (pedal-smoothness deterioration; only meaningful pattern in ERG since power is held by trainer) |

Interpretation bands: 0–30 fresh, 30–60 working as expected, 60–80 elevated
(fatigue-management feedback activates), 80+ high (modification suggestions
activate, motivation suppressed in favor of pacing guidance). The score is
*relative to intent*: expected-drift baselines differ per `SegmentClass`, so a
VO2 session doesn't false-positive.

### 4.5 Confidence score

0–1 data-quality score gating everything: fraction of expected samples present
in active windows, sensor connection state, HR plausibility checks (HR < restHR
or > maxHR + 5 → discard sample, lower confidence), time since last dropout.
Rules consult per-signal confidence: below 0.7 for a signal, feedback derived
from that signal is suppressed and (once per session) the coach says the sensor
looks unreliable. This prevents the classic failure mode of coaching off a
flaky HR strap.

---

## 5. Physiological Expectation Engine

Generates an `ExpectationEnvelope` per interval: expected HR trajectory band,
power adherence band, cadence band. Envelopes, not point estimates — feedback
triggers on leaving the band, which is far more robust than thresholding
noisy point values.

### 5.1 Heart-rate expectation (MVP: rule-based with session calibration)

**Anchors.** Need two HR anchors: LTHR (HR at FTP) and resting/baseline HR.
`UserProfile` already has `maxHr` and `restingHr`; LTHR is a new field.
Sources in priority order: (1) user-entered LTHR, (2) estimated from `maxHr`
(LTHR ≈ 0.89 × maxHR), (3) maxHR estimated as 208 − 0.7 × age (only if the
profile defaults were never edited). Each fallback widens the envelope.

**Steady-state HR for a power target.** Piecewise-linear map from %FTP to %LTHR
(the HR-power relationship is near-linear from Z2 through threshold; above FTP,
HR saturates toward maxHR):

- 55% FTP → ~0.75 LTHR; 75% → ~0.85; 88% → ~0.92; 100% → 1.00 LTHR;
  ≥ 115% → asymptote toward 0.97 maxHR.

**Session calibration (cheap, high value).** During warmup and the first work
interval, compare observed steady-state HR to predicted; compute a per-session
offset (bpm) and apply it to all later predictions. This absorbs day-to-day HR
variance (heat, hydration, caffeine, sleep) without any historical modeling.

**HR kinetics.** HR responds to a power step as first-order lag:
`HR(t) = HR_ss − (HR_ss − HR_0) · e^(−t/τ)` with τ ≈ 30–45 s below threshold
(configurable). Consequences the engine must respect:

- No HR-based judgments in the first ~45 s of an interval (HR still rising is
  *normal*, not "rising faster than expected").
- Expected *end-of-interval* HR includes an intent-dependent drift allowance:
  ENDURANCE +2–4 bpm/30 min, THRESHOLD +5–8 bpm/20 min, VO2 +8–15 bpm per rep
  with upward creep across reps expected.
- For intervals above threshold, HR never reaches steady state; expectation is
  a *slope corridor*, not a plateau.

**Fatigue coupling.** The fatigue score shifts the expected envelope up
(elevated fatigue → higher HR for same power is *expected*, so the engine warns
about the trend once rather than re-alarming every interval).

**Envelope width.** ±5 bpm base, widened by anchor-quality penalty and
narrowed as session calibration accumulates confidence.

### 5.2 Power execution expectations

ERG mode changes the semantics: the *trainer* holds power, so raw adherence
measures the trainer + athlete system, and deviations mean something specific:

- **Adherence** = 30 s avg power / target mid. In ERG, sustained adherence
  < 95% almost always means cadence collapse dragging power down (the
  "ERG spiral") — this is *the* critical real-time catch, and the feedback is a
  cadence instruction, not a power instruction.
- **Time-in-band** = % of interval samples within target ± band (band = ±5%
  work, ±10% recovery/endurance; wider for sprints where ERG is meaningless).
- **Variability** = power CV over the interval; in ERG expect CV < 4%; rising
  CV across intervals feeds fatigue (§4.4).
- **Resistance/free mode** = athlete holds power; adherence and time-in-band
  become the primary pacing feedback signals, smoothing window 10 s.
- **Ramps** = adherence computed against the instantaneous ramp target.

### 5.3 Cadence expectations

Self-selected cadence is a cheap RPE proxy. Baseline = athlete's mean cadence
in the first same-class interval (or segment `targetCadenceRpm` if prescribed).
Expectations:

- Within interval: stay within ±5 rpm of own baseline; sustained −8 rpm →
  fatigue/technique feedback.
- Across intervals: slope of per-interval mean cadence; −3 rpm per interval
  across ≥ 2 intervals → "cadence has dropped significantly during the last two
  intervals" class of feedback.
- ERG-specific: cadence below ~65 rpm at high force → warn before the ERG
  spiral (proactive, fires on 10 s cadence trend, not after power collapses).
- Prescribed-cadence segments (e.g. low-cadence torque work): `WorkoutSegment`
  already carries `targetCadence: IntRange?` on Step/Ramp — use it directly as
  the band (it's already a range, no ±5 padding needed).

### 5.4 Fatigue indicators (summary)

All defined above; consolidated list the analytics engine watches: HR drift
beyond intent-adjusted expectation, Pw:Hr decoupling > 5%/8% (steady intents),
recovery HR deficit (HRR60 < 15 bpm after hard work, or recovery-end HR
> baseline + 10 for two consecutive recoveries), cross-interval cadence
decline, rising power CV in ERG, and adherence collapse in non-ERG. Rising-RPE
proxy = weighted co-occurrence of cadence decline + HR drift + power
instability (any 2 of 3 sustained 60 s).

---

## 6. Derived Metrics Catalog

Computed in the Athlete State Model / Analytics Engine; all incremental:

| Metric | Definition | Window | Consumers |
|---|---|---|---|
| Rolling power 3 s / 30 s | mean | ring buffer | UI, adherence |
| NP, IF, TSS (running) | true 30 s-rolling 4th-power NP | session | progress, fatigue, summary |
| Adherence % | 30 s power / target mid | 30 s | pacing, ERG spiral |
| Time-in-band % | samples within target band | per interval | execution score |
| Power CV | σ/μ of power | per interval | stability, fatigue |
| HR smoothed / slope | 10 s mean; 60 s regression slope | rolling | drift, kinetics |
| HR drift % | (HR late-window − HR expected-at-time) / expected | per interval | fatigue, warnings |
| Pw:Hr decoupling | (P/HR)₁ˢᵗʰᵃˡᶠ vs 2ⁿᵈʰᵃˡᶠ | steady segments ≥ 10 min | endurance feedback |
| HRR60 | HR drop 60 s into recovery | per recovery | recovery quality |
| Recovery completeness | recovery-end HR − session baseline | per recovery | recovery feedback |
| Cadence slope (intra/inter) | 60 s regression; per-interval Δ | rolling / ledger | technique, fatigue |
| **Interval execution score** | 0–100: 0.5·time-in-band + 0.2·(1 − CV penalty) + 0.2·cadence-band + 0.1·HR-envelope | per interval | feedback, summary, stars |
| **Workout execution score** | TSS-weighted mean of interval scores × completion factor | session | summary, motivation |
| **Fatigue score** | §4.4 composite | session EWMA | thresholds everywhere |
| **Confidence score** | §4.5 per-signal quality | rolling | global gate |

Execution-score weights are per-intent (RECOVERY intent scores *restraint*;
SPRINT intent drops HR term entirely).

---

## 7. Baseline Generation — Approach Comparison & Recommendation

| Approach | Pros | Cons | Verdict |
|---|---|---|---|
| **Rule-based** (population physiology + session calibration) | Ships fast; explainable; testable; no data needed; works for first-ever session | Generic; envelope must be wide; misses individual quirks (low/high HR responders) | **MVP (Phase 1)** — with session calibration (§5.1) it's ~80% of the value |
| **Personalized physiological model** (fit per-athlete HR↔power curve, τ, drift rates, cadence norms from stored sessions) | Big accuracy gain; still explainable; small data needs (3–5 sessions); pure on-device math | Needs session history schema + fitting code; stale after fitness changes (mitigate: exponential recency weighting) | **Phase 2** — best value/complexity ratio |
| **Historical profiling** (per workout-class empirical envelopes: "your last three VO2 sessions looked like this") | Great for repeat workouts; enables "better than last time" feedback | Sparse coverage of workout space; cold-start | **Phase 2/3 supplement**, not the backbone |
| **Machine learning** (sequence models predicting HR/failure) | Highest ceiling; captures nonlinearities, day-state | Data-hungry (single-user app!); opaque; on-device training complexity; hard to test; wrong prediction = embarrassing feedback | **Phase 3+, and only for specific sub-problems** (HR forecasting, completion probability), never as the whole engine |
| **Hybrid** (rules as guardrails, learned components adjust parameters inside rule bounds) | Learned parts can't produce absurd coaching; degradation path is graceful | Two systems to maintain | **Target end-state** — rules own the decision logic forever; learning owns the *parameters* |

**Recommendation:** rule-based with session calibration for MVP; evolve the
*parameters* (HR map, τ, drift allowances, cadence norms, fatigue weights) from
athlete history in Phase 2; add predictive components in Phase 3 strictly
inside rule guardrails. The decision engine's structure never changes across
phases — only the expectation engine's parameter source does. This is the
single most important architectural commitment in this plan.

---

## 8. Coaching Feedback System

### 8.1 Taxonomy

Priority tiers (P0 highest). Every feedback item carries exactly one category.

| Category | Tier | Purpose | Examples |
|---|---|---|---|
| SAFETY | P0 | protect the athlete | HR ≥ configured ceiling (default 97% maxHR) sustained 30 s; HR implausibly high for effort with high confidence |
| DATA_QUALITY | P0 | protect trust | "HR signal looks unreliable — HR coaching paused" (once/session/sensor) |
| WORKOUT_MODIFICATION | P1 | actionable suggestions (v1 engine) | adjust intensity ±, extend recovery, skip remaining intervals — **always accept/reject, never automatic** |
| FATIGUE_MANAGEMENT | P1 | surface accumulating strain | "HR drift is becoming excessive; consider easing the next interval" |
| PACING / ADHERENCE | P2 | in-interval execution | under/over target (non-ERG), surging, "settle in — 3 min to go" |
| RECOVERY | P2 | between-interval quality | "recovery looks incomplete — soft-pedal and get HR down" |
| TECHNIQUE | P3 | cadence/smoothness | "cadence dropping — shift and spin up before the trainer bogs down" |
| MOTIVATION | P4 | encouragement, milestones | "power execution excellent — stay focused through the final minute", interval countdowns, set completion, halfway |
| INSIGHT | P4 | educational color | "HR settled 6 bpm below usual for this power — good freshness sign" (Phase 2+) |

### 8.2 Trigger logic per category

Each rule is declaratively specified as: *condition (with sustain duration) →
candidate(severity, category, message key, data)*. Conditions read only
`AthleteState + ExpectationEnvelope + IntervalContext + FeedbackLedger`.

**SAFETY** — trigger: HR above ceiling sustained 30 s with HR confidence
≥ 0.8. Priority absolute; no cooldown suppression (but re-fire minimum 60 s);
escalation: 2nd fire → modification suggestion to reduce intensity; 3rd →
suggest stopping. Never suppressed by anything.

**WORKOUT_MODIFICATION** — triggers: existing v1 rules verbatim
(`docs/coach-rules.md`), plus new fatigue-score gates (score > 80 enables
reduce/skip paths even when instantaneous adherence is OK). Cooldown: profile
`cooldownSeconds` (global to this category); pending suggestion blocks all new
ones; rejection suppresses same-direction suggestions for 2 intervals (existing
behavior, kept).

**FATIGUE_MANAGEMENT** — triggers: fatigue score crosses 60 (first
notification) or 80 (stronger, pairs with modification); decoupling crosses
5%/8% on steady intents; recovery-deficit pattern (2 consecutive incomplete
recoveries). Cooldown 240 s per sub-signal; escalation via severity bump when
same signal re-fires 50% worse; suppression: never during final work interval
(pointless — deliver in summary instead), never when intent is VO2/ANAEROBIC
and drift is within intent allowance.

**PACING** — triggers (non-ERG or free segments primarily): 30 s adherence
outside band sustained 20 s; over-target on RECOVERY intent ("this is supposed
to be easy — back off"). In ERG: only the low-cadence/ERG-spiral precursor
lives here. Cooldown 90 s; escalation: same deviation 3× in one interval →
severity up; suppression: first 30 s of any interval, last 15 s (too late to
act), during pending modification suggestion.

**RECOVERY** — trigger: evaluated once per recovery segment at its midpoint or
60 s in, whichever is later (HRR60 available). Never more than one per
recovery. Escalates to FATIGUE_MANAGEMENT input, not to louder messages.

**TECHNIQUE** — trigger: cadence out of band sustained 30 s; inter-interval
cadence decline ≥ 2 intervals. Cooldown 180 s; suppression: SPRINT segments
(cadence chaos expected), and after 2 fires per session for the same sub-signal
(athlete has heard it; stop nagging).

**MOTIVATION** — triggers are *scheduled slots*, not deviations: interval
start (context-setting: "4 min at 300 W, interval 3 of 5"), final 60 s / final
15 s of hard intervals, halfway through long steady blocks, set completion,
workout milestones — filled only when the slot arrives AND no higher-tier
candidate exists AND stability check passes (execution score ≥ 70). Positive
reinforcement fires on genuinely good execution (interval score ≥ 85):
variable-ratio (fire with p = 0.7) so praise doesn't become metronomic.
Cooldown: profile encouragement interval (default 300 s) between pure
encouragements; countdowns exempt but capped at 2 per interval.

### 8.3 Anti-spam discipline (global)

- **Global rate limit:** ≥ 45 s between any two feedback items (P0 exempt).
- **Session budget:** soft cap ≈ 1 item / 2.5 min of workout, tier-weighted so
  motivation exhausts its budget first. Coach profile scales this
  (`chatty ↔ minimal`).
- **Deduplication:** identical message key within 10 min → suppressed unless
  severity increased.
- **Interval-transition quiet zone:** nothing (except P0) in the 10 s
  surrounding a segment boundary; boundary slots (interval start context) own
  that window.
- **Escalate, don't repeat:** a suppressed-but-worsening condition re-enters as
  higher severity with a message that references persistence ("still climbing"),
  not the same sentence again.

### 8.4 Ranking / arbitration

Once per arbitration cycle (every 5 s, not every tick — feedback doesn't need
1 Hz):

```
score = tierBase(category)                      // SAFETY 1000, MOD 800, FATIGUE 600,
                                                //  PACING 400, RECOVERY 380, TECH 300, MOT 100
      + severity × 50                           // severity 0–3 from rule
      + actionability × 30                      // can the athlete do something *now*?
      + timeCriticality × 20                    // expires soon? (final-minute slot)
      − staleness penalty                       // similar message recently
      × intentAffinity(category, intent)        // e.g. PACING ×1.3 on RECOVERY-intent rides
      × confidence(signal)                      // §4.5
```

Winner (if any survives cooldown/suppression/budget) is emitted; losers are
either dropped (motivation) or retained in a short pending queue (fatigue,
recovery) for the next cycle if their condition persists. At most one visible
feedback at a time; a pending modification suggestion blocks new emissions
except P0.

### 8.5 Message content

Extend the existing `CoachProfile.messages` template system: per category,
per message key, list of templates with `{{placeholders}}` (percent, seconds,
watts, bpm, blockNumber, blocksRemaining), filtered by coach `tone`/`style`.
Random pick with no-repeat-last-2. All copy lives in the profile JSON files so
coach personalities differ in voice, thresholds, *and* category budgets (e.g.
`michele-ferrari.json` terse and metric-heavy, `chris-carmichael-cts.json`
warmer and chattier). Note: the Android app currently ships only
`assets/coach_profiles/default.json`; the five personality profiles still live
at the repo root (`profiles/*.json`) from the TS app and need porting into
assets as part of this feature. *(Update 2026-07-06: porting done, but the
roster is being replaced with 3–4 contrasting archetypes — see §18 for the
review findings and decisions that supersede the examples above.)*

---

## 9. Data Model Additions

New/extended types (Room-persisted where noted):

```kotlin
// Static, computed at load
data class WorkoutPlanModel(
    val workoutId: String, val intent: WorkoutIntent,
    val segments: List<ClassifiedSegment>,   // segment + SegmentClass + setId
    val sets: List<IntervalSet>,
    val plannedTss: Double, val plannedIf: Double,
    val plannedTimeInClass: Map<SegmentClass, Int>,
)

data class ExpectationEnvelope(
    val hrBand: ClosedRange<Double>?,        // at current elapsed-in-interval
    val hrSlopeBand: ClosedRange<Double>?,   // bpm/min corridor (supra-threshold)
    val powerBand: ClosedRange<Double>,
    val cadenceBand: ClosedRange<Double>?,
    val driftAllowancePct: Double,
    val anchorQuality: Double,               // 0–1, widens bands
)

data class AnalysisEvent(                    // rule output, pre-arbitration
    val ruleId: String, val category: FeedbackCategory,
    val severity: Int, val messageKey: String,
    val data: Map<String, Number>, val signalConfidence: Double,
    val expiresAtSec: Int,
)

data class FeedbackItem(                     // arbitration output; Room: feedback_events
    val id: String, val sessionId: String, val timestampSec: Int,
    val category: FeedbackCategory, val severity: Int,
    val message: String, val rationale: String?,
    val suggestion: CoachSuggestion?,        // only for WORKOUT_MODIFICATION
    val delivery: Set<DeliveryChannel>,      // CARD, TTS, HAPTIC
    val userResponse: CoachResponse?,
)

// Room: interval_records (per session) — the ledger (§4.3), persisted at
// session end; this is the Phase 2 personalization training data.
// Room: session_physio_summary — session calibration offset, fatigue curve
// samples (1/min), execution scores, decoupling; small and queryable.
```

`CoachProfile` gains: `categoryBudgets`, `tierCooldowns`, `hrCeilingPctMax`,
`fatigueWeights`, `verbosity`. Existing fields unchanged → existing profile
JSONs remain valid with defaults.

**Persistence rule:** raw 1 Hz telemetry already persists via the session
recorder/FIT path; the coach adds only *derived* rows (interval records,
feedback events, physio summary). Do not duplicate raw samples.

---

## 10. Real-Time Processing Design

- **Tick pipeline:** `WorkoutClock` tick (1 Hz) + latest `TelemetrySample`
  are combined into a single `conflate()`d flow processed on
  `Dispatchers.Default`. Per tick: update windows (O(1)) → refresh
  `IntervalContext` → expectation lookup (precomputed per segment, O(1)
  interpolation) → run rule conditions (a few dozen comparisons). Budget
  << 1 ms; no allocation-heavy work per tick.
- **Arbitration cycle:** every 5 s (aligned to tick), the decision engine
  drains candidates and emits ≤ 1 `FeedbackItem` on a `SharedFlow`.
- **Segment-boundary events:** clock exposes a boundary `Flow`; ledger append,
  recovery evaluation, and boundary slots hang off it rather than being
  detected by diffing (removes the `lastSegmentRef` fragility of the TS
  implementation).
- **Pause/seek:** clock events invalidate rolling windows and defuse any
  sustain-timers; expectation engine re-anchors HR kinetics on resume (HR after
  a pause is a fresh `HR_0`).
- **Determinism:** the whole 3→6 pipeline is a pure function of
  (sample stream, clock stream, plan model, profile) — this is the property the
  replay test harness (§12) exploits.

---

## 11. Feedback Delivery Layer

- **Card UI (default):** existing coach panel (migration Task 8.4) shows the
  current `FeedbackItem`; modification suggestions keep accept/reject buttons;
  informational items auto-dismiss after 12 s into the scrollable event log.
- **TTS (recommended for v1.1 of the feature):** Android `TextToSpeech`, since
  eyes-free is the natural mode mid-interval. Per-category user toggle; P0/P1
  spoken by default, motivation spoken only if enabled. Queue-flush on new P0.
- **Haptics/chime:** short distinct cues per tier so athletes learn "that sound
  = look at screen". Respect system DND.
- **Event log:** every emitted item → Room (`feedback_events`) and into the
  session summary ("coach highlights" already exist in v1 completion flow).
- **Summary integration:** post-ride screen gets execution score, fatigue
  curve, decoupling, and the feedback timeline — closes the loop and builds
  trust in the live feedback.

---

## 12. Android Considerations

- **Offline-first:** zero network dependency in Phases 1–3. All models,
  profiles, and history are on-device (Room + JSON assets). Phase 4 LLM
  features must degrade to Phase 3 behavior when offline.
- **Battery:** the coach adds ~1 Hz arithmetic on an already-running pipeline —
  negligible next to BLE + screen-on. Rules: no wakelocks of its own (rides the
  existing foreground service from migration Phase 11); no per-tick
  allocations in hot path (reuse buffers); TTS only on emission.
- **Background processing:** pipeline lives in the workout foreground service
  scope (`app/WorkoutForegroundService.kt`, already shipped), not the
  ViewModel, so coaching (and TTS) continues with screen off or app
  backgrounded. UI re-subscribes to flows on return.
- **Latency:** end-to-end sample→feedback ≤ 2 s is ample (coaching, not
  gaming). BLE notify (~1 s cadence) dominates; pipeline adds ms. Arbitration
  at 5 s is a product choice, not a performance limit.
- **Persistence:** derived rows only (§9); interval records + physio summary
  written at boundaries/session-end, feedback events as they occur (small,
  WAL-friendly). Crash mid-session loses at most the current interval's derived
  data — raw telemetry recovery already handled by the recorder.
- **Device floor:** Pixel 2 XL / API 30 (per migration plan) — everything above
  is comfortably within budget; avoid ML runtimes until Phase 3 and gate them
  on device capability.

---

## 13. Testing Strategy

1. **Unit tests (pure Kotlin, bulk of coverage):** interpreter classification
   and intent inference over the workout library + imported ERG/MRC/ZWO
   fixtures; window math against hand-computed values; expectation envelopes
   for canonical athletes; every trigger rule with synthetic condition streams
   (fire, sustain, cooldown, escalation, suppression each asserted); arbitration
   ordering and budget exhaustion.
2. **Replay harness (highest-value investment):** feed recorded rides through
   the full 3→6 pipeline as a deterministic offline run; assert on the emitted
   feedback timeline. Two sample sources, both already available: persisted
   sessions from Room (`SessionData.samplesJson` deserializes straight to
   `List<TelemetrySample>` — the primary path, zero new code) and the FIT
   fixtures in `docs/plans/*.fit` (needs a small FIT decoder; `FitEncoder`
   exists but only writes). Golden-file tests catch regressions; new rides become new
   fixtures. Also the primary *tuning* tool — thresholds are adjusted by
   replaying real sessions and eyeballing the timeline, not by riding 100
   trainer hours.
3. **Scenario simulator:** synthetic stream generator (athlete simulator with
   configurable HR kinetics, fatigue, cadence behavior, sensor dropouts) to
   cover cases real rides don't: HR strap dying mid-VO2 set, ERG spiral,
   blow-up mid-set, sandbagging, pause/seek storms.
4. **Property tests:** never two non-P0 items < 45 s apart; never HR feedback
   with HR confidence < 0.7; never intensity-up during RECOVERY intent; budget
   never exceeded.
5. **Device/field checklist:** extend migration Task 12.2 — full workout with
   real trainer + strap, screen-off TTS delivery, dropout-reconnect mid
   interval, suggestion accept/reject applying to ERG target.
6. **Feedback quality review loop (product, not code):** every emitted item is
   logged with its rule + metric snapshot; a post-session debug view lists
   "why did the coach say this" — essential for tuning and user trust.

---

## 14. Roadmap

### Phase 1 — MVP: Rule-Based Live Coach (~4–6 engineering weeks on top of migration Phases 0–8)

**Scope:** workout interpreter (classification, sets, intent); athlete state
model with incremental windows, ledger, fatigue + confidence scores;
expectation engine with population HR map + session calibration; full feedback
taxonomy with the ~15 highest-value rules (safety, ERG-spiral, pacing,
HR-drift, recovery-quality, cadence-trend, motivation slots, v1 modification
rules rehomed); decision engine with ranking/cooldowns/budget; card UI +
event log + summary integration; replay harness.
**Explicitly deferred:** TTS (fast-follow), decoupling/insight messages,
per-athlete learned parameters.
**Complexity:** medium — all deterministic Kotlin; the hard work is tuning, so
the replay harness is *in* the MVP, not after it.
**Risks:** threshold tuning wrong → annoying coach (mitigate: conservative
budgets, replay tuning, per-profile verbosity); HR anchor absent → weak HR
features (mitigate: honest degradation + onboarding prompt for LTHR).
**Value:** the headline product differentiator; 80% of perceived
intelligence comes from context-aware timing + intent-aware thresholds, which
are all here.

### Phase 2 — Personalization from Athlete History (~3–4 weeks)

**Scope:** persist interval records + physio summaries (already schema'd in
Phase 1); fit per-athlete parameters with recency weighting — HR↔power map
(replaces population map), τ, per-intent drift allowances, cadence norms,
recovery-HR norms; per-workout-class historical envelopes ("vs your last three
threshold sessions"); INSIGHT category activates; day-freshness estimate from
warmup HR vs personal norm; suggestion accept/reject history tunes modification
aggressiveness.
**Complexity:** medium — robust fitting with tiny n (3–5 sessions) and outlier
rejection is the tricky part; everything slots into existing parameter
interfaces.
**Risks:** overfitting sparse/dirty history (mitigate: parameter bounds =
rule guardrails, minimum-session gates, fall back to Phase 1 values);
fitness-change staleness (recency weighting, FTP-change invalidation).
**Value:** accuracy jump users can feel ("it knows my HR runs low"); narrower
envelopes → earlier, more confident feedback.

### Phase 3 — Predictive Models (~4–6 weeks)

**Scope:** W′bal (Skiba) for above-threshold work → "you have N matches left",
anaerobic-capacity-aware modification suggestions; short-horizon HR forecasting
(does drift trajectory hit ceiling before interval ends → preemptive advice);
interval/workout completion probability from ledger trajectory vs historical
patterns → earlier, better-targeted modification suggestions; optional small
on-device model (TFLite) for HR prediction *inside rule guardrails only*.
**Complexity:** high — W′ needs CP-model parameters (estimable from FTP +
history but noisy); forecasting quality varies per athlete; ML adds runtime +
model-management burden.
**Risks:** confident-but-wrong predictions damage trust more than no
prediction (mitigate: display as forecasts not facts, confidence gating,
guardrails); scope creep into research (timebox, ship W′bal first — it's
closed-form and well-validated).
**Value:** the coach becomes *anticipatory* — advice before failure instead of
diagnosis after; strongest for VO2/anaerobic intents where MVP is weakest.

### Phase 4 — AI-Powered Adaptive Coaching (~6–8 weeks, product-led)

**Scope:** LLM-generated feedback *phrasing* and session narratives — the
decision engine still decides *when/what category/what data*; the LLM renders
richer, non-repetitive, personality-consistent language and the post-ride
narrative ("today's session in context of your week"). Conversational
post-ride Q&A. Optionally: cross-session adaptive prescription ("based on
Tuesday, today's 3rd block at 95%").
**Architecture stance:** LLM as *renderer and narrator on top of the
deterministic engine* — never as the trigger logic. Cloud API with strict
offline fallback to Phase 1–3 template messages; latency budget means LLM copy
is prefetched at interval boundaries, not generated at fire-time.
**Complexity:** medium code / high product — prompt design, tone safety,
cost, privacy (telemetry leaves device → explicit opt-in).
**Risks:** hallucinated physiology (mitigate: LLM only rephrases
engine-supplied facts, template fallback on validation failure); recurring
cost; privacy expectations.
**Value:** delight and retention; the coach stops sounding canned; post-ride
narratives are highly shareable.

---

## 15. Key Risks (Cross-Phase)

1. **Annoyance is the existential risk.** A wrong-but-quiet coach is
   recoverable; a spammy coach gets muted permanently. Hence: budgets,
   variable-ratio praise, escalation-not-repetition, per-profile verbosity, and
   the replay-based tuning loop are core architecture, not polish.
2. **HR data quality.** Straps drop, spike, and lie. The confidence gate (§4.5)
   must land in MVP or HR-based features will misfire and burn trust.
3. **Single-user data scarcity.** This is a personal app; ML approaches assume
   data volume that doesn't exist. The rules-own-decisions/learning-owns-
   parameters split is the hedge — enforce it in code review.
4. **ERG semantics.** Half of classic pacing feedback is meaningless in ERG
   mode. Every power rule must be ERG-aware from day one (§5.2) or the coach
   will say absurd things in the app's primary mode.
5. **Tuning cost.** Thresholds have no ground truth. The replay harness +
   "why did the coach say this" debug view are the mitigation; without them
   tuning is guesswork on a trainer at 9 pm.
6. **Coupling to migration timeline.** This feature assumes migration Phases
   0–8 (models, clock, BLE, recorder, workout UI) are done. If migration slips,
   the interpreter + state model + decision engine can still be built and
   replay-tested as pure Kotlin against FIT fixtures — only layer 1 and 7
   block on the app shell.
7. **FTP staleness.** Every expectation keys off FTP/LTHR. Wrong FTP → wrong
   everything. Mitigation: session-calibration absorbs some error; the in-app
   ramp test (`domain/RampTest.kt`, already shipped) gives the coach a concrete
   call-to-action; Phase 2 adds drift detection ("your HR has been low for
   prescribed power across 3 sessions — retest FTP?").

---

## 16. Suggested Build Order (Phase 1 internal sequencing)

1. `WorkoutPlanModel` + interpreter (+ tests over workout library) — pure, no deps.
2. Athlete state model: windows, ledger, scores (+ synthetic-stream tests).
3. Expectation engine: HR map, kinetics, envelopes (+ canonical-athlete tests).
4. Analytics rules — the ~15 MVP rules as declarative condition specs.
5. Decision engine: arbitration, cooldowns, budget (+ property tests).
6. Replay harness over persisted Room sessions + `docs/plans/*.fit`; first tuning pass.
7. Delivery: coach panel integration, event log, summary; profile JSON extensions.
8. Field test protocol; second tuning pass; ship behind a settings toggle.

Each step is independently testable and committable, consistent with the
task-by-task style of the migration plan.

---

## 17. Implementation Status (2026-07-06)

### Shipped (Phase 1 core, `android/.../domain/coach/`)

- **`CoachModels.kt`** — SegmentClass, WorkoutIntent, WorkoutPlanModel,
  IntervalContext, ExpectationEnvelope, AnalysisEvent, FeedbackItem,
  IntervalRecord/RecoveryRecord, FeedbackCategory tier table.
- **`WorkoutInterpreter.kt`** — §3: classification, set detection (±10%
  tolerance), intent inference, effort-weighted progress, `contextAt()`.
- **`AthleteStateModel.kt`** — §4: O(1) ring-buffer windows (3/10/30/60 s),
  segment accumulators, interval ledger, recovery records + HRR60, fatigue
  EWMA composite, per-signal confidence with HR plausibility gating.
- **`ExpectationEngine.kt`** — §5: LTHR-anchored piecewise HR map (LTHR
  estimated 0.89×maxHR when absent), first-order HR kinetics (τ=40 s, 45 s
  blackout), warmup session calibration, intent-scaled drift allowances,
  ERG-aware power bands, prescribed/baseline cadence bands.
- **`AnalyticsEngine.kt`** — MVP rules: HR-ceiling safety (P0), HR-sensor
  data-quality (once/session), ERG-spiral precursor, pacing under/over
  (non-ERG, quiet zones respected), recovery-intent over-riding, fatigue band
  crossings (60/80), HR-above-envelope, recovery quality, cadence decline,
  motivation slots (interval start w/ set position, final-minute, halfway).
- **`FeedbackDecisionEngine.kt`** — §8.3–8.4: ranking, 45 s global gap
  (P0 exempt), per-category cooldowns, tier-weighted session budget,
  pending-modification blocking.
- **`LiveCoach.kt`** — facade: tick pipeline, 5 s arbitration cycle,
  seek/pause window invalidation, `replan()` on mid-ride modification.
- **Integration** — `UserProfile.lthr` (+prefs persistence); `LiveCoach`
  wired into `WorkoutViewModel` (alongside v1 `CoachEngine`, whose pending
  suggestion suppresses non-P0 live feedback); `LiveFeedbackCard` in
  `WorkoutScreen` with 12 s auto-dismiss; TTS for SAFETY/DATA_QUALITY/FATIGUE
  tiers via the screen's existing engine.
- **Tests** (20, all passing) — interpreter classification/sets/intent/
  context; decision-engine ranking/gap/cooldown/budget; synthetic end-to-end
  pipeline runs (ERG spiral, safety, quiet ride sparseness, ≥45 s-gap
  property, low-HR-confidence suppression).

### Follow-up items — completed 2026-07-06 (Mac dev machine)

1. **Device build** — `assembleDebug` + unit tests pass; installed and
   launched on the attached Pixel. §13.5 field-ride checklist still needs a
   human on the trainer.
2. **Persistence** — `CoachSessionData` (feedback log, interval ledger,
   recoveries, 1/min fatigue curve, final fatigue) serialized into the
   previously unused `coachEventsJson` session column; no schema migration.
3. **Replay harness** — `ReplayHarness` (test sources) replays any sample
   stream (incl. straight from a session's `samplesJson`) through the full
   pipeline; golden-file test over a simulated 3×10 threshold ride
   (`REGENERATE_GOLDEN=1` to re-baseline).
4. **Summary integration** — `CoachSummaryCard` on the complete screen:
   workout/interval execution scores (reduced-term formula; see
   `executionScore` in CoachModels), fatigue sparkline, feedback timeline.
5. **v1 rehoming** — CoachEngine keeps detection + accept/reject; its
   suggestion enters arbitration as WORKOUT_MODIFICATION and only surfaces on
   winning, so the global gap/budget/log now cover it. Expired-unemitted
   suggestions auto-reject.
6. **Profiles** — five personality profiles converted into
   `assets/coach_profiles/` (flat v1 schema), loaded via `CoachProfileLoader`,
   selected profile drives CoachEngine; settings coach row is a picker dialog.
   Message templating still v1-style (§8.5 category budgets/extensions remain
   Phase 2 work).
7. **LTHR** — editable field in Settings (blank = estimated from max HR).
8. **Coach toggle** — master switch in Settings gating live + v1 coach ticks.

### Still open

- §13.5 field-ride checklist and threshold tuning against real recorded rides.
  The FIT-decoder gap is closed (2026-07-06): `domain/fit/FitDecoder.kt`
  decodes activity files (both endiannesses, compressed timestamps, developer
  fields) and `ReplayHarness.replayFromFit` feeds them through the pipeline;
  the three `rides/*.fit` files (incl. a Wahoo-recorded one) decode in tests.
  Tuning replays need each ride paired with its workout's segment list.
- Phase 2+ items (personalization, W′bal, LLM rendering) per roadmap.

### Coach profile differentiation — implemented 2026-07-06 (§18 scope)

- Five real-name profiles replaced by four archetypes in
  `assets/coach_profiles/`: `drill-sergeant`, `mentor`, `silent-scientist`
  (merged Sassi+Sola, drift thresholds sanity-tuned to 5/8), `base-builder`.
  `default.json` retained as fallback (stale selected ids fall back to the
  in-code default).
- `CoachProfile` gains `feedback` (rule-id → template list for all live-coach
  copy), `verbosity`, `cooldownScale`, `motivationShare`; `voice` and
  `encouragement` deleted (were never read).
- `MessagePicker` (seeded, deterministic for replays): random pick with
  no-repeat-last-2 + `{{placeholder}}` fill (watts, bpm, duration,
  blockNumber, blockCount). `AnalyticsEngine` copy now profile-driven with
  built-in fallbacks; `CoachMessageBuilder` `firstOrNull` collapse fixed.
- `FeedbackDecisionEngine` budget/motivation-budget/category-cooldowns now
  scale by the profile knobs; `LiveCoach` takes the `CoachProfile` and wires
  it through (WorkoutViewModel passes the selected one).
- `CoachDiffTest`: same simulated ride through every bundled profile —
  asserts distinct message streams, silent-scientist emits fewer items than
  mentor and zero MOTIVATION.

---

## 18. Coach Profile Differentiation (review + decisions, 2026-07-06)

A review of the shipped profiles against the live pipeline found that
**switching coaches is barely perceivable**: all LiveCoach feedback
(safety/pacing/technique/fatigue/recovery/motivation) uses hardcoded strings
in `AnalyticsEngine` and `UserProfile` thresholds — the `CoachProfile` only
drives v1 modification suggestions (0–3 cards per hour after gating +
arbitration) and the completion message. Additional findings:

- `voice.tone`/`voice.style` are parsed but never read; `encouragement` is
  empty everywhere and never read.
- `CoachMessageBuilder` uses `firstOrNull()` — message *lists* (incl. the
  three completion variants per profile) collapse to always the first entry.
- Some threshold combos make coaches near-mute: e.g. Sassi's up-adjust needs
  HR drift ≤ 3% over a 120 s window (HR naturally rises more than that early
  in work intervals) on top of 420 s min-elapsed + 300 s cooldown.
- `aldo-sassi` and `javier-sola` are near-duplicates (same timing/step
  params, several verbatim-shared sentences — some also shared with
  `chris-carmichael-cts`).

**Decisions:**

1. **Archetypes, not real names.** Replace the five real-name profiles with
   3–4 strongly contrasting archetypes (real names are a liability if this
   ships wider — Michele Ferrari in particular). Working roster:
   - *The Drill Sergeant* (ex-Ferrari): terse, metric-heavy, aggressive ±8%
     steps, pushes up readily, near-zero motivation slots.
   - *The Mentor* (ex-Carmichael): warm, educational rationales,
     motivation-rich, conservative adjustments, protective of recovery.
   - *The Silent Scientist* (merge Sassi + Sola): speaks only on
     data-significant events, long cooldowns, tight zones, no cheerleading —
     "quiet" as a deliberate trait.
   - *The Base-Builder* (ex-Overton): sweet-spot bias, moderate chat, guards
     against over-riding easy days.
2. **Verbosity is a coach trait**, not a user setting (master coach toggle
   remains the only user control).
3. **Implementation scope** (this is the §8.5/§9 work, *not* roadmap
   Phase 2 personalization — they are independent):
   - Move `AnalyticsEngine` copy into profile JSON keyed by rule id (or
     category) with the `{{placeholder}}` system extended (watts, bpm,
     blockNumber, blocksRemaining); random pick, no-repeat-last-2 (also
     fixes the `firstOrNull` collapse).
   - Per-profile `verbosity`/`categoryBudgets`/`tierCooldowns` scaling the
     decision engine's session budget, motivation budget, and category
     cooldowns (currently global constants in `FeedbackDecisionEngine`).
   - Delete or wire up `voice` and `encouragement`; randomize completion
     messages; sanity-tune the near-impossible up-adjust conditions.
   - **Coach-diff replay test:** run the same recorded ride through every
     profile and assert the feedback streams differ meaningfully in count
     and content — differentiation as a tested property.
