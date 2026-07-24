# TrainerLoop — California Beaches Color System Plan

**Date:** 2026-07-11
**Status:** Proposed; no production colors changed
**Scope:** Android app theme, dark mode, semantic states, charts, and color verification
**Source analysis:** `android/docs/app-redesign/APP_SCREEN_BRIEF.md` and the current Compose implementation

## 1. Outcome

Reframe TrainerLoop as a calm, focused coastal training instrument using a **California Beaches** foundation:

- **Pacific water** supplies the brand and primary action color.
- **Sea glass** supplies soft selection and informational surfaces.
- **Warm sand** supplies light-mode backgrounds and cards.
- **Deep ocean** supplies dark-mode backgrounds and elevated surfaces.
- **Sunset coral** supplies a limited secondary accent.
- **Kelp** supplies success and connected states.

This is a tonal and semantic redesign, not a decorative beach theme. It should not introduce beach imagery, novelty gradients, or warm accents on every component. During a ride, the result must still read as a high-contrast instrument panel.

The proposed emotion is **calm readiness before a ride, confidence during effort, and warmth after completion**.

## 2. Constraints from the product analysis

1. The workout player is safety- and glanceability-critical. Power, target, interval time, connection loss, and transport state must remain readable at distance.
2. The six power-zone colors are data, not brand decoration. Their ordering and meaning must survive the rebrand.
3. Red already means both high power and destructive/error. Context, labels, and shape must continue to disambiguate those meanings.
4. Disconnected, stale, reconnecting, and unavailable telemetry are ordinary operating states and cannot rely on color alone.
5. Light and dark modes need independently designed ramps. Dark mode must not be the light palette with lower brightness or opacity.
6. The Pixel 2 / Android 11 remains the floor. The design cannot depend on dynamic color, HDR, or API 31 blur effects.
7. Existing Material 3 roles and contrast tests provide a good migration seam; preserve that architecture.

## 3. Current-to-proposed design review

| Before | After | Why |
| --- | --- | --- |
| Performance green owns brand, action, selection, success, cadence, and some chart emphasis | Pacific blue owns brand/action; kelp green owns success; sea-glass blue owns information/selection | Separates brand from system meaning and reduces the amount of undifferentiated green |
| Warm-free green-charcoal neutral ramp | Sand-tinted light ramp and deep-ocean dark ramp | Establishes the California coast character primarily through surfaces, where it can remain calm |
| White/green gradient Home hero with hard-coded white/black overlays | Theme-owned Pacific gradient and semantic `onHero`/overlay tokens | Makes the hero correct in both modes and removes the largest theme exception |
| Secondary blue and primary green compete in charts | Pacific blue remains the actual-power line; zone colors remain interval/data colors | Creates a stable distinction between measured data and prescribed zones |
| Amber is both coaching/suggestion and warning-adjacent | Sunset coral is editorial/coaching accent; gold remains warning | Avoids using warning color for non-warning content |
| Dark surfaces are green-tinted near-black | Dark surfaces use a blue-ocean ladder with warm-neutral text | Better coastal identity and clearer elevation without relying on borders |
| Theme follows system only | Keep system default; optionally expose System / Light / Dark in Profile later | Preserves platform behavior while allowing a handlebars-friendly dark preference |

## 4. Palette architecture

### 4.1 Foundation families

The hex values below are **starting candidates**, not final approved tokens. They must pass the contrast and on-device gates in section 10 before adoption.

| Family | Light anchor | Dark anchor | Intended use |
| --- | --- | --- | --- |
| Pacific | `#006782` | `#78D1F0` | Primary actions, active controls, links, actual-power line |
| Sea glass | `#D0EDF3` | `#164E5C` | Selected nav/chips, informational containers, focus support |
| Sand | `#FAF8F2` | — | Light background and low-elevation canvas |
| Foam | `#FFFDF8` | — | Light cards and highest light surface |
| Deep ocean | — | `#081417` | Dark background and player canvas |
| Ocean slate | — | `#101E22` / `#17282D` / `#203238` | Dark surface elevation ladder |
| Sunset coral | `#9C4234` | `#FFB4A6` | Coaching, highlights, celebratory/editorial accent |
| Kelp | `#2D6A4F` | `#78D6A3` | Connected, completed, successful upload/sync |
| Warning gold | `#7A5900` | `#F3C75B` | Reconnecting, degraded, caution |
| Error red | `#B3261E` | `#FFB4AB` | Failure, destructive actions, safety alerts |

