# TrainerLoop reference-led color and UI revamp

**Date:** 2026-07-12  
**Status:** Implementation-ready proposal  
**Scope:** Compose theme, shared UI components, navigation chrome, all rider-facing screens, accessibility, and visual verification  
**Supersedes:** the visual direction in `2026-07-11-california-beaches-color-system-plan.md`; it preserves that plan's semantic-color and verification requirements  

## 1. Outcome

Recast TrainerLoop as a **warm, precise cycling instrument**: welcoming before a ride, quiet while browsing, and exceptionally legible during effort.

The supplied UI montage is a hierarchy and density reference, not a pixel-for-pixel iOS specification. The Android implementation should retain Material behavior, system insets, Android navigation conventions, and the app's existing local-first flows.

The four supplied colors become recognizable anchors:

- `#FFEB3B` — readiness and optimistic emphasis;
- `#F88379` — human/coaching and assessment accent;
- `#82C8E5` — brand, interaction, selection, and measured-data accent;
- `#E6D8C4` — warm structural surface and neutral accent.

The intended feeling is **friendly at rest, focused under load**. The player is not pastel decoration: it remains a high-contrast instrument panel whose data colors and control states have fixed meanings.

## 2. What the current code makes feasible

The redesign is feasible without changing repositories, BLE behavior, workout models, navigation routes, or ViewModel contracts.

Existing foundations to preserve and extend:

- `ui/theme/Color.kt`, `Theme.kt`, `Type.kt`, `Spacing.kt`, and `ZoneColors.kt` already centralize most theme decisions.
- `Motion.kt`, `Pressable.kt`, and `navigation/Transitions.kt` already provide reduced-motion-aware springs, touch-down feedback, and route transitions.
- `ThemeContrastTest.kt` and `ZoneColorsContrastTest.kt` provide a starting safety net.
- Screens already expose the product hierarchy seen in the reference: four bottom tabs, Home readiness, searchable workout cards, player metrics/chart, Devices sheet, History, Profile, routes, and builder.

Current constraints and cleanup needs:

- Home still imports foundation greens and hard-codes white/black overlays.
- Devices and the landscape player directly import `Green40`.
- `CoachSummaryCard` contains a hard-coded fatigue reference color.
- The theme has only Material roles; it lacks explicit roles for connected, warning, readiness, coaching, chart series, and hero content.
- Several screens are large composables, so a screen-by-screen recolor would create drift. Shared primitives must land first.
- The target Pixel 2 XL runs Android 11. Backdrop blur and API 31-only material effects cannot be required.
- The existing screenshots expose chart regressions from full-opacity zone fills; the seam-free band/elevation fixes in the visual-regression plan remain prerequisites.

## 3. Design decisions

| Before | After | Why |
| --- | --- | --- |
| Green owns brand, action, selection, success, and some chart meaning | Sky/ocean blue owns brand and interaction; success gets a dedicated green role | A single color no longer has five unrelated meanings |
| Exact palette swatches used as possible button backgrounds with white text | Pastel anchors use dark content; darker derived tones support white content where needed | White contrast fails on all four supplied swatches; black contrast ranges from 8.51:1 to 17.20:1 |
| Selected navigation is green everywhere | One blue selection treatment across all four tabs | Consistency makes selection immediately predictable; per-tab rainbow accents would weaken navigation semantics |
| Large green gradient Home hero | Yellow readiness card with a dark neutral CTA and semantic connection status | Closely captures the reference while keeping “ready” distinct from routine actions |
| White cards on a green-gray page | Foam cards on a restrained warm-sand canvas | Uses the beige anchor as atmosphere without making the app muddy |
| Generic green primary actions | Dark ocean-blue primary actions; soft sky-blue selected containers | Keeps primary actions accessible and lets the exact blue remain visible |
| Coral used as a possible general secondary action | Coral reserved for coaching, FTP assessment, celebration, and editorial emphasis | Prevents coral from competing with primary actions or being mistaken for error |
| Yellow can imply generic warning | Yellow is reserved for the positive readiness hero; warnings get a separate amber role | “Ready” and “degraded/reconnecting” must not look alike |
| Zone colors can be harmonized into the brand palette | Six power-zone colors remain a separate data system | Riders already depend on recovery → endurance → tempo → threshold → VO2 → anaerobic recognition |
| Live metrics animate through every 1 Hz update | Values update directly; only meaningful state/zone/target changes transition briefly | Constant number motion harms glanceability and is seen hundreds of times per ride |
| Decoration-heavy glass/shadows | Opaque tonal surfaces, restrained borders, and small elevation differences | Reliable on Android 11 and clearer on the Pixel 2 XL |

