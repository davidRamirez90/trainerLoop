# GPX Free-Ride with Virtual Gears Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A new free-ride session type: import a GPX route, ride it with ERG-backed virtual gears (14 gears, shift via buttons or volume keys), position advancing along the real track, GPS coordinates in the FIT upload. Plus two Phase 1 fixes: pause→play no longer restarts the workout, and the advanced physics params become sliders.

**Architecture:** Pure domain units (`GpxParser`→`Route`, `VirtualDrivetrain`, `FreeRideTracker`) mirror Phase 1's sim package. `TelemetryRecorder`'s tracker hook generalizes to a `SampleStamper` interface so both trackers stamp samples. Routes persist in a new Room table (JSON-blob pattern, migration 2→3). A new `FreeRideViewModel`/`FreeRideScreen` reuses `WorkoutClock`, `TelemetryRecorder`, and the existing complete/upload flow. Spec: `docs/plans/2026-07-06-gpx-sim-free-ride-design.md`.

**Tech Stack:** Kotlin, Jetpack Compose, Room, kotlinx-serialization, JUnit 4 + turbine + mockk. No new dependencies.

## Global Constraints

- **ERG only** — resistance comes from `FtmsControlManager.setTargetPower`; no FTMS SIM opcode.
- Gear table: 14 gears, ratios geometrically spaced 1.0 → 4.6, wheel circumference 2.096 m, start gear 7. Constants in code, not user-configurable.
- Target power re-sent only on ≥ 2 W change or every 2 s. Power clamped 0–2000 W.
- Cadence EMA ≈ 3 s (alpha 0.28) before computing gear speed.
- GPX: elevation smoothed over ~75 m window, resampled to a 10 m grid, grade clamped ±20 %.
- Trainer difficulty (0–100 %, default 100 %) scales grade **only** in the target-power calculation; position/recorded speed/altitude use true grade.
- All new `TelemetrySample` fields nullable with `null` defaults (old JSON sessions must keep deserializing).
- **Deviation from spec:** GPX parsing uses `javax.xml.parsers.DocumentBuilderFactory` (DOM), not XmlPullParser — that's what `ZwoParser` already uses and it runs in plain JVM unit tests.
- Tests run from `android/`: `./gradlew :app:testDebugUnitTest --tests "<class>"`.
- Test style: JUnit 4, backtick names, `org.junit.Assert.*`; turbine for flows (see `WorkoutClockTest.kt`), mockk for managers (see `WorkoutViewModelTest.kt`).
- Commit after every task, `feat:`/`fix:` conventional style.

---

### Task 1: Fix — pause then play restarts the workout from 0

**Root cause:** `WorkoutScreen.kt`'s portrait control row (line ~468) branches on `!isRunning && !isComplete` → "Start" → `viewModel.start()`. A *paused* workout also matches that condition, so tapping the button mid-ride calls `start()`, and `WorkoutClock.start()` resets `elapsedSec` to 0. The `else` → Resume branch is unreachable while paused. (The landscape path at line ~193 already guards with `elapsedSec == 0`.)

**Fix at the shared root:** make `WorkoutClock.start()` treat a paused mid-session start as a resume — then every caller is safe — and fix the portrait button label.

**Files:**
- Modify: `android/app/src/main/java/com/trainerloop/domain/WorkoutClock.kt:42-55`
- Modify: `android/app/src/main/java/com/trainerloop/ui/workout/WorkoutScreen.kt:468-499`
- Test: `android/app/src/test/java/com/trainerloop/domain/WorkoutClockTest.kt` (add a test)

**Interfaces:**
- Consumes: existing `WorkoutClock` API.
- Produces: no signature changes; `start()` gains resume-when-paused semantics.

- [ ] **Step 1: Write the failing test**

Add to `WorkoutClockTest.kt` (it already has the `shortWorkout(...)` helper and turbine imports):

```kotlin
  @Test
  fun `start while paused resumes without resetting elapsed`() = runTest {
    val clock = WorkoutClock(shortWorkout(durationSec = 60), StandardTestDispatcher(testScheduler))
    clock.elapsedSec.test {
      assertEquals(0, awaitItem())
      clock.start()
      runCurrent()
      advanceTimeBy(5000)
      assertEquals(1, awaitItem())
      assertEquals(2, awaitItem())
      assertEquals(3, awaitItem())
      assertEquals(4, awaitItem())
      assertEquals(5, awaitItem())
      clock.pause()
      runCurrent()
      clock.start() // the bug: this used to reset elapsed to 0
      runCurrent()
      expectNoEvents() // no reset-to-0 emission
      advanceTimeBy(1000)
      assertEquals(6, awaitItem())
    }
  }

  @Test
  fun `start while paused does not bump session id`() = runTest {
    val clock = WorkoutClock(shortWorkout(durationSec = 60), StandardTestDispatcher(testScheduler))
    clock.sessionId.test {
      assertEquals(0, awaitItem())
      clock.start()
      runCurrent()
      assertEquals(1, awaitItem())
      advanceTimeBy(3000)
      clock.pause()
      runCurrent()
      clock.start()
      runCurrent()
      expectNoEvents()
    }
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.domain.WorkoutClockTest"`
Expected: FAIL — first test sees an unexpected `0` emission after the second `start()`, second test sees sessionId `2`.

- [ ] **Step 3: Fix WorkoutClock.start()**

In `WorkoutClock.kt`, inside `start()`'s `mutex.withLock` block, after the `if (_isRunning.value) return@withLock` line, add:

```kotlin
        // A start while paused mid-session is a resume — never wipe progress.
        // Only stop()/completion reset elapsed, so elapsed > 0 means "paused".
        if (_elapsedSec.value > 0 && !_isComplete.value) {
          _isRunning.value = true
          tickJob = launchTickLoop()
          return@withLock
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.domain.WorkoutClockTest"`
Expected: PASS (all pre-existing clock tests too — `stop()` still resets, so start-after-stop still begins fresh).

- [ ] **Step 5: Fix the portrait button label**

In `WorkoutScreen.kt`, replace the three-branch `when` in the main controls Row (~line 468) with:

```kotlin
        when {
          uiState.isRunning -> {
            Button(
              onClick = { viewModel.pause() },
              modifier = Modifier.weight(1f)
            ) {
              Icon(Icons.Default.Pause, contentDescription = null)
              Spacer(modifier = Modifier.width(4.dp))
              Text("Pause")
            }
          }
          else -> {
            val resumable = uiState.elapsedSec > 0 && !uiState.isComplete
            Button(
              onClick = { if (resumable) viewModel.resume() else viewModel.start() },
              modifier = Modifier.weight(1f)
            ) {
              Icon(Icons.Default.PlayArrow, contentDescription = null)
              Spacer(modifier = Modifier.width(4.dp))
              Text(if (resumable) "Resume" else "Start")
            }
          }
        }
```

- [ ] **Step 6: Full test run + commit**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

```bash
git add android/app/src/main/java/com/trainerloop/domain/WorkoutClock.kt \
  android/app/src/main/java/com/trainerloop/ui/workout/WorkoutScreen.kt \
  android/app/src/test/java/com/trainerloop/domain/WorkoutClockTest.kt
git commit -m "fix(workout): resume instead of restarting when play is tapped after pause"
```

---

### Task 2: Sliders for advanced physics params

Raw decimal text fields (`0.005`, `0.32`) are hard to reason about. Replace them with steppy sliders showing the value plus a plain-language hint.

**Files:**
- Modify: `android/app/src/main/java/com/trainerloop/ui/settings/SettingsScreen.kt` (Virtual Ride group, ~lines 219-255)

**Interfaces:**
- Consumes: existing `SettingsViewModel` string state + `updateBikeWeight/updateCrr/updateCda` (unchanged — sliders write formatted strings; save-time clamps stay the trust boundary).
- Produces: `LabeledSlider` composable (private to `SettingsScreen.kt`) — Task 8 reuses it for trainer difficulty:
  `@Composable fun LabeledSlider(label: String, valueText: String, hint: String?, value: Float, valueRange: ClosedFloatingPointRange<Float>, steps: Int, onValueChange: (Float) -> Unit)`

- [ ] **Step 1: Add the LabeledSlider composable and hint helpers**

At the bottom of `SettingsScreen.kt` add:

```kotlin
@Composable
internal fun LabeledSlider(
  label: String,
  valueText: String,
  hint: String?,
  value: Float,
  valueRange: ClosedFloatingPointRange<Float>,
  steps: Int,
  onValueChange: (Float) -> Unit
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(label, style = MaterialTheme.typography.bodyMedium)
      Text(valueText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
    androidx.compose.material3.Slider(
      value = value,
      onValueChange = onValueChange,
      valueRange = valueRange,
      steps = steps
    )
    if (hint != null) {
      Text(
        hint,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

/** Locale-safe decimal formatting — the ViewModel parses with toDoubleOrNull(). */
private fun fmt(v: Float, decimals: Int): String =
  String.format(java.util.Locale.US, "%.${decimals}f", v)

private fun crrHint(v: Double): String = when {
  v <= 0.0035 -> "Very fast surface (track, new asphalt)"
  v <= 0.0055 -> "Smooth asphalt road"
  v <= 0.0080 -> "Rough or worn road"
  else -> "Gravel / poor surface"
}

private fun cdaHint(v: Double): String = when {
  v <= 0.25 -> "Aggressive aero position (drops / TT)"
  v <= 0.35 -> "Road position on the hoods"
  v <= 0.45 -> "Upright endurance position"
  else -> "Very upright (MTB / city bike)"
}
```

- [ ] **Step 2: Replace the three OutlinedTextFields with sliders**

Inside the `if (advancedExpanded)` block of the Virtual Ride group, replace the three `OutlinedTextField` + `Spacer` blocks (keep the "Reset to defaults" `TextButton`) with:

```kotlin
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
```