### 4.2 Naming strategy

Replace implementation-oriented names such as `Green40` with two layers:

1. **Foundation tokens** in `Color.kt`: `Pacific40`, `Pacific80`, `Sand95`, `Ocean10`, `Coral40`, `Kelp40`, and so on.
2. **Semantic roles** exposed through Material 3 or a small `TrainerLoopColors` composition local: `connected`, `onConnected`, `warning`, `onWarning`, `stale`, `chartPower`, `chartHeartRate`, `chartCadence`, and `heroScrim`.

Screens should consume semantic roles. Only `Theme.kt`, `ZoneColors.kt`, and visualization primitives should know foundation values.

## 5. Material 3 role mapping

### 5.1 Light scheme

| Material role | Proposed family | Usage |
| --- | --- | --- |
| `primary` / `onPrimary` | Pacific 40 / foam | Primary CTA, active switch, progress and links |
| `primaryContainer` / `onPrimaryContainer` | sea-glass pale / deep Pacific | Selected navigation, emphasized neutral cards, focus state |
| `secondary` / `onSecondary` | coral 40 / foam | Restrained secondary emphasis, not routine buttons |
| `secondaryContainer` | pale coral/sunset | Coach motivation or editorial callout |
| `tertiary` / `tertiaryContainer` | kelp / pale kelp | Completion and positive outcomes if a standard M3 role is sufficient |
| `background` | sand | Page canvas |
| `surfaceContainerLowest` | foam | Raised white/foam card |
| `surfaceContainerLow` / `surfaceContainer` | light sand variants | Standard cards and sheets |
| `surfaceContainerHigh` / `Highest` | deeper sand-gray | grouped controls, disabled tracks, dividers |
| `outline` / `outlineVariant` | coastal gray | fields and subtle boundaries |
| `error` roles | dedicated error red | failures and destructive confirmation only |

### 5.2 Dark scheme

Dark mode should feel like the same coast after sunset, not a black theme with neon accents.

| Material role | Candidate | Purpose |
| --- | --- | --- |
| `background` | `#081417` | Deepest app/player canvas |
| `surface` / `surfaceContainerLowest` | `#081417` | Flush surfaces |
| `surfaceContainerLow` | `#101E22` | Standard cards |
| `surfaceContainer` | `#17282D` | Sheets and grouped controls |
| `surfaceContainerHigh` | `#203238` | Hover/pressed/raised state |
| `surfaceContainerHighest` | `#294047` | Highest non-modal surface |
| `onBackground` / `onSurface` | warm foam near-white | Primary text without stark blue-white glare |
| `onSurfaceVariant` | desaturated sand-gray | Secondary text with at least 4.5:1 contrast |
| `primary` | `#78D1F0` | Active actions and actual-power line |
| `primaryContainer` | `#164E5C` | Selected and emphasized containers |
| `secondary` | `#FFB4A6` | Coral accent, used sparingly |
| `tertiary` | `#78D6A3` | Positive state |

Do not use pure black for the normal player background. Reserve black for temporary scrims and OLED-sensitive testing only. Keep text and icons opaque; use tonal roles before alpha to establish hierarchy.

## 6. Functional colors that must remain independent

### 6.1 Power zones

Retain the current six-step semantic sequence:

1. Recovery: slate
2. Endurance: blue
3. Tempo: green
4. Threshold: amber
5. VO2: orange
6. Anaerobic: red

The beach palette may harmonize saturation and temperature slightly, but it must not turn all zones into Pacific/sea-glass shades. Keep the existing `fill`, `line`, and `onFill` model and separate light/dark arrays.

Required refinements:

- Recalculate every zone against the new sand, foam, and deep-ocean surfaces.
- Preserve at least 3:1 for graphical objects against adjacent chart surfaces and 4.5:1 for text placed on a zone fill.
- Keep fills fully opaque in charts; alpha blends are unpredictable across the new warm/cool surfaces.
- Continue pairing zone color with position, labels, target values, and chart geometry. Color is supplementary.
- In the player, reserve the brighter `line` token for live power/value emphasis and the calmer `fill` token for large chart areas.

### 6.2 Operational semantics

Add explicit app roles rather than borrowing brand roles:

| State | Role | Color family | Non-color cue |
| --- | --- | --- | --- |
| Connected / synced / completed | `connected` or `success` | kelp | check/Bluetooth icon and state label |
| Scanning / syncing | `info` | Pacific | progress indicator and verb |
| Reconnecting / stale | `warning` | warning gold | warning icon, “Reconnecting” or age label |
| Disconnected / unavailable | neutral until it blocks a ride | coastal gray | slash icon and explicit status |
| Failed / unsafe | `error` | error red | error icon, message, recovery action |
| Destructive | `error` | error red | destructive verb and confirmation context |

Coral must not replace error red. Kelp must not be used as the general brand color. This keeps status recognition stable.

### 6.3 Chart series

Define visualization tokens once:

- Actual power: Pacific blue, thickest line.
- Heart rate: coral-red distinct from both brand coral and error red; never used without a “HR” label/legend.
- Cadence: kelp or cool mint, with a distinct line style where series overlap.
- Elevation: neutral ocean/sand tint so it recedes behind performance data.
- Grid/cursor: opaque theme roles chosen for contrast rather than `onSurface.copy(alpha = ...)` wherever possible.

Audit color-deficiency behavior with grayscale and protanopia/deuteranopia simulation. Where two series share luminance, differentiate line width, dash pattern, marker, or direct label.

## 7. Component and screen application

### 7.1 Global components

- **Primary button:** Pacific solid; foam text in light mode, deep-ocean text on the brighter dark-mode primary if contrast requires it.
- **Secondary button:** neutral/sand container with Pacific text; do not make coral the default secondary button.
- **Selected chip/nav item:** sea-glass container + deep-Pacific content. Unselected items remain neutral.
- **Cards:** use the surface ladder for separation; avoid outlines unless surfaces overlap or accessibility requires them.
- **Text fields:** neutral container/outline; Pacific focus indicator; error red only after validation fails.
- **Sheets/dialogs:** one step above their parent surface, with a scrim. Do not stack multiple pale translucent layers.
- **Pressed/disabled states:** derive from semantic pairs and M3 state layers. Do not introduce hand-authored white/black alpha overlays per screen.

### 7.2 Home

- Replace `Brush.linearGradient(listOf(Green20, Green40))` with a named theme gradient, deep Pacific to Pacific blue.
- Replace hard-coded `Color.White` and `Color.Black.copy(...)` in the hero and connection strip with `onHero`, `heroActionContainer`, and `heroScrim` roles.
- Let the hero be the richest brand expression. Keep the rest of the page predominantly sand/foam so the app does not become uniformly blue.
- Planned workout remains a sea-glass informational surface, while actual sync success uses kelp.

### 7.3 Workout library and builder

- Use neutral cards so zone-colored mini charts stay dominant.
- Use Pacific for Sync/import progress and selected filters.
- Use coral only for a small editorial accent such as the ramp-test assessment badge, subject to usability testing.
- Keep interval delete/error states on error red, visually separated from the Z6 chart red.

### 7.4 Workout and free-ride players

- Treat dark mode as the preferred visual reference during design, but honor the user's selected/system theme.
- Deep-ocean background, ocean-slate controls, warm-foam text, and minimal brand color outside interactive/action states.
- Keep the current power value zone-aware where that behavior is useful; target/progress uses zone fill, while measured power chart line remains Pacific.
- Start/Pause/Resume uses Pacific. Stop is neutral in the frequent-control layer and becomes error red in the confirmation layer.
- Lost connection uses warning gold while reconnecting, error only after recovery fails or the ride becomes unsafe.
- Validate at arm's length in portrait and landscape with sweaty-hand conditions; large metrics target 4.5:1 even if their size technically permits 3:1.

### 7.5 History and completion

- Use sand/ocean neutral surfaces for summaries.
- Use kelp for successful upload/completion and coral as a restrained celebratory accent.
- Charts keep data colors; the latest-week emphasis can use Pacific rather than kelp.

### 7.6 Profile, devices, routes, and coaching

- Profile selection/focus uses Pacific; saved state uses kelp.
- Connected devices use kelp, scanning uses Pacific, degraded signal/reconnect uses gold, and permission failures use error red.
- Route elevation uses Pacific fill with a neutral baseline; grade/severity colors remain functional.
- Coach information uses sea-glass; motivation may use pale coral; safety feedback always uses error roles.

## 8. Dark-mode behavior and user preference

### Phase A: palette parity

Keep the current `isSystemInDarkTheme()` behavior while both themes are redesigned and tested. Do not add preference state in the same change as the palette migration; that complicates rollback and screenshot comparison.

### Phase B: optional explicit preference