## 4. Color system

### 4.1 Foundation and semantic layers

Do not expose raw palette values to screens. Build two layers:

1. Foundation ramps in `Color.kt`: `Sun`, `Coral`, `Sky`, `Sand`, `Ocean`, and neutral tonal steps.
2. Semantic roles in a new `TrainerLoopColors.kt`, supplied through a `CompositionLocal`:
   - `ready`, `onReady`;
   - `coach`, `onCoach`;
   - `connected`, `onConnected`;
   - `warning`, `onWarning`;
   - `stale`, `onStale`;
   - `heroAction`, `onHeroAction`;
   - `chartPower`, `chartHeartRate`, `chartCadence`, `chartElevation`, `chartGrid`, and `chartCursor`.

Screens consume Material roles or these semantic roles. Only theme and chart files may consume foundation colors.

### 4.2 Candidate light scheme

These are implementation candidates, not approval by hex alone. The theme catalog and device review decide the final tonal steps.

| Role | Candidate | Content |
| --- | --- | --- |
| Page background | warm off-white derived from sand, around `#FAF7F1` | near-black neutral |
| Standard card | foam, around `#FFFCF8` | near-black neutral |
| Grouped/elevated surface | pale sand, around `#F2EADF` | near-black neutral |
| Structural sand accent | supplied `#E6D8C4` | near-black neutral |
| Primary action | derived ocean blue, around `#006782` | white/foam after contrast validation |
| Selected/info container | supplied `#82C8E5` or a slightly lighter tint | near-black neutral |
| Readiness hero | supplied `#FFEB3B`, optionally a very restrained tonal gradient | near-black neutral |
| Coach/assessment container | supplied `#F88379` or pale coral tint | near-black neutral |

### 4.3 Candidate dark scheme

Dark mode must be designed independently:

- background: deep blue-charcoal around `#081417`;
- standard card: `#101E22`;
- grouped/sheet surface: `#17282D`;
- raised/pressed surface: `#203238`;
- primary: bright sky near `#82C8E5` with dark content;
- ready and coral containers: deeper derived tones for large surfaces, with brighter anchors reserved for compact highlights;
- primary text: warm foam, not pure white;
- secondary text: opaque warm gray with at least 4.5:1 contrast.

Do not achieve dark mode by applying alpha to the light palette.

### 4.4 Functional colors outside the four-color palette

The supplied palette cannot safely cover every operational state. Keep dedicated roles for:

- connected/success: kelp green plus check/Bluetooth label;
- reconnecting/degraded: amber plus warning icon and explicit verb;
- error/destructive: dedicated red plus error icon or destructive verb;
- unavailable/disconnected: neutral gray plus slash/dash and text;
- power zones: the existing six-category sequence with independently tested light/dark fills, lines, and on-fill colors.

No state may depend on hue alone.

## 5. Layout and component language

### 5.1 Global rules

- Keep the 4 dp spacing foundation, but expand `Spacing` to named screen, section, card, and control values. Default screen margin: 16 dp; section gap: 24 dp; card padding: 16 dp.
- Use 14–18 dp card corners for routine surfaces. Reserve 24–28 dp corners for the Home hero and modal sheets so every row does not look equally important.
- Prefer tonal separation to outlines. Use a 1 dp outline only for fields, unselected chips, overlapping surfaces, and accessibility.
- Keep system typography. Preserve tabular figures for ride metrics and timers. Add a compact 12/14/16 scale for dense metadata and maintain the existing 64 sp player display style.
- Use a minimum 48 dp touch target; frequent in-ride controls should be 56 dp or larger.
- Press feedback remains immediate via `Modifier.pressable()`. Avoid additional bounce on routine actions.
- Keep transitions under roughly 250 ms unless they are gesture-driven sheets. Reduced motion uses short fades or snap.

### 5.2 Shared primitives to add or consolidate

Create small semantic primitives rather than a broad custom UI framework:

- `TrainerLoopTopBar` — title, back, and up to two actions;
- `TrainerLoopCard` — standard and emphasized tonal variants;
- `PrimaryActionButton` and `SecondaryActionButton`;
- `StatusPill` — connected, scanning, warning, unavailable, success;
- `MetricTile` — label, tabular value, unit, unavailable/stale semantics;
- `SectionHeader` — title and optional trailing action;
- `EmptyState` and `InlineMessage`;
- `WorkoutCard`/`RouteCard` stay domain-specific because their charts are core recognition cues.

