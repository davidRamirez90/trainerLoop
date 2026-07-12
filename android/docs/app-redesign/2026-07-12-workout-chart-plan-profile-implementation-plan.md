# Monochrome Workout-Chart Plan Profile — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace zone-colored interval blocks in all workout charts with a single monochrome stepped outline + faint fill, keeping elevation and HR traces highly visible.

**Architecture:** Two new semantic theme roles (`chartPlanOutline`, `chartPlanFill`) feed one shared stepped-profile geometry helper (built on the existing `zoneBands()` sampling) consumed by all three chart renderers: `WorkoutChart` (player portrait + detail), `WorkoutMiniChart` (library/home/builder), and `ImmersiveWorkoutChart` (player landscape). Zone colors remain untouched everywhere else (tooltip dot, builder accents, zone math).

**Tech Stack:** Kotlin, Jetpack Compose Canvas, JUnit4 unit tests. Spec: `docs/app-redesign/2026-07-12-workout-chart-plan-profile-design.md`.

## Global Constraints

- Run all Gradle commands from `android/` with JDK 17.
- Theme tokens are **opaque**; renderers apply alpha (existing `chartElevation.copy(alpha = …)` pattern).
- Outline is **stepped**, never smoothed; free-ride stretches produce a **gap** (separate subpaths), never a diagonal bridge or an invented 0 W baseline.
- No changes to gestures, pan/zoom, sampling, ViewModels, data models, BLE, or `ZoneColors.kt` values.
- No new landscape features (no tooltip/elevation added to `ImmersiveWorkoutChart`).
- Plan outline stroke: 2 dp. Plan fill alpha: `0.08f` (shared constant `PLAN_FILL_ALPHA`).
- Candidate hexes may be tuned later via screenshots, but only inside theme files.

---

### Task 1: Theme roles `chartPlanOutline` / `chartPlanFill`

**Files:**
- Modify: `app/src/main/java/com/trainerloop/ui/theme/TrainerLoopColors.kt`
- Modify: `app/src/test/java/com/trainerloop/ui/theme/ThemeContrastTest.kt`
- Modify: `app/src/debug/java/com/trainerloop/ui/catalog/ThemeCatalog.kt` (chart swatch list, around lines 103–108)
- Modify: `docs/app-redesign/2026-07-12-token-usage-spec.md` (chart-role table, around line 87)