If product testing shows riders want dark mode on the trainer regardless of system setting:

- Add `ThemePreference { SYSTEM, LIGHT, DARK }` to local app preferences.
- Add a segmented or radio preference under Profile → App → Appearance.
- Resolve preference above `TrainerLoopTheme` and pass `darkTheme` explicitly.
- Apply the chosen mode to edge-to-edge system bar icon appearance.
- Persist locally; no account/sync dependency.
- Default existing and new users to `SYSTEM`.

Avoid an automatic “dark while riding” switch in the first release. A theme change at workout start is a large brightness transition and can feel surprising. Explore it only as an opt-in later, with a gentle crossfade and no mid-session switching.

## 9. Implementation phases

### Phase 0 — approve the direction and inventory (0.5–1 day)

1. Produce one light and one dark palette board showing every foundation family and M3 semantic pair.
2. Apply the candidate colors to four representative static states: Home, Workout Library, active Player portrait, active Player landscape.
3. Include disconnected/reconnecting and error examples before approval.
4. Decide whether coral is the secondary brand accent or only an editorial accent. Recommendation: editorial/accent only.
5. Freeze token names, not necessarily every hex, before implementation.

**Exit:** design/product agree that the system feels “California coast” without reducing training clarity.

### Phase 1 — token and test foundation (1–2 days)

Files:

- `android/app/src/main/java/com/trainerloop/ui/theme/Color.kt`
- `android/app/src/main/java/com/trainerloop/ui/theme/Theme.kt`
- new `android/app/src/main/java/com/trainerloop/ui/theme/TrainerLoopColors.kt` if custom roles are needed
- `android/app/src/test/java/com/trainerloop/ui/theme/ThemeContrastTest.kt`

Tasks:

1. Add complete Pacific, sea-glass, sand, ocean, coral, kelp, warning, and error tonal ramps.
2. Build full light and dark M3 schemes; explicitly populate every role currently populated so no Material purple fallback appears.
3. Add app semantic roles only for concepts Material does not represent clearly.
4. Expand contrast tests to include `onSecondary/secondary`, `onTertiary/tertiary`, all container pairs, disabled text where meaningful, and every custom role.
5. Add tests that the light and dark surface ladders are monotonic and visibly separated.
6. Add a temporary theme catalog composable or preview showing all roles, state layers, buttons, chips, fields, cards, and status banners.

**Exit:** the theme compiles, every semantic pair passes automated contrast, and the catalog has no fallback colors.

### Phase 2 — remove theme exceptions (1–2 days)

Files found in the current audit:

- `android/app/src/main/java/com/trainerloop/ui/home/HomeScreen.kt`
- `android/app/src/main/java/com/trainerloop/ui/complete/CoachSummaryCard.kt`
- chart components under `android/app/src/main/java/com/trainerloop/ui/components/`

Tasks:

1. Replace hard-coded Home hero white/black overlays with named roles.
2. Replace the hard-coded fatigue reference `#E57373` with a chart semantic token.
3. Audit all `Color(...)`, `Color.White/Black`, and direct foundation imports outside the theme and visualization layers.
4. Replace alpha-composited hierarchy with tonal surface roles where practical.
5. Verify launcher resources separately; do not change black/white adaptive-icon resources unless a new app icon is in scope.

**Exit:** screen code consumes Material/app semantic roles; remaining direct colors are documented data-visualization exceptions.

### Phase 3 — zone and chart harmonization (1–2 days)

Files:

- `android/app/src/main/java/com/trainerloop/ui/theme/ZoneColors.kt`
- `android/app/src/main/java/com/trainerloop/ui/components/WorkoutChart.kt`
- `WorkoutMiniChart.kt`, `SampleChart.kt`, `RouteProfileChart.kt`
- player and session-detail chart call sites
- `ZoneColorsContrastTest.kt`

Tasks:

1. Re-evaluate zone colors against all new chart surfaces without changing their semantic sequence.
2. Add chart tokens for power, HR, cadence, elevation, grid, cursor, and fatigue reference.
3. Add contrast assertions for zone fills/lines against light background, light card, dark background, and every dark card elevation used by a chart.
4. Add tests or golden fixtures for zone boundaries and full opacity.
5. Check overlapping series in grayscale and common color-vision simulations; add dash/width/marker distinctions where needed.

**Exit:** charts remain immediately readable in both themes, and zone identity is at least as clear as before.