Reuse or adapt `MetricCard`, `MetricBadge`, `PagerDots`, and the chart components before introducing duplicates.

## 6. Screen interpretation

### 6.1 App shell and navigation

- Keep four bottom destinations: Home, Workouts, History, Profile.
- Use a warm/foam navigation surface with one blue selected pill and consistent icon/text behavior.
- Remove the selected-icon bounce tick; retain a subtle critically damped scale or color transition only if it remains imperceptibly fast during repeated tab use.
- Keep tab switches as fade-through and pushed routes as mirrored shared-axis transitions.
- Use edge-to-edge system bars with icon appearance derived from the active surface.

### 6.2 Home — readiness launchpad

- Compact rider header: initial, name, FTP, weight, chevron; tap opens Profile.
- Make the yellow **Ready to ride?** card the dominant object only when trainer state supports riding. Its copy and icon change for disconnected/reconnecting states rather than showing a false-ready yellow state.
- Put one dark neutral **Start Free Ride** CTA inside the hero. If disconnected, keep the action available only if the product permits it and clearly explain the consequence.
- Keep Trainer and Heart Rate as two adjacent status tiles directly below the hero; the entire connection group opens Devices and includes a chevron/“Manage” affordance.
- Keep Workout Builder and GPX Routes as compact secondary cards.
- Recent workout uses a mini plan-profile chart and one concise metadata line. Empty history gets a designed first-ride message.
- When a planned workout exists, it takes priority between readiness and utilities and uses the sky informational container, not the readiness yellow.

### 6.3 Workout library and preview

- Compact top bar: title, Sync pill with inline progress, import icon button.
- Search and horizontally scrolling single-line filters remain.
- FTP Ramp Test becomes a coral-accented assessment row, not a generic primary button.
- Workout cards use foam surfaces, plan-profile chart first, then title/description/meta. Favorite and overflow stay trailing and have 48 dp hit targets.
- Preserve provenance/favorite/delete behavior. Do not add categories or remote concepts the data model does not support.
- Workout preview keeps the full profile and workload facts, with one blue Start action pinned after the content.

### 6.4 Structured workout player — flagship

- Optimize for the rider several feet away: interval name/time and current power versus target dominate.
- Top context: back, workout name, overflow; below it, current interval and `n / total · time left`.
- Power hero: 64–72 sp actual watts, compact unit, target immediately beneath. Unavailable telemetry displays an em dash and an accessible status, never a misleading zero.
- HR and cadence occupy two stable metric tiles. Their geometry does not change when data is missing or stale.
- Chart renders the plan as a monochrome stepped profile (`chartPlanOutline`/`chartPlanFill`); zone meaning survives in the tap tooltip and FTP gridlines only (superseded per `2026-07-12-workout-chart-plan-profile-design.md`). High-contrast cursor, elapsed/remaining/total labels, and Full/Focus framing unchanged.
- Bottom controls use the existing `PlayerControlsSheet`: large Start/Pause, distinct Skip/Lap, and a neutral Stop affordance that becomes red only in confirmation.
- Expanded controls contain ERG, bias, recovery extension, and finish. Preserve one-handed, sweaty-touch spacing.
- Do not animate every sample. Crossfade zone tint and target changes; announce stale/lost/reconnected state with text, icon, and restrained haptic feedback.
- Landscape is a separate acceptance layout: chart and primary metrics fill the canvas, with controls reachable at the edges and no portrait card stacking.

### 6.5 Devices

- Keep it as the existing modal/dialog route presented as a sheet-like task, not a fifth destination.
- Top section answers “what is connected?” with semantic status and one row per physical device.
- Available rows prioritize friendly name and capabilities. MAC address and RSSI move to smaller secondary text or disclosure.
- Scanning uses blue progress; connected uses green; reconnecting uses amber; failure uses red. Every state includes a verb.
- Stop/Scan remains in the top action position. Connect uses the primary blue button.

### 6.6 History and session detail

- Six weekly bars remain because the app computes that story; do not imply recovery/readiness analytics it does not have.
- Use blue for the current week and quiet sand/ocean neutrals for previous weeks.
- Session rows show name, date, duration, average power, and upload status with fixed trailing geometry.
- Detail metrics become neutral cards with data-colored accents rather than large brand-colored slabs.
- Completion uses green for saved/uploaded success and coral only for a restrained celebratory or coach accent.

### 6.7 Profile/settings

