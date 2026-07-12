# Workout chart plan-profile design (monochrome intervals)

**Date:** 2026-07-12
**Status:** Approved design, ready for implementation planning
**Scope:** `WorkoutChart`, `WorkoutMiniChart`, `ImmersiveWorkoutChart`, chart theme roles, related tests and docs
**Relates to:** `2026-07-12-reference-led-color-and-ui-revamp-plan.md` (supersedes its §6.4 "chart keeps full zone meaning" for chart rendering) and `2026-07-12-token-usage-spec.md`

## 1. Problem

The interval charts render the planned workout as six zone-colored opaque blocks. The zone palette clashes with the reference-led warm/foam visual system, competes with the measured-data traces (HR, power), and forces the elevation overlay to be drawn as a compensating scrim on top of opaque fills. The rider values a clean, simple profile over per-interval intensity color.

## 2. Outcome

The planned effort renders as one quiet monochrome shape — a thin stepped outline tracing target watts over time with a single faint uniform tint beneath it. Measured data (HR trace, live power) becomes the loudest layer; the elevation silhouette stays clearly readable between the plan backdrop and the traces. Intensity semantics survive only in the player's tap tooltip (zone dot + watt range) and the FTP / half-FTP gridlines.

Decisions confirmed with the user:

- Stepped outline + faint uniform fill; **not** a smoothed spline (splines distort ERG steps).
- Zone info survives in tooltip + FTP gridlines only; no zone-colored chart geometry.
- Applied everywhere these charts appear: library cards, home recent workout, builder live preview, workout detail, player portrait, player landscape.

## 3. Design

### 3.1 Theme roles

Add two semantic roles to `TrainerLoopColors.kt`, both schemes:

- `chartPlanOutline` — muted neutral for the plan outline (sand/neutral-derived; light and dark values designed independently).
- `chartPlanFill` — neutral base for the plan tint.

Tokens are **opaque**; renderers apply alpha (matches the existing `chartElevation.copy(alpha = …)` pattern and keeps contrast tests meaningful). Cover both roles in `ThemeContrastTest`, the debug `ThemeCatalog`, and `2026-07-12-token-usage-spec.md`.

### 3.2 Shared stepped-profile geometry

One shared path builder (fed by the existing `zoneBands()` sampling) produces:

- **Outline subpaths** following the band tops (steps and ramp staircases). Subpaths **break across free-ride stretches** — no diagonal bridging between separated bands, no invented 0 W baseline.
- **Matching fill paths** closing each contiguous run down to the chart baseline.

Consumed by all three renderers so they cannot drift apart. `zoneBands()` and `ZoneColors.kt` remain unchanged (still used by tooltips, builder accents, and zone tests); the per-zone fill drawing (`zoneScratchPaths` / zone rects) is deleted from all three charts.

### 3.3 WorkoutChart (player portrait + workout detail)

Draw order, bottom → top:

1. Plan fill (`chartPlanFill` at ~8% renderer alpha)
2. Selected-interval wash (existing 0.18-alpha `chartCursor` wash) **plus** thin edge strokes at the selected interval's boundaries — the wash alone was calibrated against opaque zone blocks and is too weak over a near-empty chart
3. FTP / half-FTP gridlines (unchanged)
4. Elevation fill + line — alphas retuned upward on real light/dark surfaces via screenshots; current 0.22/0.50 compensated for sitting over opaque fills
5. Plan outline (`chartPlanOutline`, 2 dp) — above elevation so low-target intervals aren't hidden behind terrain
6. HR trace (2 dp `chartHeartRate`), then power trace (2.5 dp `chartPower`)
7. Cursor, tooltip (tooltip keeps zone dot + watt range)

Gestures, pan/zoom, Focus mode, animation, and accessibility summaries are unchanged.

### 3.4 WorkoutMiniChart (library, home, builder, detail)

- Same outline + fill language; zone paths removed.
- `lineColor` default changes from `colorScheme.primary` (brand blue reads as measured power) to `chartPlanOutline`. The parameter stays: Home's recent-workout card passes a surface-aware `content` color because it sits on a colored container; drop that override only if the new role passes contrast there.
- Power axis becomes `max(400, workout peak)` — fixes clipping of >400 W targets while keeping cross-card comparability. Fully dynamic scaling is rejected.
- Free-ride-only dashed placeholder unchanged.

### 3.5 ImmersiveWorkoutChart (player landscape)

In scope — it renders zone rects independently (`WorkoutScreen.kt`, `ZoneColors.forTarget(...).fill`) and would otherwise ship a second visual language in the same ride. It adopts the shared plan-profile rendering and trace styling. **No new features**: no tooltip, no elevation overlay added to landscape.

## 4. Error handling / edge cases

- Free-ride stretches inside structured workouts: gap in outline and fill (no plan geometry).
- Empty/zero-duration workouts: existing early returns unchanged.
- Missing HR samples: existing path-break behavior unchanged.
- Very short intervals at mini-chart scale must still render (outline follows band geometry; no minimum-width collapse).

## 5. Testing

- New unit tests for the shared path builder: steps, ramps, mixed workout/free-ride gap behavior (subpath count), and axis expansion.
- Existing `WorkoutChartTest` / `ZoneBandsTest` zone assertions stay (palette + sampling still used elsewhere).
- `ThemeContrastTest` covers the new roles on actual chart surfaces.
- `./gradlew testDebugUnitTest lint` (JDK 17).
- Light/dark screenshots: library card, workout detail, player portrait active, player landscape active. Elevation/plan alpha values are tuned from these composited screenshots, not from token math alone.

## 6. Documentation updates

- Amend revamp plan §6.3/§6.4 ("zone chart first", "chart keeps full zone meaning") to record this supersession.
- Add `chartPlanOutline` / `chartPlanFill` rows to `2026-07-12-token-usage-spec.md` and the ThemeCatalog swatches.

## 7. Non-goals

- Removing zone semantics outside charts (builder interval accents, tooltip dot, zone math, ramp-test logic).
- Smoothed/curved plan rendering.
- New landscape features (tooltip, elevation).
- Changes to gestures, sampling, ViewModels, data models, or BLE behavior.