### Phase 4 — component and screen rollout (2–4 days)

Roll out in risk order:

1. Theme catalog and shared components.
2. Home, navigation, and Workout Library.
3. Profile, Devices, Routes, History, and completion states.
4. Workout Builder.
5. Workout/free-ride players last, after the palette is stable elsewhere.

Use a temporary build flag only if incremental rollout would expose visibly mixed palettes in production. Otherwise, make the token switch atomic and fix semantic call sites in the same branch.

**Exit:** every screen and important state has light/dark screenshots with no legacy-green visual islands.

### Phase 5 — preference, documentation, and cleanup (1 day; optional preference adds 1 day)

1. Optionally add System / Light / Dark preference as described in section 8.
2. Update `android/README.md` with theme architecture and validation commands.
3. Add a short color specification under `android/docs/app-redesign/` containing approved tokens, semantic usage, and “do not use” rules.
4. Remove deprecated token aliases after all call sites migrate.
5. Record the final decision in an ADR if custom semantic colors or an explicit theme preference become architectural policy.

## 10. Verification plan

### Automated

- Run `./gradlew testDebugUnitTest lint` with JDK 17.
- Keep a 4.5:1 minimum for normal text and interactive icon/content pairs.
- Use 3:1 only for qualifying large text and graphical objects; the live player metrics should still target 4.5:1.
- Assert every zone fill/line against every actual chart background.
- Assert each `on*` token against its paired role; do not test tokens in isolation.
- Add screenshot/golden coverage if the project adopts Compose screenshot testing: Home, Library, Player portrait, Player landscape, Devices reconnecting, and error dialog in both modes.

### Manual on Pixel 2 / real trainer context

- Light and dark modes at minimum, 25%, and maximum practical brightness.
- Indoor daylight, dim room, and direct side glare.
- Portrait at hand distance and landscape at handlebar distance.
- Active, paused, stale telemetry, reconnecting, disconnected, and stop-confirmation states.
- Font scales 1.0, 1.3, and 2.0; TalkBack; grayscale; protanopia and deuteranopia simulation.
- Screenshot review for surface banding on the Pixel 2 display and for muddy alpha blends.
- Confirm theme/system-bar icon contrast on every top-level and modal surface.

### Acceptance checklist

- [ ] A rider can identify primary action, current power, target, interval time, and connection state in under one second in both modes.
- [ ] Brand blue, success green, warning gold, error red, and zone colors have distinct responsibilities.
- [ ] No status depends on hue alone.
- [ ] All theme and zone contrast tests pass.
- [ ] No hard-coded presentation color remains in screen code without a documented reason.
- [ ] Dark cards are distinguishable from the background without excessive borders.
- [ ] Light mode feels warm and coastal without appearing beige or low-contrast.
- [ ] Coral appears as a restrained accent, not as a competing primary action color.
- [ ] Existing power-zone recognition is preserved.
- [ ] Home and player are verified on the physical target device in portrait and landscape.

## 11. Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Pacific brand blue is confused with Z2 blue | Use brand blue on controls/lines and Z2 blue in filled chart geometry; tune luminance and accompany with labels/position |
| Coral is confused with error or HR | Keep error red semantically separate; restrict coral to editorial containers; directly label HR series |
| Warm sand reduces light-mode contrast | Build a complete neutral ramp and test actual token pairs rather than relying on visual judgment |
| Deep-ocean surfaces become muddy on old displays | Use opaque tonal steps, verify on Pixel 2, and avoid low-alpha overlays for structural separation |
| Recoloring zones breaks learned behavior | Preserve hue order and semantic names; harmonize only enough to work on new surfaces |
| A palette-only change leaves legacy hard-coded colors | Make the exception audit a named migration phase and fail review on undocumented direct colors |
| Adding a theme preference expands scope | Ship palette parity first; add preference in a separate change after user need is validated |

## 12. Recommended delivery slices

1. **Design spike:** approved palette board plus six representative light/dark screens.
2. **Theme PR:** foundation tokens, M3 schemes, custom semantics, and contrast tests.
3. **Semantic migration PR:** hard-coded color cleanup and shared component adoption.
4. **Chart PR:** zones, chart-series roles, contrast, and color-vision differentiation.
5. **Screen verification PR:** screenshot baselines, device QA fixes, and documentation.
6. **Optional appearance PR:** persisted System / Light / Dark preference.

Each slice is independently reviewable and keeps the safety-critical player until after the token system and chart behavior are proven.
