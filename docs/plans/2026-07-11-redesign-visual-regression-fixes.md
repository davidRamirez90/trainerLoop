# Redesign Visual Regression Fixes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the four visual regressions introduced by the 2026-07-10 Apple-design UI redesign: striped interval blocks, hidden elevation profile, overlapping Advanced sliders, and the undiscoverable Home connection strip.

**Architecture:** All four are surgical fixes to existing composables. The chart fixes replace per-step `drawRect` calls with per-zone `Path` fills (a single path fill has no anti-aliasing seams between abutting rects) via a new pure, unit-testable `zoneBands()` helper; the elevation fix is a draw-order + styling change in the same Canvas; the other two are small layout/affordance edits.

**Tech Stack:** Jetpack Compose (Material 3), Canvas drawing, JUnit4 unit tests. Verified on the USB-connected Pixel 2 XL (`adb -s 710KPWQ0470905`, package `com.trainerloop.app`).

## Global Constraints

- Working directory for all commands: `/Users/david.ramirez/Projects/trainer-loop/android`.
- Floor device is a Pixel 2 XL on Android 11 — chart work stays in `Canvas` with remembered/scratch `Path`s; no per-frame allocations in the draw loop beyond what exists today.
- All animation uses existing `MotionSpec` tokens / `reducedMotionAware` — no ad-hoc specs (per the redesign plan).
- Indentation is 2 spaces (repo convention).
- Unit tests run with `./gradlew :app:testDebugUnitTest`; on-device verification with `./gradlew installDebug` + `adb exec-out screencap`.
- Each commit message ends with: `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`

## Root causes (from the on-device debugging session, 2026-07-11)

| # | Symptom | Root cause | Introduced by |
|---|---------|-----------|---------------|
| 1 | Interval blocks look like many thin vertical bars | `WorkoutChart.kt:424-440` and `WorkoutMiniChart.kt:105-113` draw one `drawRect` per ~`winSpan/200` (or `/120`) time step. Adjacent anti-aliased rect edges at float x-coordinates never sum to full coverage, leaving a light hairline seam between every pair. The seams were invisible at the old `alpha 0.55` fills; commit `69d121a` (full-alpha zone palette) made them read as stripes. | `69d121a` |
| 2 | Elevation profile hidden behind interval blocks | `WorkoutChart.kt:393-412` draws the elevation silhouette *first*, then the interval blocks paint over it full-height-from-zero. At the old 0.55 alpha the terrain showed through; at full alpha it is completely covered (visible today only over free-ride segments, where blocks have zero height). | `69d121a` |
| 3 | "Ready to ride" connection strip not evidently tappable | `HomeScreen.kt:486-514`: the whole strip is `clickable` → Devices, but with `indication = null`, no chevron, no action wording — it reads as a passive status readout ("Trainer · —"). | `6d44b64` |
| 4 | Advanced virtual-ride sliders stack on top of each other | `SettingsScreen.kt:260-312`: four `LabeledSlider`s + a `TextButton` are direct children of `AnimatedVisibility`, whose content scope measures like a `Box` — all five children overlap at the top-left. | `94739ec` |

---

### Task 1: `zoneBands()` pure helper — merge sampled steps into seam-free zone bands

**Files:**
- Modify: `app/src/main/java/com/trainerloop/ui/components/WorkoutChart.kt` (add helper near the other `internal fun`s at the bottom, after `workoutProfileSummary`)
- Test: `app/src/test/java/com/trainerloop/ui/components/ZoneBandsTest.kt` (create)

**Interfaces:**
- Consumes: `WorkoutMath.targetRangeAt(segments, elapsedSec)`, `ZoneColors.zoneIndex(targetWatts, ftp)` (both exist).
- Produces: `internal data class ZoneBand(val zone: Int, val startSec: Float, val endSec: Float, val targetWatts: Int)` and `internal fun zoneBands(segments: List<WorkoutSegment>, ftp: Int, winStartSec: Float, winEndSec: Float, stepSec: Float): List<ZoneBand>` — consumed by Tasks 2 and 3.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/trainerloop/ui/components/ZoneBandsTest.kt`:

```kotlin
package com.trainerloop.ui.components

