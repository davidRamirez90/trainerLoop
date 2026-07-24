# Trainer Loop — UI/UX Redesign Plan (Apple Design Review)

**Date:** 2026-07-10
**Method:** Live review on Pixel 2 XL (USB, `adb`), all screens exercised and screenshotted in dark + light themes; code audit of `ui/` (theme, screens, charts, motion usage).
**Framework:** Apple's design principles (Designing Fluid Interfaces WWDC18, Principles of Great Design WWDC26), translated to Jetpack Compose / Material 3.

---

## 1. Executive summary

The app is functionally rich and information-dense, with a coherent green brand. Its two biggest gaps against the Apple bar:

1. **Zero motion design.** A grep across `ui/` finds no `animate*AsState`, no `spring()`, no `AnimatedVisibility/AnimatedContent`, no pressed-state feedback, no haptics. Every value change, screen change, tab change, and chip toggle is a hard cut. This is the single largest lever: the app *works* but never *responds*.
2. **Craft debt in the details.** Crashes on two of four bottom tabs (fixed during this review), URL-encoded workout names rendered raw (`Z2+Endurance+++1x8m+SS+primer`), a `0-0 W` target before start, `IF 0.00 / TSS 0` on free-ride workouts, an empty flat chart for Endurance Ride, chips that wrap to two lines, an icon that overlaps text. Per *Craft*: "jittery scroll, misaligned icons… read as carelessness" — these erode trust in a training tool that asks users to trust its numbers.

The plan below is phased so each phase ships independently: **P0 correctness → P1 design tokens → P2 motion foundation → P3 screen redesigns → P4 gestures & haptics → P5 polish pass.**

---

## 2. Findings from the live review

### 2.1 Bugs found while driving the app (violate *Craft* / *Safety-Predictability*)

| # | Finding | Where | Severity |
|---|---------|-------|----------|
| B1 | **Workouts tab crashed the app** — `WorkoutLibraryViewModel(Application, ProfileRepository = …)` has no `(Application)`-only constructor for the default factory. **Fixed in this review** with `@JvmOverloads`. | `WorkoutLibraryViewModel.kt:50` | Critical (fixed) |
| B2 | **Profile tab crashed the app** — same pattern in `SettingsViewModel`. **Fixed** the same way. | `SettingsViewModel.kt:34` | Critical (fixed) |
| B3 | Same latent pattern in `HomeViewModel` (works today only because nothing constructs it via the reflective path on the tested flow). Audit all `AndroidViewModel`s with default-arg constructors; prefer explicit factories or `viewModel { }` initializers over reflection. | `HomeViewModel.kt:47` | High |
| B4 | **Workout names render URL-encoded** — History and Session Detail show `Z2+Endurance+++1x8m+SS+primer`, `VO2max+\|+Castle+Crag…`. Names pass through nav-route `createRoute()` encoding and are never decoded (or were stored encoded at session save). | `Screen.kt` createRoute / session save path | High |
| B5 | **Player shows `TARGET 0-0 W` and `Power 0` highlighted before start.** The pre-start state should show the first interval's target and a clear "ready" affordance, not zeros that look broken. | `WorkoutScreen.kt:285` | Medium |
| B6 | **Endurance Ride card: `IF 0.00 · TSS 0` and an empty chart** (flat line in an otherwise blank card). Free-ride-only workouts need a designed preview (dashed "rider's choice" band) and should hide meaningless IF/TSS zeros. | `WorkoutMiniChart`/`WorkoutSummaryMath` | Medium |
| B7 | **Sync cloud icon overlaps the title** on long History rows (VO2max row). Icon must be a fixed sibling, not overlay; title gets `weight(1f)` + ellipsis. | `HistoryScreen.kt` row layout | Medium |
| B8 | **`VO2 Max` filter chip wraps to two lines** while siblings are one line. | `WorkoutLibraryScreen.kt` chip row | Low |
| B9 | **Devices lists the same Zwift Hub twice** ("Trainers" and "Controllers" sections) with two Connect buttons — ambiguous mapping (*Grouping & mapping*: a control should sit next to what it affects, once). | `DevicesScreen.kt` | Medium |

### 2.2 Heuristic violations (mapped to the skill's principles)