- Keep the current data and persistence behavior; reorganize visible hierarchy into Athlete, Heart Rate, Ride Preferences, Coaching, Simulation, Connections, and App.
- Use compact label/value rows for stable values and clear numeric fields for editable values. Keep units attached to their fields.
- Keep advanced physics collapsed. API credentials remain masked with reveal.
- Blue means focus/selection; green means saved/connected; coral may identify coaching but not toggles generally.
- Large font scales must reflow cards instead of clipping fixed-height fields.

### 6.8 Routes, builder, free ride, and completion

- Route list uses the elevation profile as recognition. Do not add photographic thumbnails: no image source exists and it would add storage/network scope.
- Route detail uses elevation as the hero, followed by distance/ascent facts and a blue Start Ride action.
- Builder retains fast numeric entry and the live preview. Interval cards use zone color only in preview/accent, not as full form backgrounds.
- Free ride shares the player metric/control language but prioritizes speed, power, distance, gradient, and virtual gear.
- Completion and ramp-result screens clearly separate Save/Accept, Later, Share/Upload, and destructive Discard actions.

## 7. Implementation sequence

### Phase 0 — baseline and design proof (1–2 days)

1. Resolve or land the outstanding chart visual-regression fixes before evaluating new colors.
2. Capture deterministic light/dark baselines for Home, Workouts, active Player portrait/landscape, Devices scanning/connected, History, and Profile.
3. Build a temporary theme catalog composable or previews showing all Material roles, custom semantic roles, buttons, chips, fields, metric states, status pills, and six zones.
4. Apply candidate tokens to four representative static previews: Home ready/disconnected, Library, active Player portrait, and active Player landscape.
5. Approve hierarchy and role names before migrating screens. Adjust hex values only through theme files.

**Exit:** the direction reads as TrainerLoop, not an iOS copy or a generic pastel app; all critical text/icon pairs pass contrast.

### Phase 1 — token foundation and tests (1–2 days)

Files:

- `ui/theme/Color.kt`
- `ui/theme/Theme.kt`
- new `ui/theme/TrainerLoopColors.kt`
- `ui/theme/ZoneColors.kt`
- `ui/theme/Type.kt`
- `ui/theme/Spacing.kt`
- `ThemeContrastTest.kt`
- `ZoneColorsContrastTest.kt`

Tasks:

1. Add complete light/dark tonal ramps derived from the four anchors plus operational colors.
2. Fill every Material role explicitly; do not allow baseline purple fallbacks.
3. Add semantic app roles and expose them through `MaterialTheme.trainerLoopColors`.
4. Expand contrast tests to every `on*` pair, every custom semantic pair, all chart surfaces, and disabled-but-readable content.
5. Assert that dark surface luminance rises monotonically through the elevation ladder.
6. Keep zone index behavior unchanged and validate all zone fills/lines against every actual chart surface.

**Exit:** theme/catalog/tests compile and pass in both modes before any screen consumes the new palette.

### Phase 2 — semantic component migration (2–3 days)

Files:

- `ui/components/MetricCard.kt`, `MetricBadge.kt`, `Pressable.kt`, and new small primitives as needed
- `ui/TrainerLoopApp.kt`
- `ui/home/HomeScreen.kt`
- `ui/devices/DevicesScreen.kt`
- `ui/workout/WorkoutScreen.kt`
- `ui/complete/CoachSummaryCard.kt`

Tasks:

1. Add shared top bar, status, card, action, section, empty/error, and metric variants.
2. Replace all direct green imports and hard-coded screen colors with semantic roles.
3. Migrate navigation chrome and system bars.
4. Ensure all pressable wrappers share the same interaction source as their Material click target; avoid duplicate gesture handling.
5. Keep motion tokenized and reduced-motion aware; remove decorative animation from frequently repeated actions.

**Exit:** no screen-level presentation color remains except documented visualization data colors.

### Phase 3 — browse/setup screens (3–5 days)

Roll out and review in small slices:

1. Home, including planned/no-plan and connected/disconnected/reconnecting states.
2. Workout Library and Workout Detail/preview.
3. Routes and Route Detail.
4. Workout Builder, including validation and long interval lists.
5. Devices in permission, scanning, connecting, connected, reconnecting, and failure states.
6. Profile/settings at the top and bottom of the scroll, with advanced sections expanded.
7. History, Session Detail, Workout Complete, and empty/error/upload states.

Each slice includes light/dark screenshots, 1.0×/1.3× font review, TalkBack labels, and touch-target checks.

**Exit:** setup and browsing screens form one coherent system with no mixed green/blue era.

### Phase 4 — player and ride surfaces (3–5 days)