import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.WorkoutSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneBandsTest {

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
  fun `flat interval collapses to a single band`() {
    val segments = listOf(step("a", 600, 150)) // 75% FTP -> zone 3
    val bands = zoneBands(segments, ftp, winStartSec = 0f, winEndSec = 600f, stepSec = 3f)
    assertEquals(1, bands.size)
    assertEquals(3, bands[0].zone)
    assertEquals(150, bands[0].targetWatts)
    assertEquals(0f, bands[0].startSec)
    assertEquals(600f, bands[0].endSec)
  }

  @Test
  fun `two flat intervals produce two contiguous bands`() {
    val segments = listOf(step("a", 300, 120), step("b", 300, 180))
    val bands = zoneBands(segments, ftp, winStartSec = 0f, winEndSec = 600f, stepSec = 3f)
    assertEquals(2, bands.size)
    assertEquals(bands[0].endSec, bands[1].startSec)
    assertEquals(120, bands[0].targetWatts)
    assertEquals(180, bands[1].targetWatts)
  }

  @Test
  fun `ramp produces contiguous bands with rising targets`() {
    val segments = listOf(
      WorkoutSegment.Ramp(
        id = "r",
        durationSec = 300,
        label = null,
        phase = SegmentPhase.WORK,
        isWork = true,
        startPower = 100,
        endPower = 240
      )
    )
    val bands = zoneBands(segments, ftp, winStartSec = 0f, winEndSec = 300f, stepSec = 3f)
    assertTrue("ramp should produce multiple bands", bands.size > 1)
    // Contiguous: no gaps, no overlaps.
    bands.zipWithNext().forEach { (a, b) -> assertEquals(a.endSec, b.startSec) }
    // Monotonic targets and zones.
    assertTrue(bands.first().targetWatts < bands.last().targetWatts)
    assertTrue(bands.first().zone < bands.last().zone)
    // Full coverage of the window.
    assertEquals(0f, bands.first().startSec)
    assertEquals(300f, bands.last().endSec)
  }

  @Test
  fun `window subset only covers the window`() {
    val segments = listOf(step("a", 600, 150))
    val bands = zoneBands(segments, ftp, winStartSec = 100f, winEndSec = 200f, stepSec = 3f)
    assertEquals(100f, bands.first().startSec)
    assertEquals(200f, bands.last().endSec)
  }

  @Test
  fun `free ride yields no bands`() {
    val segments = listOf(WorkoutSegment.FreeRide("f", 300, null, SegmentPhase.WARMUP))
    val bands = zoneBands(segments, ftp, winStartSec = 0f, winEndSec = 300f, stepSec = 3f)
    assertTrue(bands.isEmpty())
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.ui.components.ZoneBandsTest"`
Expected: FAIL to compile — `zoneBands` / `ZoneBand` unresolved.

- [ ] **Step 3: Implement the helper**

Append to `app/src/main/java/com/trainerloop/ui/components/WorkoutChart.kt` (after `workoutProfileSummary`):

```kotlin
internal data class ZoneBand(
  val zone: Int,
  val startSec: Float,
  val endSec: Float,
  val targetWatts: Int
)

/**
 * Samples the plan across [winStartSec, winEndSec] and merges equal-target
 * runs into bands. Rendering fills all bands of a zone as ONE Path so
 * abutting edges cannot leave anti-aliasing seams (the thin-vertical-bars
 * regression). Free-ride stretches (target 0) produce no band.
 */
internal fun zoneBands(
  segments: List<WorkoutSegment>,
  ftp: Int,
  winStartSec: Float,
  winEndSec: Float,
  stepSec: Float
): List<ZoneBand> {
  if (winEndSec <= winStartSec || stepSec <= 0f) return emptyList()
  val bands = mutableListOf<ZoneBand>()
  var sec = winStartSec
  while (sec < winEndSec) {
    val nextSec = (sec + stepSec).coerceAtMost(winEndSec)
    val range = WorkoutMath.targetRangeAt(segments, sec.toInt())
    val target = (range.low + range.high) / 2
    if (target > 0) {
      val zone = ZoneColors.zoneIndex(target, ftp)
      val last = bands.lastOrNull()
      if (last != null && last.zone == zone && last.targetWatts == target && last.endSec == sec) {
        bands[bands.lastIndex] = last.copy(endSec = nextSec)
      } else {
        bands.add(ZoneBand(zone, sec, nextSec, target))
      }
    }
    sec = nextSec
  }
  return bands
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.ui.components.ZoneBandsTest"`
Expected: 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/trainerloop/ui/components/ZoneBandsTest.kt app/src/main/java/com/trainerloop/ui/components/WorkoutChart.kt
git commit -m "feat(chart): zoneBands helper — merge sampled plan steps into zone bands

Groundwork for seam-free interval rendering: bands of one zone will be
filled as a single Path so abutting rect edges cannot alias into stripes.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: Seam-free interval blocks in `WorkoutChart`

**Files:**
- Modify: `app/src/main/java/com/trainerloop/ui/components/WorkoutChart.kt:424-440` (the "Full-height-from-zero interval blocks" loop) plus scratch-path declarations near line 186.

**Interfaces:**
- Consumes: `zoneBands(...)` from Task 1; existing `ZoneColors.forZone(zone, dark)`.
- Produces: no new API — pure rendering change.

- [ ] **Step 1: Add per-zone scratch paths**

In `WorkoutChart`, next to the existing scratch paths (`WorkoutChart.kt:186-188`), add:

```kotlin
  val zoneScratchPaths = remember { Array(6) { Path() } }
```

- [ ] **Step 2: Replace the per-step drawRect loop**

Replace `WorkoutChart.kt:424-440` (everything from the `// Full-height-from-zero interval blocks…` comment through the `while` loop's closing brace and `sec += step` line):

```kotlin
        // Full-height-from-zero interval blocks over the visible window.
        // All bands of one zone are filled as a single Path: abutting rects
        // inside one path cancel their shared AA edges, so no hairline seams.
        zoneScratchPaths.forEach { it.rewind() }
        zoneBands(
          segments = segments,
          ftp = ftp,
          winStartSec = winStart,
          winEndSec = winEnd,
          stepSec = (winSpan / 200f).coerceAtLeast(1f)
        ).forEach { band ->
          val yTop = yForPower(band.targetWatts.toFloat())
          zoneScratchPaths[band.zone - 1].addRect(
            androidx.compose.ui.geometry.Rect(
              left = xForTime(band.startSec),
              top = yTop,
              right = xForTime(band.endSec),
              bottom = chartBottom
            )
          )
        }
        zoneScratchPaths.forEachIndexed { index, path ->
          if (!path.isEmpty) {
            drawPath(path, color = ZoneColors.forZone(index + 1, darkTheme).fill)
          }
        }
```

(If preferred, add `import androidx.compose.ui.geometry.Rect` and drop the qualified name.)

- [ ] **Step 3: Build and run existing tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.ui.components.*"`
Expected: PASS (ZoneBandsTest + WorkoutChartTest).

- [ ] **Step 4: Verify on device**

```bash
./gradlew installDebug
adb shell am start -n com.trainerloop.app/.MainActivity
# Navigate: Workouts tab → Sweet Spot → (preview chart) → Start Workout (player chart)
adb exec-out screencap -p > /tmp/chart-check.png
```

Expected: interval blocks are solid, uniform fills — zero vertical hairlines at 100% zoom on the screenshot. Check both Workout Preview and the player, light and dark theme (`adb shell "cmd uimode night yes"` / `night no`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/trainerloop/ui/components/WorkoutChart.kt
git commit -m "fix(chart): interval blocks render as solid fills, not striped bars

Per-step drawRect calls left an AA seam between every abutting pair —
invisible at the old 0.55-alpha fills, stripes at the new full-alpha
palette. Fill all bands of a zone as one Path instead.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: Seam-free interval blocks in `WorkoutMiniChart`

**Files:**
- Modify: `app/src/main/java/com/trainerloop/ui/components/WorkoutMiniChart.kt:86-118`

**Interfaces:**
- Consumes: `zoneBands(...)` from Task 1 (same package).
- Produces: no new API.

- [ ] **Step 1: Replace the per-step drawRect loop**

Replace the block at `WorkoutMiniChart.kt:86-118` (from `val step = …` through the final `drawPath(path, …)`) with band-based rendering. The outline line on top is preserved — it is built from the same bands so it cannot drift from the fills:

```kotlin
    val stepSec = (totalDuration / 120f).coerceAtLeast(1f)
    val bands = zoneBands(
      segments = workout.segments,
      ftp = ftp,
      winStartSec = 0f,
      winEndSec = totalDuration.toFloat(),
      stepSec = stepSec
    )

    // Zone fills: one Path per zone, so abutting bands can't leave AA seams.
    val zonePaths = Array(6) { Path() }
    bands.forEach { band ->
      val yTop = yForPower(band.targetWatts)
      zonePaths[band.zone - 1].addRect(
        androidx.compose.ui.geometry.Rect(
          left = xForTime(band.startSec.toInt()),
          top = yTop,
          right = xForTime(band.endSec.toInt()),
          bottom = chartBottom
        )
      )
    }
    zonePaths.forEachIndexed { index, path ->
      if (!path.isEmpty) {
        drawPath(path, color = ZoneColors.forZone(index + 1, darkTheme).fill, style = Fill)
      }
    }

    // Stepped outline along the top of the profile.
    val outline = Path()
    var started = false
    bands.forEach { band ->
      val y = yForPower(band.targetWatts)
      val xStart = xForTime(band.startSec.toInt())
      val xEnd = xForTime(band.endSec.toInt())
      if (!started) {
        outline.moveTo(xStart, y)
        started = true
      } else {
        outline.lineTo(xStart, y)
      }
      outline.lineTo(xEnd, y)
    }
    if (started) drawPath(outline, color = lineColor, style = Stroke(width = 2.dp.toPx()))
```

Note: the mini chart is drawn once per card (not per frame at 1 Hz), so building the `Path`s inside the draw block is fine here.

- [ ] **Step 2: Build and eyeball**

```bash
./gradlew installDebug && adb shell am start -n com.trainerloop.app/.MainActivity
# Workouts tab
adb exec-out screencap -p > /tmp/minichart-check.png
```

Expected: Sweet Spot / Power Pyramid cards show solid zone blocks with a clean stepped outline; the free-ride-only Endurance Ride card is unchanged (dashed band).

- [ ] **Step 3: Run the unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/trainerloop/ui/components/WorkoutMiniChart.kt
git commit -m "fix(library): mini-chart interval blocks render seam-free via zone paths

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: Elevation profile drawn above interval blocks

**Files:**
- Modify: `app/src/main/java/com/trainerloop/ui/components/WorkoutChart.kt` — move/restyle the elevation block currently at lines 393-412; colors at line 195; scratch paths near line 188.

**Interfaces:**
- Consumes: existing `elevationProfile: DoubleArray?` parameter.
- Produces: no new API.

- [ ] **Step 1: Update scratch paths and colors**

Next to `elevationScratchPath` (line 188) add a second scratch path:

```kotlin
  val elevationLineScratchPath = remember { Path() }
```

Replace the single color at line 195:

```kotlin
  val elevationColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f)
```

with:

```kotlin
  // Drawn OVER the opaque zone fills, so it needs a scrim + edge line to read.
  val elevationFillColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
  val elevationLineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f)
```

- [ ] **Step 2: Move the elevation drawing after the interval blocks**

Delete the elevation block at its current position (lines 393-412, before the gridlines) and insert this immediately **after** the zone-path drawing from Task 2 (before the `selectedIndex` highlight), so the z-order becomes: elevation-under? No — blocks first, then terrain scrim, then highlight, HR/power lines, cursor:

```kotlin
        // Soft terrain silhouette across the bottom 30%, drawn over the zone
        // blocks (they are opaque now) as a translucent scrim + edge line.
        if (elevationProfile != null && elevationProfile.isNotEmpty()) {
          // Local val: smart casts don't reliably reach into local functions.
          val profile = elevationProfile
          val minAlt = profile.min()
          val altSpan = (profile.max() - minAlt).coerceAtLeast(1.0)
          val bandHeight = chartHeight * 0.3f
          fun yForAlt(sec: Float): Float {
            val alt = profile[sec.toInt().coerceIn(0, profile.lastIndex)]
            return chartBottom - ((alt - minAlt) / altSpan).toFloat() * bandHeight
          }
          val elevStep = (winSpan / 200f).coerceAtLeast(1f)
          val linePath = elevationLineScratchPath
          linePath.rewind()
          linePath.moveTo(xForTime(winStart), yForAlt(winStart))
          var t = winStart + elevStep
          while (t <= winEnd) {
            linePath.lineTo(xForTime(t), yForAlt(t))
            t += elevStep
          }
          linePath.lineTo(xForTime(winEnd), yForAlt(winEnd))

          val fillPath = elevationScratchPath
          fillPath.rewind()
          fillPath.addPath(linePath)
          fillPath.lineTo(xForTime(winEnd), chartBottom)
          fillPath.lineTo(xForTime(winStart), chartBottom)
          fillPath.close()

          drawPath(fillPath, color = elevationFillColor)
          drawPath(linePath, color = elevationLineColor, style = Stroke(width = 1.5.dp.toPx()))
        }
```

- [ ] **Step 3: Verify on device (virtual ride on)**

Profile → Virtual Ride toggle is already on for this device.

```bash
./gradlew installDebug && adb shell am start -n com.trainerloop.app/.MainActivity
# Workouts → Sweet Spot → Start Workout
adb exec-out screencap -p > /tmp/elevation-check.png
```

Expected: the terrain silhouette is visible across the *entire* chart width — including over the orange/green interval blocks — as a subtle darker band with a legible top edge. HR/power lines and the cursor still draw on top of it. Check dark theme too.

- [ ] **Step 4: Run unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/trainerloop/ui/components/WorkoutChart.kt
git commit -m "fix(chart): elevation profile draws over interval blocks, not under

Full-alpha zone fills were completely covering the terrain silhouette.
Draw it after the blocks as a translucent scrim with a stroked top edge;
telemetry lines and cursor stay on top.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 5: Advanced virtual-ride sliders — wrap in a Column

**Files:**
- Modify: `app/src/main/java/com/trainerloop/ui/settings/SettingsScreen.kt:260-312`

**Interfaces:**
- Consumes: existing `LabeledSlider` composable (`SettingsScreen.kt:650`).
- Produces: no new API.

- [ ] **Step 1: Wrap the AnimatedVisibility content**

`AnimatedVisibility`'s content scope measures children like a `Box`, so the five children currently overlap. Wrap them (`SettingsScreen.kt:272-312`, the content lambda body) in a `Column`:

```kotlin
      ) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
          LabeledSlider(
            label = "Bike weight",
            valueText = "${uiState.bikeWeightKg} kg",
            hint = null,
            value = uiState.bikeWeightKg.toFloatOrNull() ?: 8.0f,
            valueRange = 5f..15f,
            steps = 19, // 0.5 kg increments
            onValueChange = { viewModel.updateBikeWeight(fmt(it, 1)) }
          )
          LabeledSlider(
            label = "Rolling resistance (Crr)",
            valueText = uiState.crr,
            hint = crrHint(uiState.crr.toDoubleOrNull() ?: 0.005),
            value = uiState.crr.toFloatOrNull() ?: 0.005f,
            valueRange = 0.002f..0.010f,
            steps = 15, // 0.0005 increments
            onValueChange = { viewModel.updateCrr(fmt(it, 4)) }
          )
          LabeledSlider(
            label = "Aero drag (CdA)",
            valueText = "${uiState.cda} m²",
            hint = cdaHint(uiState.cda.toDoubleOrNull() ?: 0.32),
            value = uiState.cda.toFloatOrNull() ?: 0.32f,
            valueRange = 0.15f..0.60f,
            steps = 44, // 0.01 increments
            onValueChange = { viewModel.updateCda(fmt(it, 2)) }
          )
          LabeledSlider(
            label = "Trainer difficulty",
            valueText = "${uiState.trainerDifficultyPct} %",
            hint = "How much of a GPX route's gradient you feel on free rides",
            value = uiState.trainerDifficultyPct.toFloat(),
            valueRange = 0f..100f,
            steps = 19, // 5 % increments
            onValueChange = { viewModel.updateTrainerDifficulty(it.toInt()) }
          )
          TextButton(onClick = { viewModel.resetPhysicsDefaults() }) {
            Text("Reset to defaults")
          }
        }
      }
```

(The `LabeledSlider` calls are byte-identical to today's — only the wrapping `Column` is new. `Arrangement`, `Spacing`, and `fillMaxWidth` are already imported in this file.)

- [ ] **Step 2: Verify on device**

```bash
./gradlew installDebug && adb shell am start -n com.trainerloop.app/.MainActivity
# Profile tab → scroll to Virtual Ride → tap "Advanced"
adb exec-out screencap -p > /tmp/advanced-check.png
```

Expected: four sliders listed vertically (Bike weight, Crr, CdA, Trainer difficulty), each with label + value + hint, "Reset to defaults" below; expand/collapse still animates.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/trainerloop/ui/settings/SettingsScreen.kt
git commit -m "fix(settings): Advanced virtual-ride sliders no longer overlap

AnimatedVisibility's content scope stacks children like a Box; the four
sliders and reset button need an explicit Column.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 6: Home connection strip — visible tap affordance

**Files:**
- Modify: `app/src/main/java/com/trainerloop/ui/home/HomeScreen.kt:486-514` (`ConnectionStrip`)

**Interfaces:**
- Consumes: existing `Icons.AutoMirrored.Filled.KeyboardArrowRight` (already imported at `HomeScreen.kt:30`), existing `pressable`/`clickable` wiring.
- Produces: no new API.

Design rationale: the strip already navigates to Devices, but nothing says so. Three small changes make the intent legible without redesigning the hero: (a) the repo's standard chevron glyph (same as `ActionRow`), (b) "Not paired" instead of the ambiguous "—", (c) an accessibility `onClickLabel`.

- [ ] **Step 1: Add chevron, action label, and clearer disconnected copy**

Replace the `ConnectionStrip` body (`HomeScreen.kt:486-514`) with:

```kotlin
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(Color.Black.copy(alpha = 0.12f))
      .pressable(interactionSource)
      .clickable(
        interactionSource = interactionSource,
        indication = null,
        onClickLabel = "Manage devices",
        onClick = onManageDevices
      )
      .padding(horizontal = Spacing.xl, vertical = Spacing.md),
    verticalAlignment = Alignment.CenterVertically
  ) {
    ConnectionStatus(
      modifier = Modifier.weight(1f),
      icon = if (trainerConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
      connected = trainerConnected,
      label = trainerName ?: "Trainer",
      value = if (trainerConnected) trainerBattery?.let { "$it%" } ?: "Connected" else "Not paired"
    )
    Spacer(modifier = Modifier.width(Spacing.md))
    ConnectionStatus(
      modifier = Modifier.weight(1f),
      icon = if (hrConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
      connected = hrConnected,
      label = "HR",
      value = if (hrConnected) latestHrBpm?.let { "$it bpm" } ?: "Connected" else "Not paired"
    )
    Spacer(modifier = Modifier.width(Spacing.sm))
    Icon(
      imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
      contentDescription = null,
      tint = Color.White.copy(alpha = 0.8f),
      modifier = Modifier.size(20.dp)
    )
  }
```

- [ ] **Step 2: Verify on device**

```bash
./gradlew installDebug && adb shell am start -n com.trainerloop.app/.MainActivity
adb exec-out screencap -p > /tmp/strip-check.png
```

Expected: strip reads `⋮ᛒ Trainer · Not paired    ⋮ᛒ HR · Not paired    ›` — the trailing chevron matches the Workout Builder / GPX Routes rows, and both texts still fit on one line at font scale 1.3 (`adb shell settings put system font_scale 1.3`, then reset to `1.0`). Tapping anywhere on the strip still opens Devices.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/trainerloop/ui/home/HomeScreen.kt
git commit -m "fix(home): connection strip signals it opens Devices

Trailing chevron (matches ActionRow), 'Not paired' instead of an
ambiguous em-dash, and an a11y onClickLabel.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 7: Full visual regression sweep on device

**Files:** none (verification only).

- [ ] **Step 1: Run the whole unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS, no regressions.

- [ ] **Step 2: Screenshot sweep, both themes**

```bash
./gradlew installDebug
for theme in yes no; do
  adb shell cmd uimode night $theme
  adb shell am start -n com.trainerloop.app/.MainActivity
  # Manually or via input taps: Home, Workouts, Sweet Spot preview,
  # player (Start Workout), Profile → Advanced expanded.
  # Screencap each: adb exec-out screencap -p > /tmp/sweep-<screen>-<theme>.png
done
adb shell cmd uimode night no
```

Checklist against the original four reports:
- [ ] Interval blocks: solid fills, no vertical striping (library cards, preview, player; dark + light).
- [ ] Elevation: terrain silhouette visible across the full player chart, including over blocks.
- [ ] Connection strip: chevron + "Not paired" copy; tap opens Devices.
- [ ] Advanced sliders: four sliders + reset button in a vertical list; disclosure animation intact.

- [ ] **Step 3: Nothing to commit** — if any check fails, return to the owning task rather than patching here.