**Response & feedback (§1, §13)**
- No pressed-state feedback anywhere beyond the default ripple; primary actions (Start Workout, Start Free Ride) deserve press-scale + instant highlight.
- No haptics at all — interval changes, countdown, workout complete, ERG toggle are exactly the moments the skill says earn multimodal feedback (*Causality/Harmony/Utility*).
- Live metrics (Power/HR/Cadence) hard-jump at 1 Hz. Values should animate through intermediate values (`AnimatedContent` slide-through or count-up), and the power tile's zone color should crossfade, not snap.

**Interruptibility & behavior (§3, §4)**
- All state changes are cuts; nothing is spring-driven, so nothing is interruptible. Tab switches in the player (`Main/Power/Trainer`) swap content instantly; `WorkoutStatsPager` exists but tabs above the chart are plain clickables.
- Zoom "Full/Focus" is a chip toggle that hard-cuts the chart window. The window bounds should animate (two independent springs on winStart/winEnd — §3's "decompose 2D motion").

**Spatial consistency & wayfinding (§7, §16)**
- Devices is pushed as a full screen but dismissed with a top-right "Done" *and* system back — pick one pattern; as a modal task it should be a sheet (it's a parallel, short task).
- Route Detail: title wraps mid-bracket, content occupies the top 30% of an otherwise empty screen — no hierarchy, no map, no zone/terrain context. Screen doesn't answer "What's here?"
- Bottom-tab metaphors: `FitnessCenter` (dumbbell) and `DirectionsRun` (runner) for a **cycling** app (*Familiarity*: metaphors should match the domain — use bike/route/history/person glyphs).

**Simplicity & hierarchy (§16.6)**
- Player screen stacks 8+ zones of information with equal visual weight (duration/target row, in-zone bar, 4 metric tiles, optional 3 more tiles, tabs, chart, footer stats, coach card, recovery button, bias row, transport row). The primary reading during a ride is: *current power vs target, time left in interval*. Those two deserve 2–3× the visual weight; the rest collapses into progressive disclosure (see P3.3).
- Bias buttons `-5% / -1% / +1% / +5%` are four identical green pills — the *mapping* is weak and they're easy to fat-finger mid-effort. A single stepper with press-and-hold repeat + haptic detents is simpler and safer.
- Home duplicates the bottom nav ("Workout Library" row ≡ Workouts tab) — *Purpose*: every element must earn its place.

**Color & materials (§12, dataviz)**
- Interval blocks render at `alpha 0.55` over dark background → muddy brown/olive (screenshots: Sweet Spot preview reads as "dirt", not zones). Zone colors need a designed dark-mode ramp (saturated fills at full alpha with a subtle top edge, or outlined blocks with tinted fills), not one web palette dimmed.
- Dark surfaces are green-tinted near-blacks with almost no elevation separation (surfaceContainer ≈ background). Cards blur together; the eye has no layering. Use the M3 surface-container ladder deliberately (background `Neutral10`, cards `Neutral15`, elevated `Neutral20`) plus a hairline `outlineVariant` only where content overlaps.
- Light mode: `Sync`/`Import` pills are bright green with white text — fails contrast (white on #4ADE80 ≈ 1.9:1). All "green pill + white label" combos need `onPrimaryContainer`-style pairs.
- Status bar area is pure black over app background `Neutral10` — mismatched band at the top; go edge-to-edge and let content own the full canvas.

**Typography (§15)**
- Reasonable scale exists in `Type.kt`, but screens frequently bypass it with manual `fontWeight` overrides. Numbers (metrics, timers) should use `FontFeature "tnum"` tabular figures so timers don't jitter width at 1 Hz.

**Accessibility (§14)**
- No `prefers-reduced-motion` equivalent: respect `Settings.Global.ANIMATOR_DURATION_SCALE == 0` and swap slides/springs for crossfades.
- Font-scale audit needed: metric tiles use fixed dp heights in places; text at 1.3× must not clip (test `adb shell settings put system font_scale 1.3`).

---

## 3. Design direction

One sentence: **a focused, glanceable training instrument — dark-first, zone-color-literate, calm by default, kinetic exactly when the ride is.**

- **Dark-first instrument panel.** The player is used on a bike at arm's length: bigger numerals, tabular figures, high-contrast zone color as the primary signal. Emotion to reinforce (*Delight* §16.8): *confident effort*.
- **Zone color as a system.** One canonical 6-zone palette (recovery gray-blue → Z2 blue → SS green → threshold amber → VO2 orange → anaerobic red) with designed dark/light variants, used identically in mini-charts, player chart, power tile, interval list dots, and session detail. Today three different renderings exist.
- **Motion vocabulary (Compose mapping of §4):**
  - Default spring: `spring(dampingRatio = 1f, stiffness = 300f)` (≈ Apple response 0.35, no bounce) for everything UI-initiated.
  - Momentum spring: `spring(dampingRatio = 0.8f, stiffness = 300f)` only after a user flick/drag (pager settles, sheet dismiss).
  - Value animations: `animateFloatAsState/animateColorAsState` with the default spring; numeric text via `AnimatedContent` with directional slide (+ `SizeTransform` off).
  - Decays/projection: `rememberSplineBasedDecay()` for chart pan; `anchoredDraggable` for sheets (velocity-aware, interruptible out of the box — this is the platform's additive-animation equivalent).
  - Reduced motion: a single `LocalReducedMotion` composition local read from `ANIMATOR_DURATION_SCALE`; when on, all specs collapse to `snap()`/fast fades.
- **Note on blur materials (§12):** `Modifier.blur`/RenderEffect backdrop blur needs API 31+; the Pixel 2 XL tops out at Android 11. Use gradient scrims + tonal elevation as the material system, with blur as a progressive enhancement behind an API check.

---

## 4. Implementation plan

### Phase 0 — Correctness (prerequisite for any redesign) ~1 day
| Task | Files | Acceptance |
|---|---|---|
| 0.1 Keep the two `@JvmOverloads` crash fixes (done in this review); audit every `AndroidViewModel` for the same reflective-factory trap; add a lint or unit test that instantiates each VM the way production does | `ui/**/**ViewModel.kt` | All 4 tabs + all pushed screens open without crash; test covers it |
| 0.2 Decode workout names once at the source; never render `+`/`%2F` | `Screen.kt`, session save path | History/detail show `Z2 Endurance — 1x8m SS primer` style names |
| 0.3 Pre-start player state: show first interval target, `Start` emphasized, no `0-0 W` | `WorkoutViewModel.kt`, `WorkoutScreen.kt` | Fresh player shows real target + ready state |
| 0.4 Free-ride workouts: suppress `IF 0.00/TSS 0`, design "rider's choice" mini-chart band | `WorkoutMiniChart.kt`, library card | Endurance Ride card looks intentional |
| 0.5 History row layout: icon as sibling, title ellipsis; date never wraps to 2 lines | `HistoryScreen.kt` | Long names truncate cleanly |
| 0.6 Chip row: single-line labels (`VO2` or horizontal scroll with no wrap) | `WorkoutLibraryScreen.kt` | No two-line chips |
| 0.7 Devices: one row per physical device with capability badges (Trainer · Controller), one Connect | `DevicesScreen.kt`, `DevicesViewModel.kt` | Zwift Hub appears once |

### Phase 1 — Design tokens & theme (foundation) ~2 days
| Task | Detail |
|---|---|
| 1.1 **Zone palette** | New `ZoneColors.kt`: 6 zones × (fill, on-fill, line) × (dark, light). Full-alpha fills tuned for dark surfaces; replace `zoneColor()`'s single web palette + 0.55 alpha. Validate contrast (≥3:1 fill vs background, ≥4.5:1 labels). |
| 1.2 **Surface ladder** | Rework dark scheme: background `#0F1410`, card `Neutral15`, elevated `Neutral20`; hairline dividers only where layers overlap. Light scheme: fix all white-on-Green60 pairs; primary actions use `Green40`+white or `Green95`+`Green10`. |
| 1.3 **Edge-to-edge** | `enableEdgeToEdge()`, transparent status/nav bars, content behind system bars with proper insets. Kills the black status-bar band. |
| 1.4 **Typography** | Add `Numeric` styles with `FontFeatureSettings = "tnum"` for all timers/metrics; display sizes for the player (56–72 sp power numeral). Keep negative tracking on display only, per §15. |
| 1.5 **Iconography** | Bottom nav: `DirectionsBike`, `Route`/`ListAlt`, `History`, `Person`. Replace dumbbell/runner everywhere (Home rows, hero button). |
| 1.6 **Spacing grid** | 4-dp grid constants (`Spacing.kt`); normalize card padding (16), section gaps (24), screen margins (16). |

### Phase 2 — Motion foundation ~3 days
| Task | Detail |
|---|---|
| 2.1 **`Motion.kt` spec** | `MotionSpec.default = spring(1f, 300f)`, `.momentum = spring(0.8f, 300f)`, `.fast = spring(1f, 700f)`; `LocalReducedMotion` (reads animator scale) collapses to `snap()`/`tween(150)` fades. All later phases consume only these tokens — no ad-hoc specs. |
| 2.2 **Pressable modifier** | `Modifier.pressable()`: scale 0.97 + tonal shift on press-down via `Interaction` + `animateFloatAsState(MotionSpec.fast)` — instant on down, springs back on up (§1, §10). Apply to hero CTA, Start/Stop, cards. |
| 2.3 **Nav transitions** | NavHost enter/exit: tabs = fade-through (no directional lie); pushed screens = shared-axis X with mirrored exit (§7 symmetric paths). Player entry: hero-expand from the Start button is stretch; ship slide-up-from-CTA first. |
| 2.4 **Animated values** | Power/HR/Cadence: `AnimatedContent` slide-through-up on increase, down on decrease; zone color crossfade `animateColorAsState`. In-zone progress bar animates with `.default` spring. |
| 2.5 **Chart motion** | Animate window bounds (winStart/winEnd) as two independent `animateFloatAsState` (§3); cursor eases; on new sample, the power line's last segment extends smoothly rather than popping. |
| 2.6 **Bottom-tab micro-motion** | Selection pill grows from the icon (scale+fade, `.default`); icon does a 1.06 scale tick. |

### Phase 3 — Screen redesigns ~1.5 weeks
**3.1 Home.** Merge profile chips into a compact header (name + FTP/weight as plain text, tap → Profile). Hero keeps `Ready to ride?` but the trainer/HR status becomes a single **connection strip inside the hero** (paired state, battery, one tap to Devices) — status lives next to the action it gates (*Grouping & mapping*). Drop the duplicate "Workout Library" row; keep Builder and Routes as secondary tiles. Recent workout card gets zone-colored mini-chart thumbnail + relative date ("Tuesday · 52 min · 122 W").

**3.2 Workouts.** Cards: mini-chart with new zone palette, single-line meta row `30m · IF 0.76 · TSS 28`, star toggles with a bounce (`spring(0.7f)` — momentum-class, it's a tap-flourish, keep subtle), overflow menu unchanged. Ramp-test banner becomes a distinct "assessment" card with icon, not a green slab that reads as tappable-but-what. Sync/Import: `Import` demoted to icon-button; `Sync` shows progress state inline (spinner-in-pill), success/error via snackbar (feedback kinds: status/completion/error §16).

**3.3 Player (the flagship — most effort here).**
- **Hierarchy:** top = interval context ("Sweet Spot · 2/4 · 4:12 left"), middle = one dominant power numeral (72 sp, tabular, zone-tinted) with target band beneath it ("target 144–151 W") and a thin circular-or-linear interval-progress ring around/under it; HR + cadence as secondary tiles.
- **Tabs → cards:** replace `Main/Power/Trainer` green tabs (all three currently look selected) with a swipeable pager + page dots; pages: Chart, Ride stats, Trainer.
- **Controls sheet:** bias/skip/recovery live in a **bottom `anchoredDraggable` sheet** — peek shows transport (Start/Pause · Skip · Stop), drag up reveals bias stepper and options. Velocity-aware settle (`momentum` spring), drag is 1:1, interruptible, rubber-bands past its anchors (§3/5/6/9 in one component).
- **Bias:** single stepper `−  +0%  +` with hold-to-repeat, haptic detent per step, ±5 on long-press.
- **Stop flow:** Stop always confirms while recording (it already does) — keep, but present as sheet action, destructive-red only in the confirm step (*Agency/forgiveness*).
- **ERG toggle:** real switch with state label, not a chip that looks like a button.
- **Haptics (P4 hooks):** interval change = double tick; 5-4-3-2-1 countdown ticks; workout complete = success pattern.

**3.4 History.** Replace the 42-bar TWTFSSM strip (unreadable) with a **weekly TSS/time bar chart (6 bars, one per week)** + streak line, labels "W27…W32". Rows: name (decoded), day + duration + avg power, small zone-distribution sparkline; sync state as a trailing glyph with contentDescription.

**3.5 Session Detail.** Green stat tiles → neutral cards with zone-colored accents (three green slabs currently over-shout). Chart tabs get the pager treatment; add zone-time distribution bar (data exists via samples+FTP).

**3.6 Profile.** Group into cards it already has, but: unit suffixes inside fields, numeric keyboards, save-on-navigate-away with inline "Saved ✓" status feedback, API key field masked with reveal. Advanced (CdA/Crr/bike weight) behind its existing "Advanced" disclosure — animate the reveal (`AnimatedVisibility` + `.default`).

**3.7 Devices.** Present as **modal bottom sheet** flow from the Home connection strip (parallel task, not a destination): permission checklist collapses to a single inline banner when all granted; scanning state = subtle pulsing radar row, not spinner+text+two buttons; connected device pins to top with battery/signal and Disconnect.

**3.8 Routes & Route Detail.** Detail screen: elevation profile as the hero (fills width, gradient fill by grade), stats row (distance/ascent/est. time at FTP), Start Ride pinned bottom. Title single-line ellipsis. Import GPX keeps its primary slot on the list screen but drops to secondary style once ≥1 route exists.

**3.9 Builder.** Add live `WorkoutMiniChart` preview that updates per keystroke (the payoff of *prototype-while-building*); interval rows draggable to reorder (`anchoredDraggable` list pattern with drop-slot springs); repeat blocks; Save enabled-state explains itself ("Add a name to save").

### Phase 4 — Gestures, haptics, sound ~4 days
| Task | Detail |
|---|---|
| 4.1 Chart scrubbing | Replace tap-to-select with press-drag scrub (pointer capture, 1:1, tooltip follows finger with grab-offset §2); pinch or double-tap toggles Full/Focus with animated window; pan decays with `splineBasedDecay`, rubber-bands at ends (§9). |
| 4.2 Haptic map | `Haptics.kt` central map: interval change, countdown, complete, ERG toggle, bias detent, PR/FTP-update moment. All fire on the causal frame (§13 harmony). Respect system haptic settings. |
| 4.3 Sheet physics polish | Tune player sheet + Devices sheet: velocity-projected settle (§6), drag-to-reopen mid-dismiss (interruptibility test: grab it mid-flight). |
| 4.4 Optional sound | Completion chime only, behind a setting, synced to the haptic (§13 utility — one meaningful moment, not per-interval). |

### Phase 5 — Polish & verification pass ~3 days
- Frame-by-frame review of every transition at 0.2× animator scale (§17 "slow motion review").
- Font scale 1.3× and 2.0× walkthrough; TalkBack pass (chart needs `contentDescription` summaries; metric tiles need semantic labels).
- Reduced-motion walkthrough with animator scale 0.
- Light-theme contrast audit (automated: compose screenshot tests + contrast assertions on the token pairs).
- Landscape player parity (it exists — apply the same hierarchy).
- Kill remaining hard cuts: any state change visible on screen without an animation token is a bug in this phase.

---

## 5. Sequencing & risk

- P0 and P1 are independent of design approval — start immediately (P0 is arguably overdue; two tabs crashed).
- P2 before P3: screens should be rebuilt *on* the motion/token foundation, not retrofitted.
- P3.3 (player) is the highest-value, highest-risk item; prototype the sheet + numeral hierarchy on-device first and ride with it (§17: test in real context — on the trainer, sweating, at arm's length).
- The Pixel 2 XL (Android 11) is the floor device: no RenderEffect blur, modest GPU — the spring-based system is cheap (transform/alpha only), but chart redraws should stay in `Canvas` with remembered paths (already mostly true).

## 6. Fixes already applied during this review

- `WorkoutLibraryViewModel` and `SettingsViewModel`: added `@JvmOverloads constructor` so the reflective `AndroidViewModelFactory` finds the `(Application)` constructor — Workouts and Profile tabs no longer crash. Both are in the working tree, unstaged; `./gradlew installDebug` was run against the device for verification.