1. Apply the hierarchy to structured workout pre-start, active, paused, stale, reconnecting, and stop-confirm states.
2. Apply the same metric/control primitives to free ride and GPX ride without forcing identical information order.
3. Complete landscape layouts explicitly.
4. Verify chart series, zones, grid, cursor, elevation, and target/actual distinction in light/dark and color-vision simulations.
5. Tune the controls sheet on the physical device: press, drag, interrupt, resume, skip, and stop with sweaty-hand-sized targets.
6. Keep telemetry updates visually stable; only state transitions that explain a change should animate.

**Exit:** primary power, target, interval time, connection state, and transport state are identifiable in under one second at handlebar distance.

### Phase 5 — regression coverage, device QA, and documentation (2–3 days)

1. Run `./gradlew testDebugUnitTest lint` with JDK 17.
2. Add Compose UI tests for navigation selection, semantic labels, disabled/enabled actions, and critical player states.
3. Adopt screenshot testing only after deterministic state fixtures exist. Minimum golden set: Home, Library, Player portrait, Player landscape, Devices reconnecting, and Profile in light/dark.
4. Test font scales 1.0, 1.3, and 2.0; animator scale 0; TalkBack; grayscale; protanopia/deuteranopia simulation.
5. Test on the Pixel 2 XL in bright and dim rooms at normal hand distance and handlebar distance.
6. Update `README.md` with theme architecture and validation commands. Add a final token/usage specification under `docs/app-redesign/`.

**Exit:** automated checks pass, device screenshots are approved, and the color semantics are documented for future work.

## 8. Delivery slices

Prefer reviewable vertical slices:

1. Design proof and token catalog.
2. Theme/custom semantic roles plus contrast tests.
3. Shared components, app shell, and hard-coded color cleanup.
4. Home and Library.
5. Devices, Routes, Builder, and Profile.
6. History, detail, and completion.
7. Structured/free-ride players and landscape.
8. Screenshot coverage, physical-device polish, and documentation.

Do not keep a long-lived mixed-palette production build. If slices cannot be merged close together, gate the new theme at the root so each shipped build is internally coherent.

## 9. Acceptance criteria

- The four supplied colors are visibly recognizable, but each has one stable responsibility.
- All normal text and interactive icon pairs meet at least 4.5:1 contrast; essential live metrics target 4.5:1 even when large.
- White text is never placed directly on the four supplied pastel anchors.
- Success, warning, error, unavailable, and zone meanings are not borrowed from brand colors and never rely on color alone.
- No undocumented hard-coded presentation color remains in screen code.
- Home answers “am I ready and what can I ride?” immediately.
- Player power, target, interval time, connection state, and transport state are readable in under one second.
- Missing and stale telemetry preserve layout and are not rendered as plausible zero values.
- All frequent ride controls meet the 56 dp target recommendation; other controls meet 48 dp minimum.
- Light/dark, portrait/landscape, font scaling, TalkBack, reduced motion, and physical-device checks pass.
- No domain, BLE, recording, local-first, or sync behavior changes as a side effect of the visual revamp.

## 10. Explicit non-goals

- Pixel-perfect iOS imitation.
- Beach imagery, decorative gradients across every screen, or photographic route thumbnails.
- Per-tab accent colors for navigation selection.
- Replacing power-zone semantics with the four-color brand palette.
- New training analytics, readiness scoring, social/account features, or cloud dependencies.
- API 31-only blur as a required visual layer.
- Theme preference state in the palette migration; System/Light/Dark can follow as a separate product decision.

## 11. Main risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Sky brand blue is confused with Z2 blue | Brand blue appears on controls/selection and measured-power lines; Z2 remains in prescribed zone geometry with labels and fixed position |
| Coral is confused with error or high-zone red | Coral is limited to coaching/assessment/editorial containers; error remains dedicated red with destructive/error language |
| Yellow reads as warning | Use it only in the explicitly worded readiness hero; use amber plus warning icon for degraded states |
| Sand makes light mode low-contrast or muddy | Use sand primarily for the canvas/elevation ladder, foam for content cards, and automated contrast tests for real pairs |
| Pastels make the player feel toy-like | Dark-first player validation, restrained brand area, neutral controls, and unchanged zone/data semantics |
| Screen-specific migration causes drift | Land semantic tokens and shared primitives first; reject direct foundation imports in screen review |
| Visual tests become brittle | Create deterministic UI state fixtures and stabilize system bars/font/device size before adopting goldens |
| Motion reduces glanceability | Keep live samples direct and stable; animate only causal state changes and respect animator scale 0 |