**Interfaces:**
- Produces: `TrainerLoopColors.chartPlanOutline: Color` and `TrainerLoopColors.chartPlanFill: Color`, available via `MaterialTheme.trainerLoopColors`. Light: outline `Sand40` (#786956), fill `Sand60` (#C6B398). Dark: outline `Neutral60` (#A9A197), fill `Neutral40` (#59666A).

- [ ] **Step 1: Write the failing contrast test**

Add to `ThemeContrastTest.kt` (inside the class; it already has `relativeLuminance(Color)`):

```kotlin
@Test
fun `plan profile outline reads against chart surfaces in both modes`() {
  listOf(
    Triple("light", LightTrainerLoopColors.chartPlanOutline, LightColorScheme.surface),
    Triple("dark", DarkTrainerLoopColors.chartPlanOutline, DarkColorScheme.surface)
  ).forEach { (mode, outline, surface) ->
    val l1 = relativeLuminance(outline)
    val l2 = relativeLuminance(surface)
    val ratio = (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    assertTrue(
      "$mode chartPlanOutline must reach 3:1 non-text contrast on surface (was $ratio)",
      ratio >= 3.0
    )
  }
}
```

If the file already defines a `contrastRatio(a, b)` helper (check the `assertSchemeContrast` body), use it instead of the inline luminance math.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.trainerloop.ui.theme.ThemeContrastTest"`
Expected: compile error `Unresolved reference: chartPlanOutline` (a compile failure is the failing state here).

- [ ] **Step 3: Add the roles**

In `TrainerLoopColors.kt`, add to the data class after `chartCursor`:

```kotlin
  val chartCursor: Color,
  val chartPlanOutline: Color,
  val chartPlanFill: Color
```

In `LightTrainerLoopColors` after `chartCursor = DarkBackground`:

```kotlin
  chartCursor = DarkBackground,
  chartPlanOutline = Sand40,
  chartPlanFill = Sand60
```

In `DarkTrainerLoopColors` after `chartCursor = Foam`:

```kotlin
  chartCursor = Foam,
  chartPlanOutline = Neutral60,
  chartPlanFill = Neutral40
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.trainerloop.ui.theme.ThemeContrastTest"`
Expected: PASS (all tests in class).

- [ ] **Step 5: Add catalog swatches and token-spec rows**

In `ThemeCatalog.kt`, extend the chart swatch list:

```kotlin
    CatalogColor("chartCursor", semantic.chartCursor),
    CatalogColor("chartPlanOutline", semantic.chartPlanOutline),
    CatalogColor("chartPlanFill", semantic.chartPlanFill)
```

In `2026-07-12-token-usage-spec.md`, after the `chartCursor` row:

```markdown
| `chartPlanOutline` | `#786956` (Sand40) | `#A9A197` (Neutral60) | Stepped outline of the planned-effort profile in workout charts. Monochrome — zone colors are never used for plan geometry. |
| `chartPlanFill` | `#C6B398` (Sand60) | `#59666A` (Neutral40) | Faint tint under the plan outline; renderers apply ~8% alpha. |
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/trainerloop/ui/theme/TrainerLoopColors.kt \
  app/src/test/java/com/trainerloop/ui/theme/ThemeContrastTest.kt \
  app/src/debug/java/com/trainerloop/ui/catalog/ThemeCatalog.kt \
  docs/app-redesign/2026-07-12-token-usage-spec.md
git commit -m "feat(theme): chartPlanOutline/chartPlanFill semantic roles"
```

---

### Task 2: Shared stepped-profile geometry

**Files:**
- Create: `app/src/main/java/com/trainerloop/ui/components/PlanProfileGeometry.kt`
- Create: `app/src/test/java/com/trainerloop/ui/components/PlanProfileGeometryTest.kt`

**Interfaces:**
- Consumes: `ZoneBand(zone, startSec, endSec, targetWatts)` and `zoneBands(...)` from `WorkoutChart.kt` (unchanged).
- Produces:
  - `internal const val PLAN_FILL_ALPHA = 0.08f`
  - `internal data class PlanProfilePoint(val timeSec: Float, val watts: Int)`
  - `internal fun planProfileRuns(bands: List<ZoneBand>): List<List<PlanProfilePoint>>` — one polyline (vertex list, time/watts space) per contiguous run of bands; a new run starts wherever `band.startSec != previousBand.endSec` (free-ride gap).
  - `internal fun planPeakWatts(bands: List<ZoneBand>): Int`
  - `internal fun buildPlanProfilePaths(runs, outline: Path, fill: Path, xForTime: (Float) -> Float, yForPower: (Float) -> Float, baselineY: Float)` — rewinds and fills the two passed-in `Path` objects (callers keep scratch paths).

- [ ] **Step 1: Write the failing tests**

Create `PlanProfileGeometryTest.kt` (pure JVM — no `Path` assertions; reuse the segment builders style from `ZoneBandsTest.kt`):

```kotlin
package com.trainerloop.ui.components

import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.WorkoutSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanProfileGeometryTest {

  private val ftp = 200

  private fun step(id: String, durationSec: Int, watts: Int) = WorkoutSegment.Step(
    id = id,
    durationSec = durationSec,
    label = null,
    phase = SegmentPhase.WORK,
    isWork = true,
    targetRange = TargetRange(watts, watts)
  )

  @Test
  fun `two steps produce one run tracing a staircase`() {
    val bands = zoneBands(
      listOf(step("a", 300, 120), step("b", 300, 180)),
      ftp, winStartSec = 0f, winEndSec = 600f, stepSec = 3f
    )
    val runs = planProfileRuns(bands)
    assertEquals(1, runs.size)
    val run = runs.single()
    // Staircase: (0,120) (300,120) (300,180) (600,180)
    assertEquals(PlanProfilePoint(0f, 120), run.first())
    assertEquals(PlanProfilePoint(600f, 180), run.last())
    assertTrue(run.contains(PlanProfilePoint(300f, 120)))
    assertTrue(run.contains(PlanProfilePoint(300f, 180)))
    // Time never decreases (steps are vertical, never diagonal-backward).
    run.zipWithNext().forEach { (a, b) -> assertTrue(a.timeSec <= b.timeSec) }
  }

  @Test
  fun `free ride in the middle splits the outline into two runs`() {
    val bands = zoneBands(
      listOf(
        step("a", 300, 150),
        WorkoutSegment.FreeRide("f", 300, null, SegmentPhase.WORK),
        step("b", 300, 200)
      ),
      ftp, winStartSec = 0f, winEndSec = 900f, stepSec = 3f
    )
    val runs = planProfileRuns(bands)
    assertEquals(2, runs.size)
    assertEquals(300f, runs[0].last().timeSec)
    assertEquals(600f, runs[1].first().timeSec)
  }

  @Test
  fun `ramp produces a single monotonic staircase run`() {
    val bands = zoneBands(
      listOf(
        WorkoutSegment.Ramp(
          id = "r", durationSec = 300, label = null,
          phase = SegmentPhase.WORK, isWork = true,
          startPower = 100, endPower = 240
        )
      ),
      ftp, winStartSec = 0f, winEndSec = 300f, stepSec = 3f
    )
    val runs = planProfileRuns(bands)
    assertEquals(1, runs.size)
    val run = runs.single()
    assertTrue(run.size > 2)
    run.zipWithNext().forEach { (a, b) ->
      assertTrue(a.timeSec <= b.timeSec)
      assertTrue(a.watts <= b.watts)
    }
  }

  @Test
  fun `empty bands produce no runs and zero peak`() {
    assertTrue(planProfileRuns(emptyList()).isEmpty())
    assertEquals(0, planPeakWatts(emptyList()))
  }

  @Test
  fun `peak watts is the highest band target`() {
    val bands = zoneBands(
      listOf(step("a", 300, 150), step("b", 60, 420)),
      ftp, winStartSec = 0f, winEndSec = 360f, stepSec = 3f
    )
    assertEquals(420, planPeakWatts(bands))
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.trainerloop.ui.components.PlanProfileGeometryTest"`
Expected: compile error `Unresolved reference: planProfileRuns`.

- [ ] **Step 3: Implement the geometry helper**

Create `PlanProfileGeometry.kt`:

```kotlin
package com.trainerloop.ui.components

import androidx.compose.ui.graphics.Path

/** Alpha applied by renderers to [com.trainerloop.ui.theme.TrainerLoopColors.chartPlanFill]. */
internal const val PLAN_FILL_ALPHA = 0.08f

internal data class PlanProfilePoint(val timeSec: Float, val watts: Int)

/**
 * Groups [bands] into contiguous runs and emits stepped-polyline vertices per
 * run. A run breaks wherever bands are not contiguous (free-ride stretches
 * emit no band), so the outline gaps instead of bridging a plan that does not
 * exist.
 */
internal fun planProfileRuns(bands: List<ZoneBand>): List<List<PlanProfilePoint>> {
  val runs = mutableListOf<MutableList<PlanProfilePoint>>()
  var prevEndSec = Float.NaN
  bands.forEach { band ->
    val run = if (runs.isEmpty() || band.startSec != prevEndSec) {
      mutableListOf<PlanProfilePoint>().also { runs.add(it) }
    } else {
      runs.last()
    }
    if (run.lastOrNull()?.watts != band.targetWatts) {
      run.add(PlanProfilePoint(band.startSec, band.targetWatts))
    }
    run.add(PlanProfilePoint(band.endSec, band.targetWatts))
    prevEndSec = band.endSec
  }
  return runs
}

internal fun planPeakWatts(bands: List<ZoneBand>): Int =
  bands.maxOfOrNull { it.targetWatts } ?: 0

/**
 * Rewinds [outline] and [fill] and rebuilds them from [runs]. The outline
 * traces each run's staircase; the fill closes each run down to [baselineY].
 * Callers own the Path instances so per-frame renderers can reuse scratch
 * paths.
 */
internal fun buildPlanProfilePaths(
  runs: List<List<PlanProfilePoint>>,
  outline: Path,
  fill: Path,
  xForTime: (Float) -> Float,
  yForPower: (Float) -> Float,
  baselineY: Float
) {
  outline.rewind()
  fill.rewind()
  runs.forEach { run ->
    if (run.size < 2) return@forEach
    val firstX = xForTime(run.first().timeSec)
    val firstY = yForPower(run.first().watts.toFloat())
    outline.moveTo(firstX, firstY)
    fill.moveTo(firstX, baselineY)
    fill.lineTo(firstX, firstY)
    run.drop(1).forEach { point ->
      val x = xForTime(point.timeSec)
      val y = yForPower(point.watts.toFloat())
      outline.lineTo(x, y)
      fill.lineTo(x, y)
    }
    fill.lineTo(xForTime(run.last().timeSec), baselineY)
    fill.close()
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.trainerloop.ui.components.PlanProfileGeometryTest"`
Expected: PASS (5 tests). Also run `--tests "com.trainerloop.ui.components.ZoneBandsTest"` — unchanged, PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/trainerloop/ui/components/PlanProfileGeometry.kt \
  app/src/test/java/com/trainerloop/ui/components/PlanProfileGeometryTest.kt
git commit -m "feat(chart): shared stepped plan-profile geometry with free-ride gaps"
```

---

### Task 3: WorkoutMiniChart restyle

**Files:**
- Modify: `app/src/main/java/com/trainerloop/ui/components/WorkoutMiniChart.kt`

**Interfaces:**
- Consumes: `planProfileRuns`, `planPeakWatts`, `buildPlanProfilePaths`, `PLAN_FILL_ALPHA` (Task 2); `MaterialTheme.trainerLoopColors.chartPlanOutline` (Task 1).
- Produces: unchanged public signature `WorkoutMiniChart(workout, ftp, modifier, chartHeight, maxPowerAxis, lineColor)`. New semantics: `lineColor` defaults to `chartPlanOutline`; `maxPowerAxis` is now the axis **minimum** (axis = `max(maxPowerAxis, plan peak)`); the fill is always `lineColor.copy(alpha = PLAN_FILL_ALPHA)` so surface-aware overrides (Home passes `content`) tint consistently. Call sites need no changes.

- [ ] **Step 1: Rewrite the drawing code**

Replace the whole file body below the imports (and fix imports: drop `isSystemInDarkTheme`, `ZoneColors`, `Fill`; add `trainerLoopColors`):

```kotlin
package com.trainerloop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.trainerloop.data.model.Workout
import com.trainerloop.domain.WorkoutMath
import com.trainerloop.domain.WorkoutSummaryMath
import com.trainerloop.ui.theme.trainerLoopColors

@Composable
fun WorkoutMiniChart(
  workout: Workout,
  ftp: Int,
  modifier: Modifier = Modifier,
  chartHeight: Dp = 60.dp,
  maxPowerAxis: Int = 400,
  lineColor: Color = MaterialTheme.trainerLoopColors.chartPlanOutline
) {
  val totalDuration = remember(workout) {
    WorkoutMath.totalDurationSec(workout.segments)
  }
  val isFreeRideOnly = remember(workout) {
    WorkoutSummaryMath.isFreeRideOnly(workout)
  }
  // Plan geometry depends only on the workout and FTP, not on canvas size.
  val runs = remember(workout, ftp) {
    if (totalDuration == 0) emptyList() else planProfileRuns(
      zoneBands(
        segments = workout.segments,
        ftp = ftp,
        winStartSec = 0f,
        winEndSec = totalDuration.toFloat(),
        stepSec = (totalDuration / 120f).coerceAtLeast(1f)
      )
    )
  }
  val axisMax = remember(runs, maxPowerAxis) {
    maxOf(maxPowerAxis, runs.maxOfOrNull { run -> run.maxOf { it.watts } } ?: 0)
  }
  val placeholderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)

  Canvas(
    modifier = modifier
      .fillMaxWidth()
      .height(chartHeight)
      .semantics { contentDescription = workoutProfileSummary(workout.segments) }
  ) {
    if (isFreeRideOnly) {
      val bandHeight = size.height * 0.4f
      val bandWidth = size.width - 4.dp.toPx()
      val bandTop = (size.height - bandHeight) / 2f
      val dash = PathEffect.dashPathEffect(
        floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
        0f
      )
      drawRoundRect(
        color = placeholderColor,
        topLeft = Offset(2.dp.toPx(), bandTop),
        size = Size(bandWidth, bandHeight),
        cornerRadius = CornerRadius(8.dp.toPx()),
        style = Stroke(width = 2.dp.toPx(), pathEffect = dash)
      )
      return@Canvas
    }

    if (totalDuration == 0 || runs.isEmpty()) return@Canvas

    val width = size.width
    val heightPx = size.height
    val padding = 2.dp.toPx()
    val drawHeight = heightPx - padding * 2
    val chartBottom = heightPx - padding

    val outline = Path()
    val fill = Path()
    buildPlanProfilePaths(
      runs = runs,
      outline = outline,
      fill = fill,
      xForTime = { sec -> (sec / totalDuration.toFloat()) * width },
      yForPower = { watts ->
        chartBottom - (watts / axisMax.toFloat()).coerceIn(0f, 1f) * drawHeight
      },
      baselineY = chartBottom
    )
    drawPath(fill, color = lineColor.copy(alpha = PLAN_FILL_ALPHA))
    drawPath(outline, color = lineColor, style = Stroke(width = 2.dp.toPx()))
  }
}
```

Note: `planPeakWatts` is not used here because the runs are already in scope; the inline `maxOf` over runs is equivalent. Do not add unused imports.

- [ ] **Step 2: Verify compile + existing tests**

Run: `./gradlew testDebugUnitTest --tests "com.trainerloop.ui.components.*"`
Expected: PASS (`WorkoutChartTest`, `ZoneBandsTest`, `PlanProfileGeometryTest` all green; mini chart has no unit tests of its own).

- [ ] **Step 3: Check call sites still make sense (no code changes expected)**

- `WorkoutLibraryScreen.kt:315` and `WorkoutBuilderScreen.kt:137` — use defaults; now render the neutral outline.
- `HomeScreen.kt:405` — passes `lineColor = content` (colored container); keep the override.
- `HomeScreen.kt:465` — defaults; fine.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/trainerloop/ui/components/WorkoutMiniChart.kt
git commit -m "feat(chart): mini chart renders monochrome plan profile, axis expands past 400W peaks"
```

---

### Task 4: WorkoutChart restyle (player portrait + detail)

**Files:**
- Modify: `app/src/main/java/com/trainerloop/ui/components/WorkoutChart.kt`

**Interfaces:**
- Consumes: Task 1 roles, Task 2 helpers.
- Produces: unchanged public signature `WorkoutChart(segments, samples, elapsedSec, ftp, modifier, elevationProfile)`. `zoneBands`, `ZoneBand`, tooltip, gestures, and accessibility summary unchanged.

- [ ] **Step 1: Swap scratch paths and colors**

Replace (around lines 191–202):

```kotlin
  val zoneScratchPaths = remember { Array(6) { Path() } }
```

with:

```kotlin
  val planOutlineScratchPath = remember { Path() }
  val planFillScratchPath = remember { Path() }
```

and replace the color block:

```kotlin
  val semanticColors = MaterialTheme.trainerLoopColors
  val cursorColor = semanticColors.chartCursor.copy(alpha = 0.7f)
  val gridColor = semanticColors.chartGrid.copy(alpha = 0.15f)
  val hrLineColor = semanticColors.chartHeartRate
  val powerLineColor = semanticColors.chartPower
  val planOutlineColor = semanticColors.chartPlanOutline
  val planFillColor = semanticColors.chartPlanFill.copy(alpha = PLAN_FILL_ALPHA)
  // With the opaque zone blocks gone, the terrain no longer needs to fight
  // for legibility; these alphas are tuned against the bare card surface.
  val elevationFillColor = semanticColors.chartElevation.copy(alpha = 0.30f)
  val elevationLineColor = semanticColors.chartElevation.copy(alpha = 0.65f)
```

Remove the now-unused `darkTheme` val and `isSystemInDarkTheme` / `ZoneColors` imports **only if** no other reference remains (`ZoneColors` is NOT imported for the tooltip — it uses `zoneColorSet`, keep that import).

- [ ] **Step 2: Reorder the Canvas drawing**

Inside the Canvas block, replace the section from the gridlines comment through the selected-interval highlight (current lines ~400–481) with, in this exact order:

```kotlin
        // 1) Faint plan fill — the quiet backdrop everything sits on.
        val planRuns = planProfileRuns(
          zoneBands(
            segments = segments,
            ftp = ftp,
            winStartSec = winStart,
            winEndSec = winEnd,
            stepSec = (winSpan / 200f).coerceAtLeast(1f)
          )
        )
        buildPlanProfilePaths(
          runs = planRuns,
          outline = planOutlineScratchPath,
          fill = planFillScratchPath,
          xForTime = ::xForTime,
          yForPower = ::yForPower,
          baselineY = chartBottom
        )
        drawPath(planFillScratchPath, color = planFillColor)

        // 2) Selected-interval highlight: wash + edge strokes. The wash alone
        // was calibrated against opaque zone fills and is too faint over the
        // bare surface, so the boundaries get explicit lines.
        selectedIndex?.let { idx ->
          bounds.getOrNull(idx)?.let { (s, e, _) ->
            val xs = xForTime(s.toFloat()).coerceIn(0f, width)
            val xe = xForTime(e.toFloat()).coerceIn(0f, width)
            drawRect(
              color = cursorColor.copy(alpha = selectedHighlightAlpha),
              topLeft = Offset(xs, 0f),
              size = Size(xe - xs, heightPx)
            )
            val edgeAlpha = (selectedHighlightAlpha * 3f).coerceAtMost(0.6f)
            listOf(xs, xe).forEach { x ->
              drawLine(
                color = cursorColor.copy(alpha = edgeAlpha),
                start = Offset(x, 0f),
                end = Offset(x, heightPx),
                strokeWidth = 1.dp.toPx()
              )
            }
          }
        }

        // 3) Gridlines at FTP and FTP/2, above the fill so they stay legible.
        if (ftp > 0) {
          val gridStrokeWidth = 1.dp.toPx()
          val ftpY = yForPower(ftp.toFloat())
          drawLine(color = gridColor, start = Offset(0f, ftpY), end = Offset(width, ftpY), strokeWidth = gridStrokeWidth)
          val halfFtpY = yForPower((ftp / 2).toFloat())
          drawLine(color = gridColor, start = Offset(0f, halfFtpY), end = Offset(width, halfFtpY), strokeWidth = gridStrokeWidth)
        }
```

Then keep the existing elevation block unchanged in place (it now draws over the faint fill instead of opaque blocks — update its stale comment about "opaque zone fills"), and immediately **after** the elevation block add:

```kotlin
        // 5) Plan outline above the terrain so low recovery targets are not
        // hidden behind the elevation silhouette.
        drawPath(planOutlineScratchPath, color = planOutlineColor, style = Stroke(width = 2.dp.toPx()))
```

Delete the old zone-fill block (`zoneScratchPaths.forEach { it.rewind() }` … `drawPath(path, color = ZoneColors.forZone(...))`) and the old standalone selected-interval highlight block. HR trace, power trace, live tail, and cursor blocks stay unchanged after the outline.

Note: `::xForTime` / `::yForPower` references work because both are local functions taking `Float`.

- [ ] **Step 3: Verify compile + tests**

Run: `./gradlew testDebugUnitTest --tests "com.trainerloop.ui.components.*" --tests "com.trainerloop.ui.theme.*"`
Expected: PASS. `WorkoutChartTest` zone assertions still pass — they test `ZoneColors`/`zoneBands`, which are unchanged.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/trainerloop/ui/components/WorkoutChart.kt
git commit -m "feat(player): WorkoutChart monochrome plan profile with reordered layers"
```

---

### Task 5: ImmersiveWorkoutChart restyle (landscape)

**Files:**
- Modify: `app/src/main/java/com/trainerloop/ui/workout/WorkoutScreen.kt` (`ImmersiveWorkoutChart`, around lines 888–1024)

**Interfaces:**
- Consumes: Task 1 roles, Task 2 helpers, existing `zoneBands` from `WorkoutChart.kt`.
- Produces: same landscape behavior (zoom, follow, cursor); no new features.

- [ ] **Step 1: Replace the zone-block loop**

Add plan colors next to the other semantic colors (around line 921):

```kotlin
  val planOutlineColor = semanticColors.chartPlanOutline
  val planFillColor = semanticColors.chartPlanFill.copy(alpha = PLAN_FILL_ALPHA)
```

Replace the `// Zone blocks from zero.` loop (lines ~986–1001) with:

```kotlin
        // Monochrome plan profile: faint fill under a stepped outline.
        val planRuns = planProfileRuns(
          zoneBands(
            segments = segments,
            ftp = ftp,
            winStartSec = 0f,
            winEndSec = totalDuration.toFloat(),
            stepSec = (totalDuration / 400f).coerceAtLeast(1f)
          )
        )
        val planOutline = Path()
        val planFill = Path()
        buildPlanProfilePaths(
          runs = planRuns,
          outline = planOutline,
          fill = planFill,
          xForTime = { sec -> (sec / totalDuration.toFloat()) * width },
          yForPower = { watts ->
            chartBottom - (watts / maxPowerAxis).coerceIn(0f, 1f) * chartHeight
          },
          baselineY = chartBottom
        )
        drawPath(planFill, color = planFillColor)
        drawPath(planOutline, color = planOutlineColor, style = Stroke(width = 2.dp.toPx()))
```

Add imports to `WorkoutScreen.kt` if missing: `com.trainerloop.ui.components.planProfileRuns`, `com.trainerloop.ui.components.buildPlanProfilePaths`, `com.trainerloop.ui.components.zoneBands`, `com.trainerloop.ui.components.PLAN_FILL_ALPHA` (they are `internal` in the same module, so this works).

- [ ] **Step 2: Remove the dead `dark` plumbing**

`ZoneColors.forTarget(target, ftp, dark)` was the only consumer of `dark` inside `ImmersiveWorkoutChart`. Remove the `dark: Boolean` parameter from `ImmersiveWorkoutChart` and the `dark = dark` argument at the call site (line ~891). Then check whether `dark` and the `ZoneColors` import are still used elsewhere in `WorkoutScreen.kt` (portrait code references `ZoneColors` too — grep before deleting the import; delete only if unreferenced).

- [ ] **Step 3: Verify compile + tests**

Run: `./gradlew testDebugUnitTest`
Expected: PASS, no unused-parameter warnings for the touched function.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/trainerloop/ui/workout/WorkoutScreen.kt
git commit -m "feat(player): landscape immersive chart adopts monochrome plan profile"
```

---

### Task 6: Documentation amendments + full verification

**Files:**
- Modify: `docs/app-redesign/2026-07-12-reference-led-color-and-ui-revamp-plan.md` (§6.2 line ~164, §6.3 line ~172, §6.4 line ~182)

- [ ] **Step 1: Amend the revamp plan**

- §6.2: change "Recent workout uses a mini zone chart" → "Recent workout uses a mini plan-profile chart".
- §6.3: change "Workout cards use foam surfaces, zone chart first" → "Workout cards use foam surfaces, plan-profile chart first".
- §6.4: replace "Chart keeps full zone meaning, a high-contrast cursor, …" with:

```markdown
- Chart renders the plan as a monochrome stepped profile (`chartPlanOutline`/`chartPlanFill`); zone meaning survives in the tap tooltip and FTP gridlines only (superseded per `2026-07-12-workout-chart-plan-profile-design.md`). High-contrast cursor, elapsed/remaining/total labels, and Full/Focus framing unchanged.
```

- [ ] **Step 2: Full test + lint run**

Run: `./gradlew testDebugUnitTest lint`
Expected: BUILD SUCCESSFUL, no new lint errors.

- [ ] **Step 3: Visual verification (screenshots)**

Build a debug APK / run on device or emulator and capture light + dark screenshots of: a library workout card, workout detail, active player portrait (with elevation profile), and active player landscape. Check: plan reads as one quiet shape, HR trace clearly dominant, elevation silhouette legible, selected-interval highlight visible, free-ride-containing workouts show an outline gap. Tune `chartPlan*` hexes or the elevation alphas (0.30/0.65) **only in theme/chart files** if needed.

- [ ] **Step 4: Commit**

```bash
git add docs/app-redesign/2026-07-12-reference-led-color-and-ui-revamp-plan.md
git commit -m "docs: record plan-profile supersession of zone-colored charts"
```