(`SettingsViewModel` is untouched: sliders feed the same string state, and stale persisted values outside a slider's range are clamped by `toFloatOrNull() ?: default` display fallback plus the existing save-time `coerceIn`.)

- [ ] **Step 3: Build + test run**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Manual verification**

Install; Settings → Virtual Ride → Advanced: three sliders snap to meaningful steps, value + hint text update while dragging, Save persists, Reset to defaults snaps sliders back.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/trainerloop/ui/settings/SettingsScreen.kt
git commit -m "chg(settings): sliders with hints for advanced physics params"
```

---

### Task 3: Route model + GpxParser

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/data/model/Route.kt`
- Create: `android/app/src/main/java/com/trainerloop/domain/parser/GpxParser.kt`
- Create: `android/app/src/test/resources/sample.gpx`
- Test: `android/app/src/test/java/com/trainerloop/domain/parser/GpxParserTest.kt`

**Interfaces:**
- Consumes: nothing (pure).
- Produces:
  - `@Serializable data class RoutePoint(distanceM: Double, lat: Double, lon: Double, elevationM: Double, gradePercent: Double)`
  - `class Route(val name: String?, val points: List<RoutePoint>)` with `val totalDistanceM: Double`, `val totalAscentM: Int`, `fun gradeAt(distanceM: Double): Double`, `fun pointAt(distanceM: Double): RoutePoint` (linear interpolation). Points sit on a uniform 10 m grid (`Route.GRID_M`).
  - `object GpxParser { fun parse(input: java.io.InputStream): Route }` — throws `GpxParseException(message)` on bad input.

- [ ] **Step 1: Create the test fixture**

`android/app/src/test/resources/sample.gpx` — a steady 4 % climb sampled every ~10 m (0.00009° latitude), with one +8 m GPS elevation spike at point 15. Point spacing matters: the ±37.5 m smoothing window must cover several neighbors, matching real GPX density.

Generate it (30 points, `ele = 500 + i*0.4`, point 15 spiked to `514.0`):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="test">
  <trk>
    <name>Test Climb</name>
    <trkseg>
      <trkpt lat="47.00000" lon="8.00000"><ele>500.0</ele></trkpt>
      <trkpt lat="47.00009" lon="8.00000"><ele>500.4</ele></trkpt>
      <trkpt lat="47.00018" lon="8.00000"><ele>500.8</ele></trkpt>
      <trkpt lat="47.00027" lon="8.00000"><ele>501.2</ele></trkpt>
      <trkpt lat="47.00036" lon="8.00000"><ele>501.6</ele></trkpt>
      <trkpt lat="47.00045" lon="8.00000"><ele>502.0</ele></trkpt>
      <trkpt lat="47.00054" lon="8.00000"><ele>502.4</ele></trkpt>
      <trkpt lat="47.00063" lon="8.00000"><ele>502.8</ele></trkpt>
      <trkpt lat="47.00072" lon="8.00000"><ele>503.2</ele></trkpt>
      <trkpt lat="47.00081" lon="8.00000"><ele>503.6</ele></trkpt>
      <trkpt lat="47.00090" lon="8.00000"><ele>504.0</ele></trkpt>
      <trkpt lat="47.00099" lon="8.00000"><ele>504.4</ele></trkpt>
      <trkpt lat="47.00108" lon="8.00000"><ele>504.8</ele></trkpt>
      <trkpt lat="47.00117" lon="8.00000"><ele>505.2</ele></trkpt>
      <trkpt lat="47.00126" lon="8.00000"><ele>505.6</ele></trkpt>
      <trkpt lat="47.00135" lon="8.00000"><ele>514.0</ele></trkpt>
      <trkpt lat="47.00144" lon="8.00000"><ele>506.4</ele></trkpt>
      <trkpt lat="47.00153" lon="8.00000"><ele>506.8</ele></trkpt>
      <trkpt lat="47.00162" lon="8.00000"><ele>507.2</ele></trkpt>
      <trkpt lat="47.00171" lon="8.00000"><ele>507.6</ele></trkpt>
      <trkpt lat="47.00180" lon="8.00000"><ele>508.0</ele></trkpt>
      <trkpt lat="47.00189" lon="8.00000"><ele>508.4</ele></trkpt>
      <trkpt lat="47.00198" lon="8.00000"><ele>508.8</ele></trkpt>
      <trkpt lat="47.00207" lon="8.00000"><ele>509.2</ele></trkpt>
      <trkpt lat="47.00216" lon="8.00000"><ele>509.6</ele></trkpt>
      <trkpt lat="47.00225" lon="8.00000"><ele>510.0</ele></trkpt>
      <trkpt lat="47.00234" lon="8.00000"><ele>510.4</ele></trkpt>
      <trkpt lat="47.00243" lon="8.00000"><ele>510.8</ele></trkpt>
      <trkpt lat="47.00252" lon="8.00000"><ele>511.2</ele></trkpt>
      <trkpt lat="47.00261" lon="8.00000"><ele>511.6</ele></trkpt>
    </trkseg>
  </trk>
</gpx>
```

(0.00009° latitude ≈ 10 m; 29 hops ≈ 290 m total; true climb 11.6 m.)

- [ ] **Step 2: Write the failing test**

```kotlin
package com.trainerloop.domain.parser

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxParserTest {

  private fun load() = GpxParser.parse(javaClass.getResourceAsStream("/sample.gpx")!!)

  @Test
  fun `parses name distance and grid`() {
    val route = load()
    assertEquals("Test Climb", route.name)
    assertTrue("distance ${route.totalDistanceM}", route.totalDistanceM in 250.0..330.0)
    // Uniform 10 m grid
    route.points.forEachIndexed { i, p ->
      assertEquals(i * 10.0, p.distanceM, 1e-6)
    }
  }

  @Test
  fun `smoothing flattens the single point elevation spike`() {
    val route = load()
    // Raw point 15 (at ~150 m) spikes to 514 m; the ±37.5 m window averages
    // ~7 points, so the smoothed elevation there must sit far below the spike.
    val atSpike = route.pointAt(150.0).elevationM
    assertTrue("smoothed elevation at spike $atSpike", atSpike < 508.0)
    val grades = route.points.map { it.gradePercent }
    assertTrue("max |grade| = ${grades.maxOf { abs(it) }}", grades.all { abs(it) <= 20.0 })
    // True climb is 11.6 m; the spike must add only a small smoothed remnant.
    assertTrue("ascent ${route.totalAscentM}", route.totalAscentM in 8..25)
  }

  @Test
  fun `gradeAt and pointAt clamp and interpolate`() {
    val route = load()
    assertEquals(route.points.first().gradePercent, route.gradeAt(-50.0), 1e-9)
    assertEquals(route.points.last().gradePercent, route.gradeAt(1e9), 1e-9)
    val mid = route.pointAt(15.0) // halfway between grid points 1 and 2
    assertEquals(
      (route.points[1].lat + route.points[2].lat) / 2.0, mid.lat, 1e-9
    )
    assertEquals(15.0, mid.distanceM, 1e-9)
  }

  @Test
  fun `latitude increases monotonically along the track`() {
    val route = load()
    route.points.zipWithNext().forEach { (a, b) -> assertTrue(b.lat >= a.lat) }
  }

  @Test
  fun `rejects gpx with fewer than two usable points`() {
    val gpx = """<?xml version="1.0"?><gpx><trk><trkseg>
      <trkpt lat="47.0" lon="8.0"><ele>500</ele></trkpt>
    </trkseg></trk></gpx>"""
    val ex = assertThrows(GpxParseException::class.java) {
      GpxParser.parse(gpx.byteInputStream())
    }
    assertNotNull(ex.message)
  }

  @Test
  fun `rejects gpx without elevation`() {
    val gpx = """<?xml version="1.0"?><gpx><trk><trkseg>
      <trkpt lat="47.0" lon="8.0"/><trkpt lat="47.001" lon="8.0"/>
    </trkseg></trk></gpx>"""
    assertThrows(GpxParseException::class.java) { GpxParser.parse(gpx.byteInputStream()) }
  }

  @Test
  fun `rejects unparseable xml`() {
    assertThrows(GpxParseException::class.java) {
      GpxParser.parse("not xml at all".byteInputStream())
    }
  }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.domain.parser.GpxParserTest"`
Expected: FAIL — unresolved references `GpxParser` / `GpxParseException`.

- [ ] **Step 4: Implement Route**

`android/app/src/main/java/com/trainerloop/data/model/Route.kt`:

```kotlin
package com.trainerloop.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RoutePoint(
  val distanceM: Double,
  val lat: Double,
  val lon: Double,
  val elevationM: Double,
  val gradePercent: Double
)

/** A GPX route resampled to a uniform [GRID_M] distance grid. */
class Route(val name: String?, val points: List<RoutePoint>) {
  init {
    require(points.size >= 2) { "Route needs at least 2 points" }
  }

  val totalDistanceM: Double = points.last().distanceM

  val totalAscentM: Int = points.zipWithNext()
    .sumOf { (a, b) -> (b.elevationM - a.elevationM).coerceAtLeast(0.0) }
    .toInt()

  fun gradeAt(distanceM: Double): Double =
    points[gridIndex(distanceM)].gradePercent

  /** Linear interpolation between the two surrounding grid points. */
  fun pointAt(distanceM: Double): RoutePoint {
    val d = distanceM.coerceIn(0.0, totalDistanceM)
    val i = gridIndex(d)
    val a = points[i]
    val b = points.getOrElse(i + 1) { a }
    val span = b.distanceM - a.distanceM
    val f = if (span <= 0.0) 0.0 else (d - a.distanceM) / span
    return RoutePoint(
      distanceM = d,
      lat = a.lat + (b.lat - a.lat) * f,
      lon = a.lon + (b.lon - a.lon) * f,
      elevationM = a.elevationM + (b.elevationM - a.elevationM) * f,
      gradePercent = a.gradePercent
    )
  }

  private fun gridIndex(distanceM: Double): Int =
    (distanceM / GRID_M).toInt().coerceIn(0, points.lastIndex)

  companion object {
    const val GRID_M = 10.0
  }
}
```

- [ ] **Step 5: Implement GpxParser**

`android/app/src/main/java/com/trainerloop/domain/parser/GpxParser.kt`:

```kotlin
package com.trainerloop.domain.parser

import com.trainerloop.data.model.Route
import com.trainerloop.data.model.RoutePoint
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.w3c.dom.Element

class GpxParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Parses GPX trackpoints into a [Route]: haversine cumulative distance,
 * distance-windowed elevation smoothing (raw GPX elevation is noisy and
 * would slam the resistance), resampling to a uniform 10 m grid, grade
 * clamped to ±20 % as a bad-data guard.
 */
object GpxParser {
  private const val SMOOTH_HALF_WINDOW_M = 37.5 // ~75 m total window
  private const val MAX_GRADE_PCT = 20.0
  private const val MIN_POINT_SPACING_M = 0.5
  private const val EARTH_RADIUS_M = 6_371_000.0

  private data class Raw(val lat: Double, val lon: Double, val ele: Double, var distM: Double = 0.0)

  fun parse(input: InputStream): Route {
    val doc = try {
      DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input)
    } catch (e: Exception) {
      throw GpxParseException("Not a valid GPX file", e)
    }

    val name = doc.getElementsByTagName("name").item(0)?.textContent?.trim()
      ?.takeIf { it.isNotBlank() }

    val trkpts = doc.getElementsByTagName("trkpt")
    val raw = ArrayList<Raw>(trkpts.length)
    for (i in 0 until trkpts.length) {
      val el = trkpts.item(i) as? Element ?: continue
      val lat = el.getAttribute("lat").toDoubleOrNull() ?: continue
      val lon = el.getAttribute("lon").toDoubleOrNull() ?: continue
      val ele = el.getElementsByTagName("ele").item(0)?.textContent?.trim()?.toDoubleOrNull()
        ?: continue // points without elevation are unusable
      val point = Raw(lat, lon, ele)
      val last = raw.lastOrNull()
      if (last != null) {
        val d = haversineM(last.lat, last.lon, lat, lon)
        if (d < MIN_POINT_SPACING_M) continue // duplicate / GPS jitter
        point.distM = last.distM + d
      }
      raw.add(point)
    }
    if (raw.size < 2) {
      throw GpxParseException("Route needs at least 2 trackpoints with elevation")
    }

    // Distance-windowed moving average over elevation.
    val smoothed = DoubleArray(raw.size) { i ->
      val center = raw[i].distM
      var sum = 0.0
      var count = 0
      for (p in raw) {
        if (p.distM >= center - SMOOTH_HALF_WINDOW_M && p.distM <= center + SMOOTH_HALF_WINDOW_M) {
          sum += p.ele
          count++
        }
      }
      sum / count
    }

    // Resample onto the uniform grid.
    val total = raw.last().distM
    val gridCount = (total / Route.GRID_M).toInt() + 1
    if (gridCount < 2) throw GpxParseException("Route too short")
    val lat = DoubleArray(gridCount)
    val lon = DoubleArray(gridCount)
    val ele = DoubleArray(gridCount)
    var seg = 0
    for (g in 0 until gridCount) {
      val d = (g * Route.GRID_M).coerceAtMost(total)
      while (seg < raw.size - 2 && raw[seg + 1].distM < d) seg++
      val a = raw[seg]
      val b = raw[seg + 1]
      val span = b.distM - a.distM
      val f = if (span <= 0.0) 0.0 else ((d - a.distM) / span).coerceIn(0.0, 1.0)
      lat[g] = a.lat + (b.lat - a.lat) * f
      lon[g] = a.lon + (b.lon - a.lon) * f
      ele[g] = smoothed[seg] + (smoothed[seg + 1] - smoothed[seg]) * f
    }

    val points = List(gridCount) { g ->
      val nextEle = ele[(g + 1).coerceAtMost(gridCount - 1)]
      val thisEle = ele[g]
      val grade = if (g == gridCount - 1 && gridCount >= 2) {
        (ele[g] - ele[g - 1]) / Route.GRID_M * 100.0
      } else {
        (nextEle - thisEle) / Route.GRID_M * 100.0
      }
      RoutePoint(
        distanceM = g * Route.GRID_M,
        lat = lat[g],
        lon = lon[g],
        elevationM = thisEle,
        gradePercent = grade.coerceIn(-MAX_GRADE_PCT, MAX_GRADE_PCT)
      )
    }
    return Route(name, points)
  }

  private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
      cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * EARTH_RADIUS_M * asin(sqrt(a))
  }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.domain.parser.GpxParserTest"`
Expected: PASS (7 tests). If the smoothing assertions fail, check the spike test tolerances against actual values before touching the window size — the fixture spike is extreme by design.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/java/com/trainerloop/data/model/Route.kt \
  android/app/src/main/java/com/trainerloop/domain/parser/GpxParser.kt \
  android/app/src/test/resources/sample.gpx \
  android/app/src/test/java/com/trainerloop/domain/parser/GpxParserTest.kt
git commit -m "feat(gpx): GPX parser with smoothing, uniform grid, and Route lookup model"
```

---

### Task 4: VirtualDrivetrain — gears, cadence smoothing, freewheel

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/domain/sim/VirtualDrivetrain.kt`
- Test: `android/app/src/test/java/com/trainerloop/domain/sim/VirtualDrivetrainTest.kt`

**Interfaces:**
- Consumes: `VirtualSpeed.speedMps` / `PhysicsParams` (Phase 1).
- Produces: `class VirtualDrivetrain(physics: PhysicsParams)` with `val gear: Int` (1..14), `fun shiftUp()`, `fun shiftDown()`, `fun tick(cadenceRpm: Int, gradePercent: Double): Double` (returns speed m/s, one call per second). Companion: `RATIOS: DoubleArray` (14), `WHEEL_CIRCUMFERENCE_M = 2.096`, `START_GEAR = 7`, `GEAR_COUNT = 14`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.trainerloop.domain.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualDrivetrainTest {
  private val params = PhysicsParams(riderKg = 75.0)

  /** Runs enough constant-cadence ticks for the EMA to converge. */
  private fun settled(dt: VirtualDrivetrain, cadence: Int, grade: Double): Double {
    var v = 0.0
    repeat(30) { v = dt.tick(cadence, grade) }
    return v
  }

  @Test
  fun `gear table is strictly increasing from 1 to 4point6`() {
    assertEquals(14, VirtualDrivetrain.RATIOS.size)
    assertEquals(1.0, VirtualDrivetrain.RATIOS.first(), 1e-9)
    assertEquals(4.6, VirtualDrivetrain.RATIOS.last(), 1e-9)
    VirtualDrivetrain.RATIOS.toList().zipWithNext().forEach { (a, b) -> assertTrue(b > a) }
  }

  @Test
  fun `90 rpm in start gear is a plausible speed`() {
    val dt = VirtualDrivetrain(params)
    assertEquals(7, dt.gear)
    val v = settled(dt, 90, 0.0)
    // ratio ~2.0 -> ~6.3 m/s -> ~23 km/h
    assertTrue("expected ~6.3 m/s, got $v", v in 5.0..8.0)
  }

  @Test
  fun `shifting up raises speed at constant cadence`() {
    val dt = VirtualDrivetrain(params)
    val before = settled(dt, 90, 0.0)
    dt.shiftUp()
    val after = settled(dt, 90, 0.0)
    assertTrue(after > before)
  }

  @Test
  fun `shift clamps at 1 and 14`() {
    val dt = VirtualDrivetrain(params)
    repeat(30) { dt.shiftDown() }
    assertEquals(1, dt.gear)
    repeat(30) { dt.shiftUp() }
    assertEquals(14, dt.gear)
  }

  @Test
  fun `cadence changes are smoothed not instant`() {
    val dt = VirtualDrivetrain(params)
    settled(dt, 90, 0.0)
    val v1 = dt.tick(0, 0.0) // cadence drops to zero
    assertTrue("one tick after dropout speed should not be 0, got $v1", v1 > 1.0)
    val vLater = settled(dt, 0, 0.0)
    assertEquals("EMA decays to standstill on the flat", 0.0, vLater, 0.3)
  }

  @Test
  fun `freewheel floor wins on steep descents at low cadence`() {
    val dt = VirtualDrivetrain(params)
    repeat(6) { dt.shiftDown() } // gear 1
    val v = settled(dt, 30, -8.0)
    val coast = VirtualSpeed.speedMps(0, -8.0, params)
    assertEquals("coasting terminal velocity should dominate", coast, v, 1e-6)
    assertTrue(coast > 5.0)
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.domain.sim.VirtualDrivetrainTest"`
Expected: FAIL — unresolved reference `VirtualDrivetrain`.

- [ ] **Step 3: Implement**

```kotlin
package com.trainerloop.domain.sim

import kotlin.math.pow

/**
 * ERG-backed virtual gears for a single-cog trainer: pedaling speed comes
 * from cadence × gear ratio, but the wheel can also freewheel — coasting
 * speed is the zero-power terminal velocity on the current grade, so
 * descents stay fast when you stop pedaling and flats roll to a stop.
 * Cadence is EMA-smoothed (~3 s) to stop cadence→resistance oscillation.
 */
class VirtualDrivetrain(private val physics: PhysicsParams) {

  var gear: Int = START_GEAR
    private set

  private var cadenceEma = 0.0

  fun shiftUp() {
    gear = (gear + 1).coerceAtMost(GEAR_COUNT)
  }

  fun shiftDown() {
    gear = (gear - 1).coerceAtLeast(1)
  }

  /** One 1 Hz tick: smooth the cadence, return the virtual speed in m/s. */
  fun tick(cadenceRpm: Int, gradePercent: Double): Double {
    cadenceEma += (cadenceRpm - cadenceEma) * CADENCE_EMA_ALPHA
    val vGear = cadenceEma / 60.0 * RATIOS[gear - 1] * WHEEL_CIRCUMFERENCE_M
    val vCoast = VirtualSpeed.speedMps(0, gradePercent, physics)
    return maxOf(vGear, vCoast)
  }

  companion object {
    const val GEAR_COUNT = 14
    const val START_GEAR = 7
    const val WHEEL_CIRCUMFERENCE_M = 2.096
    private const val CADENCE_EMA_ALPHA = 0.28 // ~3 s time constant at 1 Hz

    /** Geometric spacing 1.0 → 4.6 (≈ 34×34 to 50×11 on a real bike). */
    val RATIOS: DoubleArray = DoubleArray(GEAR_COUNT) { i ->
      4.6.pow(i.toDouble() / (GEAR_COUNT - 1))
    }
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.domain.sim.VirtualDrivetrainTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/trainerloop/domain/sim/VirtualDrivetrain.kt \
  android/app/src/test/java/com/trainerloop/domain/sim/VirtualDrivetrainTest.kt
git commit -m "feat(sim): virtual drivetrain with 14 gears, cadence EMA, freewheel floor"
```

---

### Task 5: FreeRideTracker + SampleStamper refactor + position fields on TelemetrySample

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/domain/sim/SampleStamper.kt`
- Create: `android/app/src/main/java/com/trainerloop/domain/sim/FreeRideTracker.kt`
- Modify: `android/app/src/main/java/com/trainerloop/data/model/TelemetrySample.kt`
- Modify: `android/app/src/main/java/com/trainerloop/domain/sim/VirtualRideTracker.kt`
- Modify: `android/app/src/main/java/com/trainerloop/domain/TelemetryRecorder.kt`
- Test: `android/app/src/test/java/com/trainerloop/domain/sim/FreeRideTrackerTest.kt`

**Interfaces:**
- Consumes: `Route`/`RoutePoint` (Task 3), `VirtualDrivetrain` (Task 4), `VirtualSpeed.powerAt`/`PhysicsParams` (Phase 1).
- Produces:
  - `TelemetrySample` gains `positionLat: Double? = null`, `positionLon: Double? = null`.
  - `interface SampleStamper { fun stamp(timeSec: Int, powerWatts: Int, cadenceRpm: Int, dropout: Boolean): VirtualStamp }` and `data class VirtualStamp(speedKph: Double, distanceM: Double, altitudeM: Double, gradePercent: Double, lat: Double? = null, lon: Double? = null)`.
  - `TelemetryRecorder` constructors take `stamper: SampleStamper? = null` (replacing `virtualRide: VirtualRideTracker?`; `VirtualRideTracker` implements `SampleStamper`, so existing call sites compile unchanged).
  - `class FreeRideTracker(route: Route, physics: PhysicsParams, difficulty: Double = 1.0) : SampleStamper` with `val drivetrain: VirtualDrivetrain`, `val latest: StateFlow<FreeRidePoint?>`, `@Synchronized fun onTick(timeSec: Int, cadenceRpm: Int): FreeRidePoint`; `data class FreeRidePoint(speedKph: Double, distanceM: Double, altitudeM: Double, gradePercent: Double, lat: Double, lon: Double, targetPowerWatts: Int, routeComplete: Boolean)`. Task 10 reads `latest` for gear/target-power/UI.

- [ ] **Step 1: Add the sample fields (pure data, no test)**

In `TelemetrySample.kt` append to the data class:

```kotlin
  /** GPS position from the GPX route at the simulated distance (free rides only). */
  val positionLat: Double? = null,
  val positionLon: Double? = null
```

- [ ] **Step 2: Create SampleStamper and adapt VirtualRideTracker**

`android/app/src/main/java/com/trainerloop/domain/sim/SampleStamper.kt`:

```kotlin
package com.trainerloop.domain.sim

/** What a simulation stamps onto each 1 Hz telemetry sample. */
data class VirtualStamp(
  val speedKph: Double,
  val distanceM: Double,
  val altitudeM: Double,
  val gradePercent: Double,
  val lat: Double? = null,
  val lon: Double? = null
)

/** 1 Hz hook for [com.trainerloop.domain.TelemetryRecorder]. */
interface SampleStamper {
  fun stamp(timeSec: Int, powerWatts: Int, cadenceRpm: Int, dropout: Boolean): VirtualStamp
}
```

In `VirtualRideTracker.kt`, make the class implement it — change the declaration to `class VirtualRideTracker(...) : SampleStamper` and add:

```kotlin
  override fun stamp(timeSec: Int, powerWatts: Int, cadenceRpm: Int, dropout: Boolean): VirtualStamp =
    onTick(timeSec, powerWatts, dropout).let {
      VirtualStamp(it.speedKph, it.distanceM, it.altitudeM, it.gradePercent)
    }
```

- [ ] **Step 3: Generalize TelemetryRecorder**

In `TelemetryRecorder.kt`, rename the constructor parameter in **both** constructors from `virtualRide: com.trainerloop.domain.sim.VirtualRideTracker? = null` to `stamper: com.trainerloop.domain.sim.SampleStamper? = null`, and replace the stamping block in `startCollecting()` with:

```kotlin
        val virtual = stamper?.stamp(elapsedSec, lastPowerWatts, lastCadenceRpm, dropout)
        val sample = TelemetrySample(
          timeSec = elapsedSec,
          powerWatts = lastPowerWatts,
          cadenceRpm = lastCadenceRpm,
          hrBpm = lastHrBpm,
          dropout = dropout,
          lagCompensated = false,
          virtualSpeedKph = virtual?.speedKph,
          virtualDistanceM = virtual?.distanceM,
          virtualAltitudeM = virtual?.altitudeM,
          gradePercent = virtual?.gradePercent,
          positionLat = virtual?.lat,
          positionLon = virtual?.lon
        )
```

(`WorkoutViewModel` passes its `VirtualRideTracker` positionally — it now satisfies `SampleStamper`, no change needed there.)

- [ ] **Step 4: Write the failing FreeRideTracker test**

```kotlin
package com.trainerloop.domain.sim

import com.trainerloop.data.model.Route
import com.trainerloop.data.model.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeRideTrackerTest {
  private val params = PhysicsParams(riderKg = 75.0)

  /** Straight 1 km route on a uniform grade, 10 m grid heading north. */
  private fun route(gradePct: Double, lengthM: Double = 1000.0): Route {
    val count = (lengthM / Route.GRID_M).toInt() + 1
    return Route("test", List(count) { i ->
      RoutePoint(
        distanceM = i * Route.GRID_M,
        lat = 47.0 + i * 0.0001,
        lon = 8.0,
        elevationM = 500.0 + i * Route.GRID_M * gradePct / 100.0,
        gradePercent = gradePct
      )
    })
  }

  private fun ride(tracker: FreeRideTracker, from: Int, to: Int, cadence: Int): FreeRideTracker.FreeRidePoint {
    var p = tracker.onTick(from, cadence)
    for (t in (from + 1)..to) p = tracker.onTick(t, cadence)
    return p
  }

  @Test
  fun `distance accumulates and position moves along the track`() {
    val tracker = FreeRideTracker(route(0.0), params)
    val p = ride(tracker, 0, 120, cadence = 90)
    assertTrue("distance ${p.distanceM}", p.distanceM > 400.0)
    assertTrue("lat should advance north, got ${p.lat}", p.lat > 47.0)
    assertEquals(8.0, p.lon, 1e-9)
    assertFalse(p.routeComplete)
  }

  @Test
  fun `repeated ticks for the same second do not double integrate`() {
    val tracker = FreeRideTracker(route(0.0), params)
    ride(tracker, 0, 10, cadence = 90)
    val a = tracker.onTick(10, 90)
    val b = tracker.onTick(10, 90)
    assertEquals(a.distanceM, b.distanceM, 1e-9)
  }

  @Test
  fun `climbing needs more target power than flat at same cadence`() {
    val flat = ride(FreeRideTracker(route(0.0), params), 0, 30, cadence = 85)
    val climb = ride(FreeRideTracker(route(6.0), params), 0, 30, cadence = 85)
    assertTrue(
      "climb ${climb.targetPowerWatts}W vs flat ${flat.targetPowerWatts}W",
      climb.targetPowerWatts > flat.targetPowerWatts + 50
    )
  }

  @Test
  fun `difficulty scales target power but not speed or altitude`() {
    val full = ride(FreeRideTracker(route(6.0), params, difficulty = 1.0), 0, 30, cadence = 85)
    val half = ride(FreeRideTracker(route(6.0), params, difficulty = 0.5), 0, 30, cadence = 85)
    assertTrue(half.targetPowerWatts < full.targetPowerWatts)
    assertEquals(full.speedKph, half.speedKph, 1e-6)
    assertEquals(full.altitudeM, half.altitudeM, 1e-6)
    assertEquals(full.gradePercent, half.gradePercent, 1e-9)
  }

  @Test
  fun `no pedaling on a climb floors target at zero and stops advancing`() {
    val tracker = FreeRideTracker(route(5.0), params)
    val p = ride(tracker, 0, 30, cadence = 0)
    assertEquals(0, p.targetPowerWatts)
    assertEquals(0.0, p.distanceM, 1.0)
  }

  @Test
  fun `route completes at the end and holds zero grade`() {
    val tracker = FreeRideTracker(route(0.0, lengthM = 100.0), params)
    val p = ride(tracker, 0, 120, cadence = 95)
    assertTrue(p.routeComplete)
    assertEquals(100.0, p.distanceM, 1e-6)
    assertEquals(0.0, p.gradePercent, 1e-9)
  }

  @Test
  fun `stamp adapts onTick for the recorder`() {
    val tracker = FreeRideTracker(route(2.0), params)
    for (t in 0..20) tracker.stamp(t, powerWatts = 200, cadenceRpm = 90, dropout = false)
    val stamp = tracker.stamp(21, 200, 90, false)
    assertTrue(stamp.distanceM > 0.0)
    assertTrue(stamp.lat != null && stamp.lat!! > 47.0)
    assertEquals(2.0, stamp.gradePercent, 1e-9)
  }
}
```

- [ ] **Step 5: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.domain.sim.FreeRideTrackerTest"`
Expected: FAIL — unresolved reference `FreeRideTracker`.

- [ ] **Step 6: Implement FreeRideTracker**

```kotlin
package com.trainerloop.domain.sim

import com.trainerloop.data.model.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Free-ride engine: cadence + virtual gear give speed, speed on the route's
 * grade gives the ERG target power, distance advances along the real GPX
 * track. Stateful, owned by the ViewModel, ticked once per workout-second
 * (repeat ticks for the same second are no-ops, dt capped at 1 s).
 *
 * [difficulty] (0..1) scales the grade used for target power only — position,
 * speed, and altitude always use the true grade.
 */
class FreeRideTracker(
  private val route: Route,
  private val physics: PhysicsParams,
  private val difficulty: Double = 1.0
) : SampleStamper {

  data class FreeRidePoint(
    val speedKph: Double,
    val distanceM: Double,
    val altitudeM: Double,
    val gradePercent: Double,
    val lat: Double,
    val lon: Double,
    val targetPowerWatts: Int,
    val routeComplete: Boolean
  )

  val drivetrain = VirtualDrivetrain(physics)

  private val _latest = MutableStateFlow<FreeRidePoint?>(null)
  val latest: StateFlow<FreeRidePoint?> = _latest.asStateFlow()

  private var lastTimeSec = 0
  private var distanceM = 0.0

  @Synchronized
  fun onTick(timeSec: Int, cadenceRpm: Int): FreeRidePoint {
    val grade = if (distanceM >= route.totalDistanceM) 0.0 else route.gradeAt(distanceM)
    val v = drivetrain.tick(cadenceRpm, grade)
    val dt = (timeSec - lastTimeSec).coerceIn(0, 1)
    if (dt > 0) distanceM = (distanceM + v * dt).coerceAtMost(route.totalDistanceM)
    if (timeSec > lastTimeSec) lastTimeSec = timeSec

    val pos = route.pointAt(distanceM)
    val complete = distanceM >= route.totalDistanceM
    val effectiveGrade = (if (complete) 0.0 else grade) * difficulty
    val target = VirtualSpeed.powerAt(v, effectiveGrade, physics).toInt().coerceIn(0, 2000)

    return FreeRidePoint(
      speedKph = v * 3.6,
      distanceM = distanceM,
      altitudeM = pos.elevationM,
      gradePercent = if (complete) 0.0 else grade,
      lat = pos.lat,
      lon = pos.lon,
      targetPowerWatts = target,
      routeComplete = complete
    ).also { _latest.value = it }
  }

  /** Recorder hook — power/dropout are ignored; cadence drives the ride. */
  override fun stamp(timeSec: Int, powerWatts: Int, cadenceRpm: Int, dropout: Boolean): VirtualStamp =
    onTick(timeSec, cadenceRpm).let {
      VirtualStamp(it.speedKph, it.distanceM, it.altitudeM, it.gradePercent, it.lat, it.lon)
    }
}
```

- [ ] **Step 7: Run all tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS — new tracker tests, plus all existing recorder/ViewModel/serialization tests (the stamper rename and new nullable fields must not break them).

- [ ] **Step 8: Commit**

```bash
git add android/app/src/main/java/com/trainerloop/domain/sim/SampleStamper.kt \
  android/app/src/main/java/com/trainerloop/domain/sim/FreeRideTracker.kt \
  android/app/src/main/java/com/trainerloop/domain/sim/VirtualRideTracker.kt \
  android/app/src/main/java/com/trainerloop/domain/TelemetryRecorder.kt \
  android/app/src/main/java/com/trainerloop/data/model/TelemetrySample.kt \
  android/app/src/test/java/com/trainerloop/domain/sim/FreeRideTrackerTest.kt
git commit -m "feat(sim): free-ride tracker with virtual gears + generic sample stamper"
```

---

### Task 6: FIT position fields (encoder + decoder)

**Files:**
- Modify: `android/app/src/main/java/com/trainerloop/domain/fit/FitEncoder.kt`
- Modify: `android/app/src/main/java/com/trainerloop/domain/fit/FitDecoder.kt`
- Test: `android/app/src/test/java/com/trainerloop/domain/fit/FitEncoderTest.kt` (add tests)

**Interfaces:**
- Consumes: `TelemetrySample.positionLat/positionLon` (Task 5).
- Produces: record messages carry position_lat (field 0) and position_long (field 1) as sint32 semicircles (`deg × 2³¹ / 180`), written only when present. `FitDecoder` fills them back on decode.

- [ ] **Step 1: Write the failing round-trip test**

Add to `FitEncoderTest.kt`:

```kotlin
  @Test
  fun `gps position survives an encode decode round trip`() {
    val samples = (1..10).map { t ->
      TelemetrySample(
        timeSec = t, powerWatts = 200, cadenceRpm = 90, hrBpm = 140,
        virtualSpeedKph = 25.0, virtualDistanceM = t * 7.0, virtualAltitudeM = 500.0,
        positionLat = 47.05 + t * 0.0001,
        positionLon = -8.5 // negative longitude must survive (signed field)
      )
    }
    val bytes = FitEncoder.encode(1_700_000_000_000L, 10, samples)
    val decoded = FitDecoder.decode(bytes)
    val last = decoded.samples.last()
    assertEquals(47.051, last.positionLat!!, 1e-5)
    assertEquals(-8.5, last.positionLon!!, 1e-5)
  }

  @Test
  fun `samples without position decode with null position`() {
    val samples = (1..5).map { t ->
      TelemetrySample(timeSec = t, powerWatts = 200, cadenceRpm = 90, hrBpm = 140)
    }
    val decoded = FitDecoder.decode(FitEncoder.encode(1_700_000_000_000L, 5, samples))
    assertNull(decoded.samples.last().positionLat)
    assertNull(decoded.samples.last().positionLon)
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.domain.fit.FitEncoderTest"`
Expected: FAIL — decoded position fields are null / compile error on new fields.

- [ ] **Step 3: Extend FitEncoder**

Add constants next to the other base types:

```kotlin
  private const val BASE_TYPE_SINT32 = 0x85
  private const val INVALID_SINT32 = 0x7fffffff
  private const val SEMICIRCLES_PER_DEGREE = (1L shl 31) / 180.0
```

In `encodeValue`, handle the signed type *before* the existing branches (sint32 must not be clamped to ≥ 0):

```kotlin
    if (field.baseType == BASE_TYPE_SINT32) {
      val v = value ?: INVALID_SINT32
      return encodeUint32(v.toLong() and 0xffffffffL) // two's complement LE
    }
```

Append to `recordFields`:

```kotlin
      FitField(0, 4, BASE_TYPE_SINT32),  // position_lat, semicircles
      FitField(1, 4, BASE_TYPE_SINT32)   // position_long, semicircles
```

In the record data loop, add before `dataBytes.addAll(...)`:

```kotlin
      val lat = sample.positionLat?.let { (it * SEMICIRCLES_PER_DEGREE).toInt() }
      val lon = sample.positionLon?.let { (it * SEMICIRCLES_PER_DEGREE).toInt() }
```

and append `lat, lon` to the end of the record's `listOf(...)` values.

- [ ] **Step 4: Extend FitDecoder**

- `RecordRow` gains `val latSemi: Int?, val lonSemi: Int?`.
- In the field-reading `when (f.num)` add `0 -> latSemi = v?.toInt()` and `1 -> lonSemi = v?.toInt()` (with matching `var latSemi: Int? = null; var lonSemi: Int? = null` locals and constructor args).
- In `readValue`, add a signed-32 case to the `when`:

```kotlin
      0x05 -> { // sint32
        val v = if (le) readUint32Le(bytes, pos) else readUint32Be(bytes, pos)
        val signed = v.toInt() // reinterpret as two's complement
        if (signed == 0x7fffffff) null else signed.toLong()
      }
```

- In the final sample mapping add:

```kotlin
        positionLat = r.latSemi?.let { it * 180.0 / (1L shl 31) },
        positionLon = r.lonSemi?.let { it * 180.0 / (1L shl 31) }
```

- [ ] **Step 5: Run all FIT tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.domain.fit.*"`
Expected: PASS — new round-trip tests plus all pre-existing encoder/decoder tests (real-file fixtures must still decode).

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/trainerloop/domain/fit/FitEncoder.kt \
  android/app/src/main/java/com/trainerloop/domain/fit/FitDecoder.kt \
  android/app/src/test/java/com/trainerloop/domain/fit/FitEncoderTest.kt
git commit -m "feat(fit): GPS position record fields as sint32 semicircles"
```

---

### Task 7: Room — routes table, session type/routeId, migration 2→3

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/data/source/local/RouteEntity.kt`
- Create: `android/app/src/main/java/com/trainerloop/data/source/local/RouteDao.kt`
- Create: `android/app/src/main/java/com/trainerloop/data/repository/RouteRepository.kt`
- Modify: `android/app/src/main/java/com/trainerloop/data/source/local/AppDatabase.kt`
- Modify: `android/app/src/main/java/com/trainerloop/data/source/local/SessionEntity.kt`
- Modify: `android/app/src/main/java/com/trainerloop/data/model/Session.kt` (`SessionData` — same new fields)
- Modify: `android/app/src/main/java/com/trainerloop/data/repository/SessionRepository.kt` (mappings)
- Test: `android/app/src/test/java/com/trainerloop/data/repository/RouteRepositoryTest.kt`

**Interfaces:**
- Consumes: `Route`/`RoutePoint` (Task 3).
- Produces:
  - `RouteEntity(id: String, name: String, distanceM: Double, ascentM: Int, pointsJson: String, importedAt: String)` in table `routes`.
  - `RouteDao`: `insert(entity)`, `getAll(): Flow<List<RouteEntity>>` (ordered by `importedAt DESC`), `getById(id): RouteEntity?`, `deleteById(id)`.
  - `class RouteRepository(dao: RouteDao)`: `suspend fun save(route: Route): String` (returns new id), `fun summaries(): Flow<List<RouteSummary>>` with `data class RouteSummary(id: String, name: String, distanceM: Double, ascentM: Int)`, `suspend fun getById(id: String): Route?`, `suspend fun deleteById(id: String)`. Companion `create(database: AppDatabase)`.
  - `SessionEntity`/`SessionData` gain `sessionType: String = "WORKOUT"` and `routeId: String? = null` (`"FREE_RIDE"` for GPX rides).

- [ ] **Step 1: Write the failing repository test**

Follow `SessionRepositoryTest.kt`/`FakeSessionDao.kt` — an in-memory fake DAO, no Robolectric:

```kotlin
package com.trainerloop.data.repository

import com.trainerloop.data.model.Route
import com.trainerloop.data.model.RoutePoint
import com.trainerloop.data.source.local.RouteDao
import com.trainerloop.data.source.local.RouteEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class FakeRouteDao : RouteDao {
  private val rows = MutableStateFlow<List<RouteEntity>>(emptyList())
  override suspend fun insert(entity: RouteEntity) {
    rows.value = rows.value.filter { it.id != entity.id } + entity
  }
  override fun getAll() = rows
  override suspend fun getById(id: String) = rows.value.firstOrNull { it.id == id }
  override suspend fun deleteById(id: String) {
    rows.value = rows.value.filter { it.id != id }
  }
}

class RouteRepositoryTest {

  private fun route() = Route("Alpe", List(11) { i ->
    RoutePoint(i * 10.0, 47.0 + i * 0.0001, 8.0, 500.0 + i, 1.0)
  })

  @Test
  fun `save and load round trips the route`() = runTest {
    val repo = RouteRepository(FakeRouteDao())
    val id = repo.save(route())
    val loaded = repo.getById(id)!!
    assertEquals("Alpe", loaded.name)
    assertEquals(11, loaded.points.size)
    assertEquals(47.0005, loaded.points[5].lat, 1e-9)
    assertEquals(100.0, loaded.totalDistanceM, 1e-9)
  }

  @Test
  fun `summaries carry name distance and ascent`() = runTest {
    val repo = RouteRepository(FakeRouteDao())
    repo.save(route())
    val summary = repo.summaries().first().single()
    assertEquals("Alpe", summary.name)
    assertEquals(100.0, summary.distanceM, 1e-9)
    assertEquals(10, summary.ascentM)
  }

  @Test
  fun `unnamed route falls back to the provided filename`() = runTest {
    val repo = RouteRepository(FakeRouteDao())
    repo.save(Route(null, route().points), fallbackName = "morning_ride")
    assertEquals("morning_ride", repo.summaries().first().single().name)
  }

  @Test
  fun `delete removes the route`() = runTest {
    val repo = RouteRepository(FakeRouteDao())
    val id = repo.save(route())
    repo.deleteById(id)
    assertNull(repo.getById(id))
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.data.repository.RouteRepositoryTest"`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Create entity + DAO**

`RouteEntity.kt`:

```kotlin
package com.trainerloop.data.source.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey val id: String,
    val name: String,
    val distanceM: Double,
    val ascentM: Int,
    /** JSON list of RoutePoint — same blob pattern as SessionEntity.samplesJson. */
    val pointsJson: String,
    val importedAt: String
)
```

`RouteDao.kt`:

```kotlin
package com.trainerloop.data.source.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RouteEntity)

    @Query("SELECT * FROM routes ORDER BY importedAt DESC")
    fun getAll(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE id = :id")
    suspend fun getById(id: String): RouteEntity?

    @Query("DELETE FROM routes WHERE id = :id")
    suspend fun deleteById(id: String)
}
```

- [ ] **Step 4: Migration + database registration**

In `AppDatabase.kt`: bump `version = 3`, add `RouteEntity::class` to `entities`, add `abstract fun routeDao(): RouteDao`, add the migration and register it in `addMigrations(MIGRATION_1_2, MIGRATION_2_3)`:

```kotlin
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS routes (" +
                        "id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                        "distanceM REAL NOT NULL, ascentM INTEGER NOT NULL, " +
                        "pointsJson TEXT NOT NULL, importedAt TEXT NOT NULL)"
                )
                db.execSQL("ALTER TABLE sessions ADD COLUMN sessionType TEXT NOT NULL DEFAULT 'WORKOUT'")
                db.execSQL("ALTER TABLE sessions ADD COLUMN routeId TEXT DEFAULT NULL")
            }
        }
```

- [ ] **Step 5: Session type fields**

Append to `SessionEntity`:

```kotlin
    /** "WORKOUT" or "FREE_RIDE". */
    val sessionType: String = "WORKOUT",
    /** RouteEntity id for free rides; null for workouts. */
    val routeId: String? = null
```

Append the same two fields (same defaults) to `SessionData` in `data/model/Session.kt`, and thread them through `toEntity`/`toData`/ in `SessionRepository.kt` (add `sessionType = s.sessionType, routeId = s.routeId` to both mappings; `toSummary` unchanged).

- [ ] **Step 6: RouteRepository**

```kotlin
package com.trainerloop.data.repository

import com.trainerloop.data.model.Route
import com.trainerloop.data.model.RoutePoint
import com.trainerloop.data.source.local.AppDatabase
import com.trainerloop.data.source.local.RouteDao
import com.trainerloop.data.source.local.RouteEntity
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

data class RouteSummary(
    val id: String,
    val name: String,
    val distanceM: Double,
    val ascentM: Int
)

open class RouteRepository(private val dao: RouteDao) {

    private val json = Json { ignoreUnknownKeys = true }
    private val pointsSerializer = ListSerializer(RoutePoint.serializer())

    open fun summaries(): Flow<List<RouteSummary>> = dao.getAll().map { rows ->
        rows.map { RouteSummary(it.id, it.name, it.distanceM, it.ascentM) }
    }

    /** [fallbackName] is the source filename, used when the GPX has no <name>. */
    suspend fun save(route: Route, fallbackName: String? = null): String {
        val id = UUID.randomUUID().toString()
        dao.insert(
            RouteEntity(
                id = id,
                name = route.name ?: fallbackName ?: "Imported route",
                distanceM = route.totalDistanceM,
                ascentM = route.totalAscentM,
                pointsJson = json.encodeToString(pointsSerializer, route.points),
                importedAt = Instant.now().toString()
            )
        )
        return id
    }

    suspend fun getById(id: String): Route? = dao.getById(id)?.let {
        Route(it.name, json.decodeFromString(pointsSerializer, it.pointsJson))
    }

    suspend fun deleteById(id: String) = dao.deleteById(id)

    companion object {
        fun create(database: AppDatabase): RouteRepository =
            RouteRepository(database.routeDao())
    }
}
```

- [ ] **Step 7: Run all tests + commit**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (including `SessionRepositoryTest` — `FakeSessionDao` may need no change since the new fields default).

```bash
git add android/app/src/main/java/com/trainerloop/data/source/local/RouteEntity.kt \
  android/app/src/main/java/com/trainerloop/data/source/local/RouteDao.kt \
  android/app/src/main/java/com/trainerloop/data/repository/RouteRepository.kt \
  android/app/src/main/java/com/trainerloop/data/source/local/AppDatabase.kt \
  android/app/src/main/java/com/trainerloop/data/source/local/SessionEntity.kt \
  android/app/src/main/java/com/trainerloop/data/model/Session.kt \
  android/app/src/main/java/com/trainerloop/data/repository/SessionRepository.kt \
  android/app/src/test/java/com/trainerloop/data/repository/RouteRepositoryTest.kt
git commit -m "feat(routes): Room routes table + session type discriminator (migration 2->3)"
```

---

### Task 8: Trainer difficulty setting

**Files:**
- Modify: `android/app/src/main/java/com/trainerloop/data/model/UserProfile.kt`
- Modify: `android/app/src/main/java/com/trainerloop/data/repository/ProfileRepository.kt`
- Modify: `android/app/src/main/java/com/trainerloop/ui/settings/SettingsViewModel.kt`
- Modify: `android/app/src/main/java/com/trainerloop/ui/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: `LabeledSlider` (Task 2), existing prefs pattern.
- Produces: `UserProfile.trainerDifficultyPct: Int = 100` (0–100). Task 10 reads it: `difficulty = profile.trainerDifficultyPct / 100.0`.

- [ ] **Step 1: Profile field + persistence**

`UserProfile`: append

```kotlin
  /** Scales the grade used for free-ride target power (0–100 %); 100 = realistic. */
  val trainerDifficultyPct: Int = 100
```

`ProfileRepository`: in `load()` add `trainerDifficultyPct = prefs.getInt(KEY_TRAINER_DIFFICULTY, 100),`; in `save()` add `.putInt(KEY_TRAINER_DIFFICULTY, profile.trainerDifficultyPct)`; companion: `private const val KEY_TRAINER_DIFFICULTY = "trainer_difficulty_pct"`.

- [ ] **Step 2: SettingsViewModel**

`SettingsUiState` gains `val trainerDifficultyPct: Int = 100,`; `init` adds `trainerDifficultyPct = profile.trainerDifficultyPct,`; add:

```kotlin
  fun updateTrainerDifficulty(value: Int) {
    _uiState.value = _uiState.value.copy(trainerDifficultyPct = value, isSaved = false)
  }
```

and in `save()`'s copy: `trainerDifficultyPct = state.trainerDifficultyPct.coerceIn(0, 100),`.

- [ ] **Step 3: Settings UI**

In the Virtual Ride group's `if (advancedExpanded)` block, add after the CdA slider:

```kotlin
        LabeledSlider(
          label = "Trainer difficulty",
          valueText = "${uiState.trainerDifficultyPct} %",
          hint = "How much of a GPX route's gradient you feel on free rides",
          value = uiState.trainerDifficultyPct.toFloat(),
          valueRange = 0f..100f,
          steps = 19, // 5 % increments
          onValueChange = { viewModel.updateTrainerDifficulty(it.toInt()) }
        )
```

- [ ] **Step 4: Build + test + commit**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all pass.

```bash
git add android/app/src/main/java/com/trainerloop/data/model/UserProfile.kt \
  android/app/src/main/java/com/trainerloop/data/repository/ProfileRepository.kt \
  android/app/src/main/java/com/trainerloop/ui/settings/SettingsViewModel.kt \
  android/app/src/main/java/com/trainerloop/ui/settings/SettingsScreen.kt
git commit -m "feat(settings): trainer difficulty slider for free rides"
```

---

### Task 9: Routes UI — import, list, detail + navigation

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/ui/routes/RoutesViewModel.kt`
- Create: `android/app/src/main/java/com/trainerloop/ui/routes/RoutesScreen.kt`
- Create: `android/app/src/main/java/com/trainerloop/ui/routes/RouteDetailScreen.kt`
- Create: `android/app/src/main/java/com/trainerloop/ui/components/RouteProfileChart.kt`
- Modify: `android/app/src/main/java/com/trainerloop/ui/navigation/Screen.kt`
- Modify: `android/app/src/main/java/com/trainerloop/ui/TrainerLoopApp.kt`
- Modify: `android/app/src/main/java/com/trainerloop/ui/home/HomeScreen.kt` (+ its call site in `TrainerLoopApp.kt`)

**Interfaces:**
- Consumes: `GpxParser` (Task 3), `RouteRepository`/`RouteSummary` (Task 7).
- Produces:
  - `Screen.Routes` (`"routes"`), `Screen.RouteDetail` (`"route_detail/{routeId}"` + `createRoute(routeId: String)`), `Screen.FreeRide` (`"free_ride/{routeId}"` + `createRoute(routeId: String)`) — Task 11 registers the FreeRide destination.
  - `@Composable fun RouteProfileChart(points: List<RoutePoint>, positionM: Double?, modifier: Modifier = Modifier)` — Task 11 reuses it with a live position marker.

- [ ] **Step 1: Screen routes**

Add to `Screen.kt`:

```kotlin
  object Routes : Screen("routes")
  object RouteDetail : Screen("route_detail/{routeId}") {
    fun createRoute(routeId: String): String = "route_detail/$routeId"
  }
  object FreeRide : Screen("free_ride/{routeId}") {
    fun createRoute(routeId: String): String = "free_ride/$routeId"
  }
```

- [ ] **Step 2: RoutesViewModel**

```kotlin
package com.trainerloop.ui.routes

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trainerloop.data.repository.RouteRepository
import com.trainerloop.data.repository.RouteSummary
import com.trainerloop.data.source.local.AppDatabase
import com.trainerloop.domain.parser.GpxParseException
import com.trainerloop.domain.parser.GpxParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Single Application ctor — required by the default viewModel() factory
// (reflection can't see Kotlin default args), same as SettingsViewModel.
class RoutesViewModel(application: Application) : AndroidViewModel(application) {

  private val repository = RouteRepository.create(AppDatabase.getInstance(application))

  val routes: StateFlow<List<RouteSummary>> = repository.summaries()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  private val _importError = MutableStateFlow<String?>(null)
  val importError: StateFlow<String?> = _importError.asStateFlow()

  fun importGpx(uri: Uri) {
    viewModelScope.launch {
      try {
        val route = withContext(Dispatchers.IO) {
          getApplication<Application>().contentResolver.openInputStream(uri)?.use {
            GpxParser.parse(it)
          } ?: throw GpxParseException("Could not open the selected file")
        }
        val fileName = withContext(Dispatchers.IO) { queryDisplayName(uri) }
        repository.save(route, fileName?.substringBeforeLast('.'))
        _importError.value = null
      } catch (e: GpxParseException) {
        _importError.value = e.message
      } catch (e: Exception) {
        _importError.value = "Import failed: ${e.message}"
      }
    }
  }

  fun deleteRoute(id: String) {
    viewModelScope.launch { repository.deleteById(id) }
  }

  fun clearError() {
    _importError.value = null
  }

  private fun queryDisplayName(uri: Uri): String? =
    getApplication<Application>().contentResolver
      .query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
      }
}
```

- [ ] **Step 3: RouteProfileChart**

```kotlin
package com.trainerloop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.trainerloop.data.model.RoutePoint

/** Elevation-vs-distance silhouette with an optional rider position marker. */
@Composable
fun RouteProfileChart(
  points: List<RoutePoint>,
  positionM: Double?,
  modifier: Modifier = Modifier
) {
  if (points.size < 2) return
  val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
  val lineColor = MaterialTheme.colorScheme.primary
  val markerColor = MaterialTheme.colorScheme.error

  Canvas(modifier = modifier.fillMaxWidth().height(120.dp)) {
    val total = points.last().distanceM
    val minEle = points.minOf { it.elevationM }
    val eleSpan = (points.maxOf { it.elevationM } - minEle).coerceAtLeast(1.0)
    fun x(d: Double) = (d / total * size.width).toFloat()
    fun y(e: Double) = size.height - ((e - minEle) / eleSpan * size.height * 0.9).toFloat()

    val step = (points.size / 300).coerceAtLeast(1)
    val path = Path().apply {
      moveTo(0f, size.height)
      for (i in points.indices step step) {
        lineTo(x(points[i].distanceM), y(points[i].elevationM))
      }
      lineTo(size.width, y(points.last().elevationM))
      lineTo(size.width, size.height)
      close()
    }
    drawPath(path, color = fillColor)
    drawPath(path, color = lineColor, style = Stroke(width = 2.dp.toPx()))

    positionM?.let { pos ->
      val px = x(pos.coerceIn(0.0, total))
      drawLine(
        color = markerColor,
        start = androidx.compose.ui.geometry.Offset(px, 0f),
        end = androidx.compose.ui.geometry.Offset(px, size.height),
        strokeWidth = 3.dp.toPx()
      )
    }
  }
}
```

- [ ] **Step 4: RoutesScreen**

```kotlin
package com.trainerloop.ui.routes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainerloop.data.repository.RouteSummary

@Composable
fun RoutesScreen(
  onRouteClick: (String) -> Unit,
  onBack: () -> Unit,
  viewModel: RoutesViewModel = viewModel()
) {
  val routes by viewModel.routes.collectAsState()
  val importError by viewModel.importError.collectAsState()

  val picker = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument()
  ) { uri -> uri?.let { viewModel.importGpx(it) } }

  Column(
    modifier = Modifier.fillMaxSize().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
      }
      Text("GPX Routes", style = MaterialTheme.typography.headlineMedium)
    }

    Button(
      onClick = {
        viewModel.clearError()
        // Many file managers don't register the GPX MIME type — accept anything
        // and let the parser reject non-GPX content.
        picker.launch(arrayOf("application/gpx+xml", "application/octet-stream", "*/*"))
      },
      modifier = Modifier.fillMaxWidth()
    ) {
      Icon(Icons.Default.Add, contentDescription = null)
      Text("Import GPX")
    }

    importError?.let {
      Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }

    if (routes.isEmpty()) {
      Text(
        "No routes yet — import a GPX file to ride it.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      items(routes, key = { it.id }) { route ->
        RouteRow(
          route = route,
          onClick = { onRouteClick(route.id) },
          onDelete = { viewModel.deleteRoute(route.id) }
        )
      }
    }
  }
}

@Composable
private fun RouteRow(route: RouteSummary, onClick: () -> Unit, onDelete: () -> Unit) {
  Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(route.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          "%.1f km · %d m ↑".format(route.distanceM / 1000.0, route.ascentM),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      IconButton(onClick = onDelete) {
        Icon(Icons.Default.Delete, contentDescription = "Delete ${route.name}")
      }
    }
  }
}
```

- [ ] **Step 5: RouteDetailScreen**

```kotlin
package com.trainerloop.ui.routes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.trainerloop.data.model.Route
import com.trainerloop.data.repository.RouteRepository
import com.trainerloop.data.source.local.AppDatabase
import com.trainerloop.ui.components.MetricBadge
import com.trainerloop.ui.components.RouteProfileChart

@Composable
fun RouteDetailScreen(
  routeId: String,
  onStartRide: (String) -> Unit,
  onBack: () -> Unit
) {
  val context = LocalContext.current
  var route by remember { mutableStateOf<Route?>(null) }
  LaunchedEffect(routeId) {
    route = RouteRepository.create(AppDatabase.getInstance(context)).getById(routeId)
  }

  val r = route ?: return
  Column(
    modifier = Modifier.fillMaxSize().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
      }
      Text(r.name ?: "Route", style = MaterialTheme.typography.headlineMedium)
    }

    RouteProfileChart(points = r.points, positionM = null)

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      MetricBadge(label = "Distance", value = "%.1f km".format(r.totalDistanceM / 1000.0))
      MetricBadge(label = "Ascent", value = "${r.totalAscentM} m")
    }

    Button(onClick = { onStartRide(routeId) }, modifier = Modifier.fillMaxWidth()) {
      Icon(Icons.Default.PlayArrow, contentDescription = null)
      Text("Start Ride")
    }
  }
}
```

- [ ] **Step 6: Navigation + home entry**

In `TrainerLoopApp.kt` add two destinations inside the `NavHost`:

```kotlin
      composable(Screen.Routes.route) {
        com.trainerloop.ui.routes.RoutesScreen(
          onRouteClick = { id -> navController.navigate(Screen.RouteDetail.createRoute(id)) },
          onBack = { navController.popBackStack() }
        )
      }

      composable(
        route = Screen.RouteDetail.route,
        arguments = listOf(navArgument("routeId") { type = NavType.StringType })
      ) { backStackEntry ->
        val routeId = backStackEntry.arguments?.getString("routeId") ?: return@composable
        com.trainerloop.ui.routes.RouteDetailScreen(
          routeId = routeId,
          onStartRide = { id -> navController.navigate(Screen.FreeRide.createRoute(id)) },
          onBack = { navController.popBackStack() }
        )
      }
```

(The `Screen.FreeRide` destination itself is registered in Task 11; until then Start Ride will crash on an unknown route — acceptable mid-plan, flagged in Task 11.)

In `HomeScreen.kt`: `ActionRows` gains a third row and a callback parameter. Add `onGpxRoutes: () -> Unit` to `HomeScreen`'s parameters, pass it through to `ActionRows(onWorkoutLibrary, onWorkoutBuilder, onGpxRoutes)`, and inside `ActionRows` add after the Workout Builder row:

```kotlin
      HorizontalDivider()
      ActionRow(
        icon = Icons.AutoMirrored.Filled.DirectionsBike,
        label = "GPX Routes",
        onClick = onGpxRoutes
      )
```

(import `androidx.compose.material.icons.automirrored.filled.DirectionsBike`; if that icon isn't in the bundled material-icons set, use `Icons.Default.Terrain` or fall back to `Icons.Default.FitnessCenter` — don't add a dependency). At the `HomeScreen(...)` call site in `TrainerLoopApp.kt` add `onGpxRoutes = { navController.navigate(Screen.Routes.route) },`.

- [ ] **Step 7: Build + test + commit**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

```bash
git add android/app/src/main/java/com/trainerloop/ui/routes \
  android/app/src/main/java/com/trainerloop/ui/components/RouteProfileChart.kt \
  android/app/src/main/java/com/trainerloop/ui/navigation/Screen.kt \
  android/app/src/main/java/com/trainerloop/ui/TrainerLoopApp.kt \
  android/app/src/main/java/com/trainerloop/ui/home/HomeScreen.kt
git commit -m "feat(routes): GPX import, routes list, and route detail screens"
```

---

### Task 10: FreeRideViewModel

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/ui/freeride/FreeRideViewModel.kt`
- Create: `android/app/src/main/java/com/trainerloop/ui/freeride/FreeRideViewModelFactory.kt`
- Test: `android/app/src/test/java/com/trainerloop/ui/freeride/FreeRideViewModelTest.kt`

**Interfaces:**
- Consumes: `FreeRideTracker` (Task 5), `Route` (Task 3), `WorkoutClock`, `TelemetryRecorder(stamper:)`, `FtmsControlManager`, `UserProfile.trainerDifficultyPct` (Task 8), `WorkoutFinishData` (existing, from `WorkoutViewModel.kt`).
- Produces: `FreeRideUiState` and `FreeRideViewModel` with `start()/pause()/resume()/stop()/shiftUp()/shiftDown()/consumeFinishEvent()` — Task 11's screen consumes these.

- [ ] **Step 1: Write the failing test**

Follow `WorkoutViewModelTest.kt`'s mockk setup (`mockFtmsManager`, `UnconfinedTestDispatcher`, `Dispatchers.setMain`):

```kotlin
package com.trainerloop.ui.freeride

import com.trainerloop.ble.FtmsManager
import com.trainerloop.ble.HrManager
import com.trainerloop.ble.model.IndoorBikeData
import com.trainerloop.data.model.Route
import com.trainerloop.data.model.RoutePoint
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FreeRideViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @Before fun setup() { Dispatchers.setMain(testDispatcher) }
  @After fun tearDown() { Dispatchers.resetMain() }

  private fun route(lengthM: Double = 2000.0) = Route("Test", List((lengthM / 10.0).toInt() + 1) { i ->
    RoutePoint(i * 10.0, 47.0 + i * 0.0001, 8.0, 500.0, 0.0)
  })

  private fun bikeData(power: Int, cadence: Double) = IndoorBikeData(
    powerWatts = power, cadenceRpm = cadence, speedKph = null, resistanceLevel = null,
    averagePower = null, averageSpeed = null, totalDistanceMeters = null,
    heartRateBpm = null, elapsedTimeSec = null, remainingTimeSec = null
  )

  private fun mockFtms(data: MutableStateFlow<IndoorBikeData?>): FtmsManager =
    mockk(relaxed = true) { every { this@mockk.data } returns data }

  private fun viewModel(ftmsData: MutableStateFlow<IndoorBikeData?>) = FreeRideViewModel(
    route = route(),
    routeId = "r1",
    ftmsManagerFlow = MutableStateFlow<FtmsManager?>(mockFtms(ftmsData)),
    hrManagerFlow = MutableStateFlow<HrManager?>(null),
    dispatcher = testDispatcher
  )

  @Test
  fun `pedaling advances distance and computes a target`() = runTest(testDispatcher) {
    val ftmsData = MutableStateFlow<IndoorBikeData?>(bikeData(180, 90.0))
    val vm = viewModel(ftmsData)
    vm.start()
    runCurrent()
    advanceTimeBy(30_000)
    runCurrent()
    val state = vm.uiState.value
    assertTrue("distance ${state.distanceM}", state.distanceM > 50.0)
    assertTrue("target ${state.targetPowerWatts}", state.targetPowerWatts > 0)
    assertEquals(7, state.gear)
  }

  @Test
  fun `shifting changes gear and is clamped`() = runTest(testDispatcher) {
    val vm = viewModel(MutableStateFlow(bikeData(180, 90.0)))
    vm.shiftUp()
    assertEquals(8, vm.uiState.value.gear)
    repeat(20) { vm.shiftDown() }
    assertEquals(1, vm.uiState.value.gear)
  }

  @Test
  fun `pause freezes distance`() = runTest(testDispatcher) {
    val ftmsData = MutableStateFlow<IndoorBikeData?>(bikeData(180, 90.0))
    val vm = viewModel(ftmsData)
    vm.start()
    runCurrent()
    advanceTimeBy(10_000)
    runCurrent()
    vm.pause()
    runCurrent()
    val frozen = vm.uiState.value.distanceM
    advanceTimeBy(10_000)
    runCurrent()
    assertEquals(frozen, vm.uiState.value.distanceM, 1e-6)
  }

  @Test
  fun `stop emits finish data with samples`() = runTest(testDispatcher) {
    val ftmsData = MutableStateFlow<IndoorBikeData?>(bikeData(180, 90.0))
    val vm = viewModel(ftmsData)
    vm.start()
    runCurrent()
    advanceTimeBy(5_000)
    runCurrent()
    vm.stop()
    runCurrent()
    val finish = vm.finishEvent.value
    assertNotNull(finish)
    assertTrue(finish!!.samples.isNotEmpty())
    assertEquals("Test", finish.workoutName)
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.ui.freeride.FreeRideViewModelTest"`
Expected: FAIL — unresolved reference `FreeRideViewModel`.

- [ ] **Step 3: Implement FreeRideViewModel**

```kotlin
package com.trainerloop.ui.freeride

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainerloop.ble.FtmsControlManager
import com.trainerloop.ble.FtmsControlStatus
import com.trainerloop.ble.FtmsManager
import com.trainerloop.ble.HrManager
import com.trainerloop.data.model.Route
import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.data.model.UserProfile
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.domain.TelemetryRecorder
import com.trainerloop.domain.WorkoutClock
import com.trainerloop.domain.sim.FreeRideTracker
import com.trainerloop.domain.sim.PhysicsParams
import com.trainerloop.ui.workout.WorkoutFinishData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class FreeRideUiState(
  val isRunning: Boolean = false,
  val elapsedSec: Int = 0,
  val gear: Int = com.trainerloop.domain.sim.VirtualDrivetrain.START_GEAR,
  val speedKph: Double = 0.0,
  val gradePercent: Double = 0.0,
  val distanceM: Double = 0.0,
  val remainingM: Double = 0.0,
  val targetPowerWatts: Int = 0,
  val currentPowerWatts: Int = 0,
  val currentCadenceRpm: Int = 0,
  val currentHrBpm: Int = 0,
  val routeComplete: Boolean = false,
  val samples: List<TelemetrySample> = emptyList()
)

/**
 * Free-ride session: [WorkoutClock] paces 1 Hz ticks (single open-ended
 * segment), [TelemetryRecorder] drives the [FreeRideTracker] via its stamper
 * hook, and this ViewModel turns tracker targets into gated ERG writes.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FreeRideViewModel(
  val route: Route,
  val routeId: String,
  private val ftmsManagerFlow: StateFlow<FtmsManager?> = MutableStateFlow(null),
  private val hrManagerFlow: StateFlow<HrManager?> = MutableStateFlow(null),
  private val ftmsControlManagerFlow: StateFlow<FtmsControlManager?> = MutableStateFlow(null),
  private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
  userProfile: UserProfile = UserProfile()
) : ViewModel() {

  // ponytail: no fixed route duration — a 12 h cap stands in for "open-ended"
  private val clock = WorkoutClock(
    listOf(
      WorkoutSegment.FreeRide(
        id = "free-ride", durationSec = MAX_RIDE_SEC, label = route.name,
        phase = SegmentPhase.WORK
      )
    ),
    dispatcher
  )

  private val tracker = FreeRideTracker(
    route = route,
    physics = PhysicsParams(
      riderKg = userProfile.weightKg,
      bikeKg = userProfile.bikeWeightKg,
      crr = userProfile.rollingResistanceCrr,
      cda = userProfile.dragAreaCda
    ),
    difficulty = userProfile.trainerDifficultyPct / 100.0
  )

  private val _uiState = MutableStateFlow(FreeRideUiState(remainingM = route.totalDistanceM))
  val uiState: StateFlow<FreeRideUiState> = _uiState.asStateFlow()

  private val _finishEvent = MutableStateFlow<WorkoutFinishData?>(null)
  val finishEvent: StateFlow<WorkoutFinishData?> = _finishEvent.asStateFlow()

  private val recorder = MutableStateFlow<TelemetryRecorder?>(null)

  private var lastSentWatts = -1
  private var lastSentAtSec = -10

  init {
    viewModelScope.launch {
      combine(ftmsManagerFlow, hrManagerFlow) { ftms, hr -> ftms to hr }
        .distinctUntilChanged()
        .collect { (ftms, hr) ->
          val previous = recorder.value
          recorder.value = if (ftms != null) {
            TelemetryRecorder(clock, ftms, hr, dispatcher, tracker)
              .also { it.startCollecting() }
          } else null
          previous?.stop()
        }
    }

    viewModelScope.launch {
      recorder
        .flatMapLatest { r -> r?.latest ?: flowOf(null) }
        .filterNotNull()
        .collect { sample ->
          val point = tracker.latest.value
          _uiState.value = _uiState.value.copy(
            currentPowerWatts = sample.powerWatts,
            currentCadenceRpm = sample.cadenceRpm,
            currentHrBpm = sample.hrBpm,
            gear = tracker.drivetrain.gear,
            speedKph = point?.speedKph ?: 0.0,
            gradePercent = point?.gradePercent ?: 0.0,
            distanceM = point?.distanceM ?: 0.0,
            remainingM = ((point?.let { route.totalDistanceM - it.distanceM })
              ?: route.totalDistanceM).coerceAtLeast(0.0),
            targetPowerWatts = point?.targetPowerWatts ?: 0,
            routeComplete = point?.routeComplete ?: false
          )
          if (_uiState.value.isRunning && point != null) {
            maybeSendTarget(point.targetPowerWatts, sample.timeSec)
          }
        }
    }

    viewModelScope.launch {
      recorder
        .flatMapLatest { r -> r?.samples ?: flowOf(emptyList()) }
        .collect { samples -> _uiState.value = _uiState.value.copy(samples = samples) }
    }

    viewModelScope.launch {
      clock.elapsedSec.collect { _uiState.value = _uiState.value.copy(elapsedSec = it) }
    }
    viewModelScope.launch {
      clock.isRunning.collect { _uiState.value = _uiState.value.copy(isRunning = it) }
    }
  }

  fun start() {
    clock.start()
    sendControlWhenReady { it.startResume() }
  }

  fun pause() {
    clock.pause()
    viewModelScope.launch { ftmsControlManagerFlow.value?.stopPause(stop = false) }
  }

  fun resume() {
    clock.resume()
    sendControlWhenReady { it.startResume() }
  }

  fun stop() {
    clock.stop()
    viewModelScope.launch { ftmsControlManagerFlow.value?.stopPause(stop = true) }
    val samples = _uiState.value.samples
    if (samples.isNotEmpty()) {
      _finishEvent.value = WorkoutFinishData(
        workoutId = "gpx-free-ride",
        workoutName = route.name ?: "GPX Ride",
        startTimeMs = System.currentTimeMillis() - _uiState.value.elapsedSec * 1000L,
        samples = samples
      )
    }
  }

  fun shiftUp() {
    tracker.drivetrain.shiftUp()
    _uiState.value = _uiState.value.copy(gear = tracker.drivetrain.gear)
  }

  fun shiftDown() {
    tracker.drivetrain.shiftDown()
    _uiState.value = _uiState.value.copy(gear = tracker.drivetrain.gear)
  }

  fun consumeFinishEvent() {
    _finishEvent.value = null
  }

  /** Re-send only on ≥ 2 W change or 2 s elapsed — no control-point spam. */
  private fun maybeSendTarget(watts: Int, timeSec: Int) {
    if (kotlin.math.abs(watts - lastSentWatts) < TARGET_MIN_DELTA_W &&
      timeSec - lastSentAtSec < TARGET_RESEND_SEC
    ) return
    lastSentWatts = watts
    lastSentAtSec = timeSec
    viewModelScope.launch { ftmsControlManagerFlow.value?.setTargetPower(watts) }
  }

  private fun sendControlWhenReady(action: suspend (FtmsControlManager) -> Unit) {
    val control = ftmsControlManagerFlow.value ?: return
    viewModelScope.launch {
      if (control.status.value == FtmsControlStatus.READY) {
        action(control)
        return@launch
      }
      val ready = withTimeoutOrNull(CONTROL_READY_TIMEOUT_MS) {
        control.status.filter { it == FtmsControlStatus.READY }.first()
      }
      if (ready != null) action(control)
    }
  }

  override fun onCleared() {
    clock.stop()
    clock.close()
    recorder.value?.stop()
    super.onCleared()
  }

  companion object {
    private const val MAX_RIDE_SEC = 12 * 3600
    private const val TARGET_MIN_DELTA_W = 2
    private const val TARGET_RESEND_SEC = 2
    private const val CONTROL_READY_TIMEOUT_MS = 5_000L
  }
}
```

- [ ] **Step 4: Factory**

```kotlin
package com.trainerloop.ui.freeride

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.trainerloop.ble.FtmsControlManager
import com.trainerloop.ble.FtmsManager
import com.trainerloop.ble.HrManager
import com.trainerloop.data.model.Route
import com.trainerloop.data.model.UserProfile
import kotlinx.coroutines.flow.StateFlow

class FreeRideViewModelFactory(
  private val route: Route,
  private val routeId: String,
  private val ftmsManagerFlow: StateFlow<FtmsManager?>,
  private val hrManagerFlow: StateFlow<HrManager?>,
  private val ftmsControlManagerFlow: StateFlow<FtmsControlManager?>,
  private val userProfile: UserProfile
) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T =
    FreeRideViewModel(
      route = route,
      routeId = routeId,
      ftmsManagerFlow = ftmsManagerFlow,
      hrManagerFlow = hrManagerFlow,
      ftmsControlManagerFlow = ftmsControlManagerFlow,
      userProfile = userProfile
    ) as T
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.ui.freeride.FreeRideViewModelTest"`
Expected: PASS (4 tests). Then the full suite: `./gradlew :app:testDebugUnitTest` — PASS.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/trainerloop/ui/freeride/FreeRideViewModel.kt \
  android/app/src/main/java/com/trainerloop/ui/freeride/FreeRideViewModelFactory.kt \
  android/app/src/test/java/com/trainerloop/ui/freeride/FreeRideViewModelTest.kt
git commit -m "feat(freeride): free-ride view model with gated ERG targets and shifting"
```

---

### Task 11: FreeRideScreen + volume-key shifting + finish flow

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/ui/freeride/FreeRideScreen.kt`
- Modify: `android/app/src/main/java/com/trainerloop/app/MainActivity.kt`
- Modify: `android/app/src/main/java/com/trainerloop/app/TrainerLoopApplication.kt`
- Modify: `android/app/src/main/java/com/trainerloop/ui/TrainerLoopApp.kt`
- Modify: `android/app/src/main/java/com/trainerloop/ui/complete/WorkoutCompleteViewModel.kt` + `WorkoutCompleteViewModelFactory.kt`

**Interfaces:**
- Consumes: `FreeRideViewModel` (Task 10), `RouteProfileChart` (Task 9), `Screen.FreeRide` (Task 9), `RouteRepository` (Task 7).
- Produces: complete free-ride flow: detail → ride → `WorkoutComplete` (session saved with `sessionType = "FREE_RIDE"`, `routeId`).

- [ ] **Step 1: Volume-key hook**

`TrainerLoopApplication.kt` — add next to `selectedWorkout`:

```kotlin
  /** Set by FreeRideScreen while active: volume keys shift gears (true = up). */
  var volumeShiftHandler: ((Boolean) -> Unit)? = null
  var pendingSessionType: String? = null
  var pendingRouteId: String? = null
```

`MainActivity.kt` — add:

```kotlin
  override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
    val handler = trainerLoopApp.volumeShiftHandler
    if (handler != null &&
      (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP ||
        keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN)
    ) {
      handler(keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP)
      return true
    }
    return super.onKeyDown(keyCode, event)
  }
```

(import `com.trainerloop.app.trainerLoopApp` if not already in scope — it's in the same package.)

- [ ] **Step 2: FreeRideScreen**

```kotlin
package com.trainerloop.ui.freeride

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trainerloop.app.trainerLoopApp
import com.trainerloop.ui.components.RouteProfileChart
import com.trainerloop.ui.workout.WorkoutFinishData

@Composable
fun FreeRideScreen(
  viewModel: FreeRideViewModel,
  onSessionFinished: (WorkoutFinishData) -> Unit,
  onExit: () -> Unit
) {
  val uiState by viewModel.uiState.collectAsState()
  val finishEvent by viewModel.finishEvent.collectAsState()
  val context = LocalContext.current
  val view = LocalView.current
  var showStopConfirm by remember { mutableStateOf(false) }

  LaunchedEffect(finishEvent) {
    finishEvent?.let {
      viewModel.consumeFinishEvent()
      onSessionFinished(it)
    }
  }

  DisposableEffect(Unit) {
    context.trainerLoopApp.volumeShiftHandler = { up ->
      if (up) viewModel.shiftUp() else viewModel.shiftDown()
    }
    onDispose { context.trainerLoopApp.volumeShiftHandler = null }
  }

  DisposableEffect(uiState.isRunning) {
    view.keepScreenOn = uiState.isRunning
    onDispose { view.keepScreenOn = false }
  }

  if (showStopConfirm) {
    AlertDialog(
      onDismissRequest = { showStopConfirm = false },
      title = { Text("End ride?") },
      text = { Text("The ride so far will be saved.") },
      confirmButton = {
        TextButton(onClick = { showStopConfirm = false; viewModel.stop() }) { Text("End ride") }
      },
      dismissButton = {
        TextButton(onClick = { showStopConfirm = false }) { Text("Keep riding") }
      }
    )
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text(
      viewModel.route.name ?: "GPX Ride",
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.SemiBold
    )

    if (uiState.routeComplete) {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
      ) {
        Text(
          "Route complete — keep riding or stop to save.",
          modifier = Modifier.padding(12.dp),
          color = MaterialTheme.colorScheme.onPrimaryContainer
        )
      }
    }

    RouteProfileChart(points = viewModel.route.points, positionM = uiState.distanceM)

    // Gear + shift controls — large tap targets near the screen edges.
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      FilledTonalButton(
        onClick = { viewModel.shiftDown() },
        modifier = Modifier.size(width = 96.dp, height = 72.dp)
      ) {
        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Shift down")
      }
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("GEAR", style = MaterialTheme.typography.labelSmall)
        Text(
          "${uiState.gear}",
          style = MaterialTheme.typography.displayMedium,
          fontWeight = FontWeight.Bold
        )
      }
      FilledTonalButton(
        onClick = { viewModel.shiftUp() },
        modifier = Modifier.size(width = 96.dp, height = 72.dp)
      ) {
        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Shift up")
      }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      RideMetric("Speed", "%.1f".format(uiState.speedKph), "km/h", Modifier.weight(1f))
      RideMetric("Grade", "%.1f".format(uiState.gradePercent), "%", Modifier.weight(1f))
      RideMetric("To go", "%.1f".format(uiState.remainingM / 1000.0), "km", Modifier.weight(1f))
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      RideMetric("Power", "${uiState.currentPowerWatts}", "W", Modifier.weight(1f))
      RideMetric("Target", "${uiState.targetPowerWatts}", "W", Modifier.weight(1f))
      RideMetric(
        "HR",
        if (uiState.currentHrBpm > 0) "${uiState.currentHrBpm}" else "--",
        "bpm",
        Modifier.weight(1f)
      )
    }
    RideMetric("Time", formatTime(uiState.elapsedSec), "", Modifier.fillMaxWidth())

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      val resumable = uiState.elapsedSec > 0
      if (uiState.isRunning) {
        Button(onClick = { viewModel.pause() }, modifier = Modifier.weight(1f)) {
          Icon(Icons.Default.Pause, contentDescription = null)
          Spacer(modifier = Modifier.width(4.dp))
          Text("Pause")
        }
      } else {
        Button(
          onClick = { if (resumable) viewModel.resume() else viewModel.start() },
          modifier = Modifier.weight(1f)
        ) {
          Icon(Icons.Default.PlayArrow, contentDescription = null)
          Spacer(modifier = Modifier.width(4.dp))
          Text(if (resumable) "Resume" else "Start")
        }
      }
      Button(
        onClick = { if (uiState.elapsedSec == 0) onExit() else showStopConfirm = true },
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
      ) {
        Icon(Icons.Default.Stop, contentDescription = null)
        Spacer(modifier = Modifier.width(4.dp))
        Text("Stop")
      }
    }
  }
}

@Composable
private fun RideMetric(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
  Card(modifier = modifier) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(label, style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
      Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (unit.isNotEmpty()) {
          Spacer(modifier = Modifier.width(2.dp))
          Text(unit, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }
}

private fun formatTime(seconds: Int): String {
  val h = seconds / 3600
  val m = (seconds % 3600) / 60
  val s = seconds % 60
  return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
```

- [ ] **Step 3: Register the navigation destination**

In `TrainerLoopApp.kt` add:

```kotlin
      composable(
        route = Screen.FreeRide.route,
        arguments = listOf(navArgument("routeId") { type = NavType.StringType })
      ) { backStackEntry ->
        val context = LocalContext.current
        val app = context.trainerLoopApp
        val routeId = backStackEntry.arguments?.getString("routeId") ?: return@composable
        var route by androidx.compose.runtime.remember {
          androidx.compose.runtime.mutableStateOf<com.trainerloop.data.model.Route?>(null)
        }
        LaunchedEffect(routeId) {
          route = com.trainerloop.data.repository.RouteRepository
            .create(com.trainerloop.data.source.local.AppDatabase.getInstance(context))
            .getById(routeId)
        }
        val loaded = route ?: return@composable
        com.trainerloop.ui.freeride.FreeRideScreen(
          viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
            factory = com.trainerloop.ui.freeride.FreeRideViewModelFactory(
              route = loaded,
              routeId = routeId,
              ftmsManagerFlow = app.ftmsManager,
              hrManagerFlow = app.hrManager,
              ftmsControlManagerFlow = app.ftmsControlManager,
              userProfile = com.trainerloop.data.repository.ProfileRepository(context).getProfileSync()
            )
          ),
          onSessionFinished = { data ->
            app.pendingSessionSamples = data.samples
            app.pendingCoachJson = data.coachJson
            app.pendingSessionType = "FREE_RIDE"
            app.pendingRouteId = routeId
            navController.navigate(
              Screen.WorkoutComplete.createRoute(
                sessionId = data.startTimeMs.toString(),
                workoutId = data.workoutId,
                workoutName = data.workoutName,
                startTimeMs = data.startTimeMs
              )
            )
          },
          onExit = { navController.popBackStack() }
        )
      }
```

- [ ] **Step 4: Thread session type into the complete flow**

In `TrainerLoopApp.kt`'s existing `Screen.WorkoutComplete` composable, next to the `pendingSessionSamples` reads add:

```kotlin
        val sessionType = app.pendingSessionType ?: "WORKOUT"
        app.pendingSessionType = null
        val routeId = app.pendingRouteId
        app.pendingRouteId = null
```

and pass `sessionType = sessionType, routeId = routeId` to `WorkoutCompleteViewModelFactory`.

`WorkoutCompleteViewModelFactory.kt`: add `private val sessionType: String = "WORKOUT"` and `private val routeId: String? = null` constructor params, forward them to the ViewModel.

`WorkoutCompleteViewModel.kt`: add matching constructor params (defaults `"WORKOUT"` / `null`) and include them in the `SessionData(...)` built in `saveSession()`:

```kotlin
      sessionType = sessionType,
      routeId = routeId
```

- [ ] **Step 5: Build + full test run**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/trainerloop/ui/freeride/FreeRideScreen.kt \
  android/app/src/main/java/com/trainerloop/app/MainActivity.kt \
  android/app/src/main/java/com/trainerloop/app/TrainerLoopApplication.kt \
  android/app/src/main/java/com/trainerloop/ui/TrainerLoopApp.kt \
  android/app/src/main/java/com/trainerloop/ui/complete/WorkoutCompleteViewModel.kt \
  android/app/src/main/java/com/trainerloop/ui/complete/WorkoutCompleteViewModelFactory.kt
git commit -m "feat(freeride): ride screen with shifting, volume keys, and complete flow"
```

---

### Task 12: Manual end-to-end verification (device + trainer)

- [ ] **Step 1: Import + browse**

Install the debug build. Home → GPX Routes → Import GPX with a real file (export one from Strava/Komoot). Verify: route appears with name/distance/ascent; detail shows the elevation profile; a deliberately broken file (e.g. a .fit renamed .gpx) shows a friendly error and saves nothing. Verify the app upgrade path: install over the previous build (migration 2→3 must not wipe sessions).

- [ ] **Step 2: Ride it**

Start the ride with the trainer connected:
- resistance rises on climbs at constant cadence; shifting up makes pedaling harder (higher target power);
- volume keys shift while the ride screen is open, and stop doing so after leaving it;
- stop pedaling on a descent → speed holds (freewheel); on a flat → rolls to a stop, target floors at 0 W;
- pause freezes distance; resume continues from the same spot (Task 1 regression check);
- position marker advances along the profile; route end shows the complete banner and keeps recording.

- [ ] **Step 3: Save + upload**

Stop → complete screen shows distance/elevation; session saves; intervals.icu upload shows the ride **on a map** with GPS trace, speed, and altitude. Check the History detail screen still renders the session.

- [ ] **Step 4: Phase 1 regression**

Run a short ERG interval workout: terrain overlay, live tiles, pause→play resumes (not restarts), settings sliders persist. Run `./gradlew :app:testDebugUnitTest` one final time.

- [ ] **Step 5: Update the design doc status**

In `docs/plans/2026-07-06-gpx-sim-free-ride-design.md` change the Status line to `**Status:** Implemented — see `2026-07-07-gpx-free-ride-plan.md`.` and commit:

```bash
git add docs/plans/2026-07-06-gpx-sim-free-ride-design.md
git commit -m "docs: mark GPX free-ride design as implemented"
```
