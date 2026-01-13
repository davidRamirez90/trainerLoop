# Coach Rules (v1)

Rules generate "coach events" based on the current workout segment, telemetry
windows, and selected coach profile. Suggestions are non-destructive and always
require accept/reject confirmation.

## Inputs

- Current segment context (type, duration, isOnInterval).
- Rolling telemetry window (power, cadence, HR).
- Coach profile thresholds and intervention limits.
- Session state (elapsed time, recent suggestions, accept/reject history).

## Global Guardrails

- Do not suggest changes during the first `minElapsedSecondsForSuggestions`.
- Enforce `cooldownSeconds` between suggestions.
- Do not suggest intensity changes during recovery segments.
- Never adjust beyond `intensityAdjustPct.min` / `intensityAdjustPct.max`.

## Rule: Increase Intensity on "On" Intervals

### Trigger Conditions

- Segment `isOnInterval` is true.
- Elapsed time within segment >= 45 sec.
- Adherence >= profile `targetAdherencePct.warn` (e.g., >= 90%).
- Cadence variance <= profile `cadenceVarianceRpm.warn`.
- HR drift <= profile `hrDriftPct.warn`.
- No failed suggestions in last 2 intervals.

### Suggestion

- Action: `adjust_intensity`
- Payload: `{ percent: +step }` where `step` is
  `intensityAdjustPct.step` (e.g., +5%).
- Scope: apply to remaining "on" intervals in the current set, or until the
  user changes it again.
- Rationale example: "Power and cadence are steady; HR drift is low."

### Rejection Handling

- Log rejection with metrics snapshot.
- Suppress further intensity increase suggestions for the next 2 intervals.

## Rule: Reduce Intensity on "On" Intervals

### Trigger Conditions

- Segment `isOnInterval` is true.
- Adherence <= profile `targetAdherencePct.intervene` (e.g., <= 80%) for
  >= 30 sec.
- HR drift >= profile `hrDriftPct.intervene` OR cadence variance >=
  profile `cadenceVarianceRpm.intervene`.

### Suggestion

- Action: `adjust_intensity`
- Payload: `{ percent: -step }` where `step` is
  `intensityAdjustPct.step` (e.g., -5%).
- Scope: apply to remaining "on" intervals in the current set.
- Rationale example: "Power is below target and HR drift is elevated."

## Rule: Extend Recovery

### Trigger Conditions

- Transition from an "on" interval to recovery.
- Last "on" interval adherence <= `targetAdherencePct.intervene`.
- HR drift >= `hrDriftPct.warn`.

### Suggestion

- Action: `extend_recovery`
- Payload: `{ seconds: recoveryExtendSec.step }`
- Scope: extend current recovery segment, capped by `recoveryExtendSec.max`.
- Rationale example: "Recovery HR is still elevated after the last effort."

## Rule: Skip Remaining "On" Intervals

### Trigger Conditions

- Profile allows skip: `allowSkipRemainingOnIntervals` is true.
- At least 2 "on" intervals completed.
- Two consecutive "on" intervals meet any of:
  - Adherence <= `targetAdherencePct.intervene`
  - HR drift >= `hrDriftPct.intervene`
  - Cadence variance >= `cadenceVarianceRpm.intervene`
- User has rejected the last two "reduce intensity" suggestions.

### Suggestion

- Action: `skip_remaining_on_intervals`
- Payload: `{ reason: "fatigue_indicator" }`
- Scope: mark remaining "on" intervals as skipped and proceed to cooldown.
- Rationale example: "Repeated under-target power and elevated HR drift."

## Encouragement and Completion

- Encouragement events fire every 5-8 minutes when metrics are stable and no
  active suggestion is pending.
- Completion event summarizes compliance, notes coach highlights, and awards
  stars based on adherence and HR drift.
