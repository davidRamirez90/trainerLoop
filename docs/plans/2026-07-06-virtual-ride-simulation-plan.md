# Virtual Ride Simulation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** ERG interval workouts gain a simulated route: terrain generated from the workout structure, physics-based virtual speed/distance/ascent computed from real power, an elevation overlay on the workout chart, and speed/distance/altitude recorded into the FIT upload.

**Architecture:** Three pure domain units (`VirtualSpeed` solver, `RouteGenerator`, `VirtualRideTracker`) feed new nullable fields on `TelemetrySample`. `WorkoutViewModel` owns the tracker and passes it to `TelemetryRecorder`, which stamps each 1 Hz sample. `FitEncoder`/`FitDecoder` gain the standard speed/distance/altitude record fields. Physics constants (bike weight, Crr, CdA) live in `UserProfile` behind an Advanced settings section. Spec: `docs/plans/2026-07-06-virtual-ride-simulation-design.md`.

**Tech Stack:** Kotlin, Jetpack Compose, kotlinx-serialization, JUnit 4. No new dependencies.

## Global Constraints

- ERG control is never touched — the simulation only *reads* power; `FtmsControlManager` is out of scope.
- All new `TelemetrySample` fields are nullable with `null` defaults (old JSON sessions must keep deserializing).
- Ramp tests get no virtual ride (they are tests, not rides).
- Physics defaults: bike 8.0 kg, Crr 0.005, CdA 0.32, ρ 1.226 kg/m³, g 9.81. Clamp ranges: bike 5.0–15.0 kg, Crr 0.002–0.010, CdA 0.15–0.60.
- Grade clamped to ±20 % in the solver; power clamped to 0–2000 W.
- Tests run from the `android/` directory: `./gradlew :app:testDebugUnitTest --tests "<class>"`.
- Test style: JUnit 4, backtick function names, `org.junit.Assert.*` (see `WorkoutMathTest.kt`).
- Commit after every task with the repo's `feat:`/`chg:` conventional style.

---

### Task 1: VirtualSpeed physics solver

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/domain/sim/VirtualSpeed.kt`
- Test: `android/app/src/test/java/com/trainerloop/domain/sim/VirtualSpeedTest.kt`

**Interfaces:**
- Consumes: nothing (pure math).
- Produces: `data class PhysicsParams(riderKg: Double, bikeKg: Double = 8.0, crr: Double = 0.005, cda: Double = 0.32)` and `object VirtualSpeed { fun speedMps(powerWatts: Int, gradePercent: Double, p: PhysicsParams): Double; fun powerAt(v: Double, gradePercent: Double, p: PhysicsParams): Double }`. Later tasks call `speedMps`.

- [x] **Step 1: Write the failing test**

```kotlin
package com.trainerloop.domain.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualSpeedTest {
  private val params = PhysicsParams(riderKg = 75.0)

  @Test
  fun `250W on the flat is roughly 36 kmh`() {
    val v = VirtualSpeed.speedMps(250, 0.0, params)
    assertTrue("expected ~10.2 m/s, got $v", v in 9.5..10.8)
  }

  @Test
  fun `more power is faster`() {
    assertTrue(
      VirtualSpeed.speedMps(300, 0.0, params) > VirtualSpeed.speedMps(200, 0.0, params)
    )
  }

  @Test
  fun `climbing is slower than flat`() {
    assertTrue(
      VirtualSpeed.speedMps(250, 8.0, params) < VirtualSpeed.speedMps(250, 0.0, params)
    )
  }

  @Test
  fun `climbing 8pct at 250W is roughly 12 kmh`() {
    val v = VirtualSpeed.speedMps(250, 8.0, params)
    assertTrue("expected ~3.4 m/s, got $v", v in 2.8..4.0)
  }

  @Test
  fun `zero power on flat or climb means standstill`() {
    assertEquals(0.0, VirtualSpeed.speedMps(0, 0.0, params), 1e-9)
    assertEquals(0.0, VirtualSpeed.speedMps(0, 3.0, params), 1e-9)
  }

  @Test
  fun `coasting a descent reaches terminal velocity`() {
    val v = VirtualSpeed.speedMps(0, -5.0, params)
    assertTrue("expected >3 m/s coasting -5%, got $v", v > 3.0)
  }

  @Test
  fun `grade is clamped to plus minus 20`() {
    assertEquals(
      VirtualSpeed.speedMps(0, -20.0, params),
      VirtualSpeed.speedMps(0, -35.0, params),
      1e-6
    )
  }

  @Test
  fun `solver round trips through powerAt`() {
    val v = VirtualSpeed.speedMps(250, 2.0, params)
    assertEquals(250.0, VirtualSpeed.powerAt(v, 2.0, params), 0.5)
  }
}
```

- [x] **Step 2: Run test to verify it fails**

Run (from `android/`): `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.domain.sim.VirtualSpeedTest"`
Expected: FAIL — unresolved reference `PhysicsParams` / `VirtualSpeed` (compile error counts as the failing state).

- [x] **Step 3: Write the implementation**

```kotlin
package com.trainerloop.domain.sim

import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin

data class PhysicsParams(
  val riderKg: Double,
  val bikeKg: Double = 8.0,
  val crr: Double = 0.005,
  val cda: Double = 0.32
)

/**
 * Steady-state cycling physics: P = Crr·m·g·cosθ·v + m·g·sinθ·v + ½·ρ·CdA·v³.
 * Solved for v by bisection — the expression has exactly one sign change on
 * (0, MAX_SPEED] for any grade in range.
 */
object VirtualSpeed {
  private const val G = 9.81
  private const val RHO = 1.226
  private const val MAX_SPEED_MPS = 40.0

  fun powerAt(v: Double, gradePercent: Double, p: PhysicsParams): Double {
    val m = p.riderKg + p.bikeKg
    val theta = atan(gradePercent / 100.0)
    return (p.crr * m * G * cos(theta) + m * G * sin(theta)) * v +
      0.5 * RHO * p.cda * v * v * v
  }

  fun speedMps(powerWatts: Int, gradePercent: Double, p: PhysicsParams): Double {
    val power = powerWatts.coerceIn(0, 2000).toDouble()
    val grade = gradePercent.coerceIn(-20.0, 20.0)
    // Rider can't overcome resistance at all -> standstill. Covers P=0 on
    // flats/climbs and descents too shallow to overcome rolling resistance.
    if (powerAt(1e-3, grade, p) >= power) return 0.0
    var lo = 1e-3
    var hi = MAX_SPEED_MPS
    repeat(50) {
      val mid = (lo + hi) / 2
      if (powerAt(mid, grade, p) < power) lo = mid else hi = mid
    }
    return (lo + hi) / 2
  }
}
```

- [x] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.domain.sim.VirtualSpeedTest"`
Expected: PASS (8 tests)

- [x] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/trainerloop/domain/sim/VirtualSpeed.kt \
  android/app/src/test/java/com/trainerloop/domain/sim/VirtualSpeedTest.kt
git commit -m "feat(sim): physics solver for virtual speed from power and grade"
```

---

### Task 2: RouteGenerator — terrain from workout structure

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/domain/sim/RouteGenerator.kt`
- Test: `android/app/src/test/java/com/trainerloop/domain/sim/RouteGeneratorTest.kt`

**Interfaces:**
- Consumes: `Workout`, `WorkoutMath.totalDurationSec(segments)`, `WorkoutMath.targetRangeAt(segments, sec)` (both exist in `com.trainerloop.domain.WorkoutMath`), `VirtualSpeed.speedMps` + `PhysicsParams` from Task 1.
- Produces: `RouteGenerator.generate(workout: Workout, ftp: Int, params: PhysicsParams): RouteProfile` where `class RouteProfile(val gradePercent: DoubleArray, val expectedAltitudeM: DoubleArray)` (one entry per second) with `fun gradeAt(sec: Int): Double`.

- [x] **Step 1: Write the failing test**

```kotlin
package com.trainerloop.domain.sim

import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.Workout
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.data.model.WorkoutSource
import kotlin.math.abs
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteGeneratorTest {
  private val params = PhysicsParams(riderKg = 75.0)

  private fun step(id: String, sec: Int, watts: Int, phase: SegmentPhase) =
    WorkoutSegment.Step(
      id = id, durationSec = sec, label = null, phase = phase,
      isWork = phase == SegmentPhase.WORK, targetRange = TargetRange(watts, watts)
    )

  private fun workout(id: String = "w1") = Workout(
    id = id, name = "Test", description = null, source = WorkoutSource.MANUAL,
    segments = listOf(
      step("warm", 300, 150, SegmentPhase.WARMUP),
      step("vo2", 300, 300, SegmentPhase.WORK),
      step("rec", 300, 100, SegmentPhase.RECOVERY),
      step("cool", 300, 130, SegmentPhase.COOLDOWN)
    )
  )

  @Test
  fun `same workout id generates the same route`() {
    val a = RouteGenerator.generate(workout(), ftp = 250, params = params)
    val b = RouteGenerator.generate(workout(), ftp = 250, params = params)
    assertArrayEquals(a.gradePercent, b.gradePercent, 1e-12)
  }

  @Test
  fun `different workout ids generate different routes`() {
    val a = RouteGenerator.generate(workout("w1"), 250, params)
    val b = RouteGenerator.generate(workout("w2"), 250, params)
    assertTrue(!a.gradePercent.contentEquals(b.gradePercent))
  }

  @Test
  fun `one grade point per second`() {
    val route = RouteGenerator.generate(workout(), 250, params)
    assertEquals(1200, route.gradePercent.size)
    assertEquals(1200, route.expectedAltitudeM.size)
  }

  @Test
  fun `grades stay within sane bounds`() {
    val route = RouteGenerator.generate(workout(), 250, params)
    assertTrue(route.gradePercent.all { it in -4.0..9.0 })
  }

  @Test
  fun `grade changes are smooth`() {
    val route = RouteGenerator.generate(workout(), 250, params)
    val maxDelta = route.gradePercent.toList().zipWithNext { a, b -> abs(b - a) }.max()
    assertTrue("max per-second grade delta $maxDelta too steep", maxDelta <= 1.5)
  }

  @Test
  fun `hard intervals climb and recoveries descend`() {
    val route = RouteGenerator.generate(workout(), 250, params)
    // Sample well inside each segment so boundary smoothing doesn't blur it.
    val vo2Grade = route.gradePercent.slice(450..550).average()   // 300 W = 120% FTP
    val recGrade = route.gradePercent.slice(750..850).average()   // 100 W = 40% FTP
    assertTrue("vo2 $vo2Grade should be a climb", vo2Grade > 2.0)
    assertTrue("recovery $recGrade should descend", recGrade < 0.0)
  }

  @Test
  fun `gradeAt clamps out of range lookups`() {
    val route = RouteGenerator.generate(workout(), 250, params)
    assertEquals(route.gradePercent.first(), route.gradeAt(-5), 1e-12)
    assertEquals(route.gradePercent.last(), route.gradeAt(99999), 1e-12)
  }

  @Test
  fun `empty workout yields empty route and zero grade`() {
    val empty = Workout("e", "Empty", null, WorkoutSource.MANUAL, emptyList())
    val route = RouteGenerator.generate(empty, 250, params)
    assertEquals(0, route.gradePercent.size)
    assertEquals(0.0, route.gradeAt(10), 1e-12)
  }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.domain.sim.RouteGeneratorTest"`
Expected: FAIL — unresolved reference `RouteGenerator`.

- [x] **Step 3: Write the implementation**

```kotlin
package com.trainerloop.domain.sim

import com.trainerloop.data.model.Workout
import com.trainerloop.domain.WorkoutMath
import kotlin.random.Random

class RouteProfile(
  val gradePercent: DoubleArray,
  val expectedAltitudeM: DoubleArray
) {
  fun gradeAt(sec: Int): Double =
    if (gradePercent.isEmpty()) 0.0
    else gradePercent[sec.coerceIn(0, gradePercent.lastIndex)]
}

/**
 * Synthesizes a grade-vs-time track from the workout plan: intensity maps to
 * grade (hard = climb, easy = descent), seeded noise makes it feel like
 * terrain, EMA smoothing ramps grade over ~8 s at segment boundaries.
 * Deterministic per workout id.
 */
object RouteGenerator {
  private const val NOISE_BUCKET_SEC = 30
  private const val NOISE_AMPLITUDE = 0.8
  private const val SMOOTHING_ALPHA = 0.12 // EMA step; ~8 s ramp

  fun generate(workout: Workout, ftp: Int, params: PhysicsParams): RouteProfile {
    val total = WorkoutMath.totalDurationSec(workout.segments)
    if (total <= 0) return RouteProfile(DoubleArray(0), DoubleArray(0))

    val targets = DoubleArray(total) { sec ->
      WorkoutMath.targetRangeAt(workout.segments, sec).let { (it.low + it.high) / 2.0 }
    }

    val rng = Random(workout.id.hashCode())
    val noise = DoubleArray(total / NOISE_BUCKET_SEC + 2) {
      rng.nextDouble(-NOISE_AMPLITUDE, NOISE_AMPLITUDE)
    }

    val grades = DoubleArray(total)
    var ema = Double.NaN
    for (sec in 0 until total) {
      val pctFtp = if (ftp > 0) targets[sec] * 100.0 / ftp else 0.0
      // Linear intensity->grade map: 65% FTP rides flat, VO2 ~+6%, recovery ~-3%.
      val base = ((pctFtp - 65.0) / 8.0).coerceIn(-3.0, 8.0)
      val bucket = sec / NOISE_BUCKET_SEC
      val frac = (sec % NOISE_BUCKET_SEC).toDouble() / NOISE_BUCKET_SEC
      val raw = base + noise[bucket] * (1 - frac) + noise[bucket + 1] * frac
      ema = if (ema.isNaN()) raw else ema + (raw - ema) * SMOOTHING_ALPHA
      grades[sec] = ema
    }

    // Expected elevation at *target* power — used only for the chart overlay.
    val altitude = DoubleArray(total)
    var alt = 0.0
    for (sec in 0 until total) {
      val v = VirtualSpeed.speedMps(targets[sec].toInt(), grades[sec], params)
      alt += v * grades[sec] / 100.0
      altitude[sec] = alt
    }
    return RouteProfile(grades, altitude)
  }
}
```

- [x] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.domain.sim.RouteGeneratorTest"`
Expected: PASS (8 tests)

- [x] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/trainerloop/domain/sim/RouteGenerator.kt \
  android/app/src/test/java/com/trainerloop/domain/sim/RouteGeneratorTest.kt
git commit -m "feat(sim): deterministic terrain generation from workout structure"
```

---

### Task 3: TelemetrySample fields + VirtualRideTracker

**Files:**
- Modify: `android/app/src/main/java/com/trainerloop/data/model/TelemetrySample.kt`
- Create: `android/app/src/main/java/com/trainerloop/domain/sim/VirtualRideTracker.kt`
- Test: `android/app/src/test/java/com/trainerloop/domain/sim/VirtualRideTrackerTest.kt`

**Interfaces:**
- Consumes: `RouteProfile` (Task 2), `VirtualSpeed`/`PhysicsParams` (Task 1).
- Produces: `TelemetrySample` gains `virtualSpeedKph: Double?`, `virtualDistanceM: Double?`, `virtualAltitudeM: Double?`, `gradePercent: Double?` (all default `null`). `class VirtualRideTracker(route: RouteProfile, params: PhysicsParams)` with `fun onTick(timeSec: Int, powerWatts: Int, dropout: Boolean): VirtualPoint`; `data class VirtualPoint(speedKph: Double, distanceM: Double, altitudeM: Double, gradePercent: Double)`.

- [x] **Step 1: Add the sample fields (no test needed — pure data)**

In `TelemetrySample.kt` change the data class to:

```kotlin
@Serializable
data class TelemetrySample(
  val timeSec: Int,
  val powerWatts: Int,
  val cadenceRpm: Int,
  val hrBpm: Int,
  val dropout: Boolean = false,
  val lagCompensated: Boolean = false,
  /** Virtual-ride simulation (null when the feature is off or for old sessions). */
  val virtualSpeedKph: Double? = null,
  val virtualDistanceM: Double? = null,
  val virtualAltitudeM: Double? = null,
  val gradePercent: Double? = null
)
```

Defaults mean old `samplesJson` blobs (which lack these keys) still deserialize — no Room migration.

- [x] **Step 2: Write the failing tracker test**

```kotlin
package com.trainerloop.domain.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualRideTrackerTest {
  private val params = PhysicsParams(riderKg = 75.0)

  /** 600 s of dead-flat route so speed is predictable. */
  private fun flatRoute() = RouteProfile(DoubleArray(600), DoubleArray(600))

  @Test
  fun `distance accumulates at physics speed`() {
    val tracker = VirtualRideTracker(flatRoute(), params)
    var last = tracker.onTick(0, 250, dropout = false)
    for (t in 1..60) last = tracker.onTick(t, 250, dropout = false)
    val expectedV = VirtualSpeed.speedMps(250, 0.0, params)
    assertEquals(expectedV * 60, last.distanceM, 1.0)
    assertEquals(expectedV * 3.6, last.speedKph, 0.1)
    assertEquals(0.0, last.altitudeM, 1e-9)
  }

  @Test
  fun `repeated ticks for the same second do not double integrate`() {
    val tracker = VirtualRideTracker(flatRoute(), params)
    tracker.onTick(1, 250, dropout = false)
    val a = tracker.onTick(5, 250, dropout = false)
    val b = tracker.onTick(5, 250, dropout = false)
    assertEquals(a.distanceM, b.distanceM, 1e-9)
  }

  @Test
  fun `seek forward adds at most one second of distance`() {
    val tracker = VirtualRideTracker(flatRoute(), params)
    tracker.onTick(10, 250, dropout = false)
    val before = tracker.onTick(10, 250, dropout = false).distanceM
    val after = tracker.onTick(400, 250, dropout = false).distanceM
    val v = VirtualSpeed.speedMps(250, 0.0, params)
    assertTrue(after - before <= v + 1e-9)
  }

  @Test
  fun `dropout holds previous speed`() {
    val tracker = VirtualRideTracker(flatRoute(), params)
    for (t in 0..10) tracker.onTick(t, 250, dropout = false)
    val point = tracker.onTick(11, 0, dropout = true)
    assertEquals(VirtualSpeed.speedMps(250, 0.0, params) * 3.6, point.speedKph, 0.1)
  }

  @Test
  fun `climbing gains altitude`() {
    val route = RouteProfile(DoubleArray(600) { 5.0 }, DoubleArray(600))
    val tracker = VirtualRideTracker(route, params)
    var last = tracker.onTick(0, 250, dropout = false)
    for (t in 1..60) last = tracker.onTick(t, 250, dropout = false)
    assertTrue("altitude should rise on a 5% climb, got ${last.altitudeM}", last.altitudeM > 5.0)
    assertEquals(5.0, last.gradePercent, 1e-9)
  }
}
```

- [x] **Step 3: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.domain.sim.VirtualRideTrackerTest"`
Expected: FAIL — unresolved reference `VirtualRideTracker`.

- [x] **Step 4: Write the implementation**

```kotlin
package com.trainerloop.domain.sim

/**
 * Integrates virtual speed into distance/altitude, one workout-second at a
 * time. Stateful and owned by the ViewModel so it survives recorder swaps.
 * Ticks are keyed by workout time: repeats for the same second are no-ops,
 * and dt is capped at 1 s so seeks don't teleport the rider.
 */
class VirtualRideTracker(
  private val route: RouteProfile,
  private val params: PhysicsParams
) {
  data class VirtualPoint(
    val speedKph: Double,
    val distanceM: Double,
    val altitudeM: Double,
    val gradePercent: Double
  )

  private var lastTimeSec = 0
  private var distanceM = 0.0
  private var altitudeM = 0.0
  private var lastSpeedMps = 0.0

  @Synchronized
  fun onTick(timeSec: Int, powerWatts: Int, dropout: Boolean): VirtualPoint {
    val grade = route.gradeAt(timeSec)
    val v = if (dropout) lastSpeedMps else VirtualSpeed.speedMps(powerWatts, grade, params)
    // ponytail: dt capped at 1 s — a seek skips route, not rides it
    val dt = (timeSec - lastTimeSec).coerceIn(0, 1)
    if (dt > 0) {
      distanceM += v * dt
      altitudeM += v * dt * grade / 100.0
    }
    if (timeSec > lastTimeSec) lastTimeSec = timeSec
    lastSpeedMps = v
    return VirtualPoint(v * 3.6, distanceM, altitudeM, grade)
  }
}
```

- [x] **Step 5: Run tests — tracker suite plus regression**

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.domain.sim.*"`
Expected: PASS.
Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS — the new `TelemetrySample` fields must not break any existing test (serialization round-trips, recorder, FIT).

- [x] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/trainerloop/data/model/TelemetrySample.kt \
  android/app/src/main/java/com/trainerloop/domain/sim/VirtualRideTracker.kt \
  android/app/src/test/java/com/trainerloop/domain/sim/VirtualRideTrackerTest.kt
git commit -m "feat(sim): virtual ride tracker + nullable sim fields on TelemetrySample"
```

---

### Task 4: Profile fields, persistence, and Advanced settings UI

**Files:**
- Modify: `android/app/src/main/java/com/trainerloop/data/model/UserProfile.kt`
- Modify: `android/app/src/main/java/com/trainerloop/data/repository/ProfileRepository.kt`
- Modify: `android/app/src/main/java/com/trainerloop/ui/settings/SettingsViewModel.kt`
- Modify: `android/app/src/main/java/com/trainerloop/ui/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: existing `ProfileRepository` prefs pattern, `SettingsGroupCard`/`OutlinedTextField` patterns in `SettingsScreen.kt`.
- Produces: `UserProfile` gains `virtualRideEnabled: Boolean = true`, `bikeWeightKg: Double = 8.0`, `rollingResistanceCrr: Double = 0.005`, `dragAreaCda: Double = 0.32`. Task 5 reads these to build `PhysicsParams`.

- [x] **Step 1: Add fields to UserProfile**

Append to the `UserProfile` data class:

```kotlin
  /** Virtual ride simulation (route overlay + physics speed) during workouts. */
  val virtualRideEnabled: Boolean = true,
  val bikeWeightKg: Double = 8.0,
  val rollingResistanceCrr: Double = 0.005,
  val dragAreaCda: Double = 0.32
```

- [x] **Step 2: Persist them in ProfileRepository**

In `load()` add before the closing paren:

```kotlin
      virtualRideEnabled = prefs.getBoolean(KEY_VIRTUAL_RIDE, true),
      bikeWeightKg = prefs.getFloat(KEY_BIKE_WEIGHT, 8.0f).toDouble(),
      rollingResistanceCrr = prefs.getFloat(KEY_CRR, 0.005f).toDouble(),
      dragAreaCda = prefs.getFloat(KEY_CDA, 0.32f).toDouble()
```

In `save()` add before `.apply()`:

```kotlin
      .putBoolean(KEY_VIRTUAL_RIDE, profile.virtualRideEnabled)
      .putFloat(KEY_BIKE_WEIGHT, profile.bikeWeightKg.toFloat())
      .putFloat(KEY_CRR, profile.rollingResistanceCrr.toFloat())
      .putFloat(KEY_CDA, profile.dragAreaCda.toFloat())
```

In `companion object` add:

```kotlin
    private const val KEY_VIRTUAL_RIDE = "virtual_ride_enabled"
    private const val KEY_BIKE_WEIGHT = "bike_weight_kg"
    private const val KEY_CRR = "rolling_resistance_crr"
    private const val KEY_CDA = "drag_area_cda"
```

- [x] **Step 3: Extend SettingsViewModel**

Add to `SettingsUiState`:

```kotlin
  val virtualRideEnabled: Boolean = true,
  val bikeWeightKg: String = "8.0",
  val crr: String = "0.005",
  val cda: String = "0.32",
```

In `init`, add to the `SettingsUiState(...)` construction:

```kotlin
      virtualRideEnabled = profile.virtualRideEnabled,
      bikeWeightKg = profile.bikeWeightKg.toString(),
      crr = profile.rollingResistanceCrr.toString(),
      cda = profile.dragAreaCda.toString(),
```

Add update functions following the existing pattern:

```kotlin
  fun updateVirtualRideEnabled(value: Boolean) {
    _uiState.value = _uiState.value.copy(virtualRideEnabled = value, isSaved = false)
  }

  fun updateBikeWeight(value: String) {
    _uiState.value = _uiState.value.copy(bikeWeightKg = value, isSaved = false)
  }

  fun updateCrr(value: String) {
    _uiState.value = _uiState.value.copy(crr = value, isSaved = false)
  }

  fun updateCda(value: String) {
    _uiState.value = _uiState.value.copy(cda = value, isSaved = false)
  }

  fun resetPhysicsDefaults() {
    _uiState.value = _uiState.value.copy(
      bikeWeightKg = "8.0", crr = "0.005", cda = "0.32", isSaved = false
    )
  }
```

In `save()`'s `it.copy(...)` add (clamps are the trust boundary — these feed a numeric solver):

```kotlin
          virtualRideEnabled = state.virtualRideEnabled,
          bikeWeightKg = (state.bikeWeightKg.toDoubleOrNull() ?: it.bikeWeightKg)
            .coerceIn(5.0, 15.0),
          rollingResistanceCrr = (state.crr.toDoubleOrNull() ?: it.rollingResistanceCrr)
            .coerceIn(0.002, 0.010),
          dragAreaCda = (state.cda.toDoubleOrNull() ?: it.dragAreaCda)
            .coerceIn(0.15, 0.60),
```

- [x] **Step 4: Add the settings section to SettingsScreen**

Insert a new group after the existing `SettingsGroupCard(title = "Zones")` block (around line 193), using the file's existing composable patterns (`SettingsGroupCard`, `OutlinedTextField`; add imports for `Switch`, `TextButton`, `KeyboardOptions`, `KeyboardType` if not present):

```kotlin
    SettingsGroupCard(title = "Virtual Ride") {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Simulated route + speed", style = MaterialTheme.typography.bodyMedium)
          Text(
            "Terrain overlay and physics-based speed during workouts",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Switch(
          checked = uiState.virtualRideEnabled,
          onCheckedChange = { viewModel.updateVirtualRideEnabled(it) }
        )
      }

      var advancedExpanded by remember { mutableStateOf(false) }
      TextButton(onClick = { advancedExpanded = !advancedExpanded }) {
        Text(if (advancedExpanded) "Hide advanced" else "Advanced")
      }
      if (advancedExpanded) {
        OutlinedTextField(
          value = uiState.bikeWeightKg,
          onValueChange = { viewModel.updateBikeWeight(it) },
          label = { Text("Bike weight (kg, 5–15)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
          value = uiState.crr,
          onValueChange = { viewModel.updateCrr(it) },
          label = { Text("Rolling resistance Crr (0.002–0.010)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
          value = uiState.cda,
          onValueChange = { viewModel.updateCda(it) },
          label = { Text("Drag area CdA m² (0.15–0.60)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        TextButton(onClick = { viewModel.resetPhysicsDefaults() }) {
          Text("Reset to defaults")
        }
      }
    }
```

Match the exact parameter names/callback style used by the surrounding groups in the file (e.g. whether the screen passes `viewModel` or lambda callbacks down — follow what `SettingsGroupCard(title = "Preferences")` does).

- [x] **Step 5: Build + run existing tests**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [x] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/trainerloop/data/model/UserProfile.kt \
  android/app/src/main/java/com/trainerloop/data/repository/ProfileRepository.kt \
  android/app/src/main/java/com/trainerloop/ui/settings/SettingsViewModel.kt \
  android/app/src/main/java/com/trainerloop/ui/settings/SettingsScreen.kt
git commit -m "feat(settings): virtual ride toggle + advanced physics params"
```

---

### Task 5: Wire tracker into TelemetryRecorder and WorkoutViewModel

**Files:**
- Modify: `android/app/src/main/java/com/trainerloop/domain/TelemetryRecorder.kt`
- Modify: `android/app/src/main/java/com/trainerloop/ui/workout/WorkoutViewModel.kt`
- Test: `android/app/src/test/java/com/trainerloop/domain/TelemetryRecorderTest.kt` (add a test to the existing file)

**Interfaces:**
- Consumes: `VirtualRideTracker`, `RouteGenerator`, `PhysicsParams` (Tasks 1–3); `UserProfile.virtualRideEnabled/bikeWeightKg/rollingResistanceCrr/dragAreaCda` (Task 4).
- Produces: `TelemetryRecorder` constructors gain a trailing `virtualRide: VirtualRideTracker? = null` parameter. `WorkoutUiState` gains `currentVirtualSpeedKph: Double?`, `currentGradePercent: Double?`, `virtualDistanceM: Double?`, `elevationProfile: DoubleArray?` — Task 8 reads all four.

- [x] **Step 1: Write the failing recorder test**

Add to the existing `TelemetryRecorderTest.kt`, which already has `bikeData(...)` and `shortWorkout(...)` helpers and drives time with `StandardTestDispatcher` + `advanceTimeBy`/`runCurrent`. New imports needed: `com.trainerloop.domain.sim.PhysicsParams`, `com.trainerloop.domain.sim.RouteProfile`, `com.trainerloop.domain.sim.VirtualRideTracker`, `org.junit.Assert.assertNotNull`, `org.junit.Assert.assertNull`.

```kotlin
  @Test
  fun `samples carry virtual ride fields when a tracker is attached`() = runTest {
    val testDispatcher = StandardTestDispatcher(testScheduler)
    val clock = WorkoutClock(shortWorkout(durationSec = 10), testDispatcher)
    val ftmsData = MutableStateFlow<IndoorBikeData?>(bikeData(powerWatts = 250, cadenceRpm = 90.0))
    val hrData = MutableStateFlow<Int?>(150)
    val tracker = VirtualRideTracker(
      RouteProfile(DoubleArray(600) { 2.0 }, DoubleArray(600)),
      PhysicsParams(riderKg = 75.0)
    )
    val recorder = TelemetryRecorder(
      clock,
      TelemetryRecorder.DataProvider(ftmsData, hrData),
      testDispatcher,
      tracker
    )

    recorder.startCollecting()
    runCurrent()
    clock.start()
    runCurrent()
    advanceTimeBy(3000)
    runCurrent()

    val sample = recorder.samples.value.last()
    assertNotNull(sample.virtualSpeedKph)
    assertTrue(sample.virtualSpeedKph!! > 0.0)
    assertTrue(sample.virtualDistanceM!! > 0.0)
    assertEquals(2.0, sample.gradePercent!!, 1e-9)
  }

  @Test
  fun `samples have null virtual fields without a tracker`() = runTest {
    val testDispatcher = StandardTestDispatcher(testScheduler)
    val clock = WorkoutClock(shortWorkout(durationSec = 5), testDispatcher)
    val ftmsData = MutableStateFlow<IndoorBikeData?>(bikeData(powerWatts = 200))
    val hrData = MutableStateFlow<Int?>(150)
    val recorder = TelemetryRecorder(
      clock,
      TelemetryRecorder.DataProvider(ftmsData, hrData),
      testDispatcher
    )

    recorder.startCollecting()
    runCurrent()
    clock.start()
    runCurrent()
    advanceTimeBy(1000)
    runCurrent()

    assertNull(recorder.samples.value.last().virtualSpeedKph)
  }
```

- [x] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.domain.TelemetryRecorderTest"`
Expected: FAIL — no such constructor parameter / null fields.

- [x] **Step 3: Extend TelemetryRecorder**

Primary constructor gains the parameter:

```kotlin
class TelemetryRecorder(
  private val clock: WorkoutClock,
  private val dataProvider: DataProvider,
  private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
  private val virtualRide: com.trainerloop.domain.sim.VirtualRideTracker? = null
) {
```

Secondary constructor passes it through:

```kotlin
  constructor(
    clock: WorkoutClock,
    ftms: FtmsManager,
    hr: HrManager? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    virtualRide: com.trainerloop.domain.sim.VirtualRideTracker? = null
  ) : this(
    clock,
    DataProvider(
      data = ftms.data,
      heartRate = hr?.heartRate ?: MutableStateFlow<Int?>(null).asStateFlow()
    ),
    dispatcher,
    virtualRide
  )
```

In `startCollecting()`'s collect block, replace the `val sample = TelemetrySample(...)` construction with:

```kotlin
        val virtual = virtualRide?.onTick(elapsedSec, lastPowerWatts, dropout)
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
          gradePercent = virtual?.gradePercent
        )
```

- [x] **Step 4: Wire the ViewModel**

In `WorkoutViewModel`, after `private val isRampTest = ...` add:

```kotlin
  private val physicsParams = com.trainerloop.domain.sim.PhysicsParams(
    riderKg = userProfile.weightKg,
    bikeKg = userProfile.bikeWeightKg,
    crr = userProfile.rollingResistanceCrr,
    cda = userProfile.dragAreaCda
  )
  private val route: com.trainerloop.domain.sim.RouteProfile? =
    if (userProfile.virtualRideEnabled && !isRampTest) {
      com.trainerloop.domain.sim.RouteGenerator.generate(workout, userProfile.ftp, physicsParams)
    } else null
  private val virtualRide = route?.let {
    com.trainerloop.domain.sim.VirtualRideTracker(it, physicsParams)
  }
```

Change the `_uiState` initialization to include the profile:

```kotlin
  private val _uiState = MutableStateFlow(
    WorkoutUiState(segments = workout.segments, elevationProfile = route?.expectedAltitudeM)
  )
```

In the recorder-recreation block, pass the tracker:

```kotlin
          val next = if (ftms != null) {
            TelemetryRecorder(clock, ftms, hr, dispatcher, virtualRide)
              .also { it.startCollecting() }
          } else null
```

In the `recorder.flatMapLatest { ... r.latest }` collect, extend the copy:

```kotlin
          _uiState.value = _uiState.value.copy(
            currentPowerWatts = sample.powerWatts,
            currentCadenceRpm = sample.cadenceRpm,
            currentVirtualSpeedKph = sample.virtualSpeedKph,
            currentGradePercent = sample.gradePercent,
            virtualDistanceM = sample.virtualDistanceM
          )
```

Add to `WorkoutUiState`:

```kotlin
  val currentVirtualSpeedKph: Double? = null,
  val currentGradePercent: Double? = null,
  val virtualDistanceM: Double? = null,
  /** Expected elevation per second (static per workout); null when sim is off. */
  val elevationProfile: DoubleArray? = null
```

(`DoubleArray` in a data class: it is set once at construction and never replaced, so reference equality through `copy()` is fine here.)

- [x] **Step 5: Run tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS — recorder tests (old + new) and any `ui/workout` ViewModel tests.

- [x] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/trainerloop/domain/TelemetryRecorder.kt \
  android/app/src/main/java/com/trainerloop/ui/workout/WorkoutViewModel.kt \
  android/app/src/test/java/com/trainerloop/domain/TelemetryRecorderTest.kt
git commit -m "feat(sim): stamp virtual ride data onto telemetry samples during workouts"
```

---

### Task 6: FIT encoder/decoder speed, distance, altitude

**Files:**
- Modify: `android/app/src/main/java/com/trainerloop/domain/fit/FitEncoder.kt`
- Modify: `android/app/src/main/java/com/trainerloop/domain/fit/FitDecoder.kt`
- Test: `android/app/src/test/java/com/trainerloop/domain/fit/FitEncoderTest.kt` (add tests to the existing file)

**Interfaces:**
- Consumes: `TelemetrySample` virtual fields (Task 3).
- Produces: FIT record messages carry speed (field 6, uint16, m/s×1000), distance (field 5, uint32, cm), altitude (field 2, uint16, (m+500)×5); session message carries total_distance (field 9, uint32, cm). `FitDecoder` fills the matching `TelemetrySample` fields on decode. `IcuActivityUploader` and `FitShareHelper` need no changes — they already pass full samples to `FitEncoder.encode`.

- [x] **Step 1: Write the failing round-trip test**

Add to `FitEncoderTest.kt` (follow the file's existing sample-building helpers):

```kotlin
  @Test
  fun `virtual ride fields survive an encode decode round trip`() {
    val samples = (1..10).map { t ->
      TelemetrySample(
        timeSec = t, powerWatts = 200, cadenceRpm = 90, hrBpm = 140,
        virtualSpeedKph = 36.0,
        virtualDistanceM = t * 10.0,
        virtualAltitudeM = 100.0 + t,
        gradePercent = 2.5
      )
    }
    val bytes = FitEncoder.encode(startTimeMs = 1_700_000_000_000L, elapsedSec = 10, samples = samples)
    val decoded = FitDecoder.decode(bytes)
    val last = decoded.samples.last()
    assertEquals(36.0, last.virtualSpeedKph!!, 0.1)
    assertEquals(100.0, last.virtualDistanceM!!, 0.1)
    assertEquals(110.0, last.virtualAltitudeM!!, 0.3)
  }

  @Test
  fun `samples without virtual data decode with null virtual fields`() {
    val samples = (1..5).map { t ->
      TelemetrySample(timeSec = t, powerWatts = 200, cadenceRpm = 90, hrBpm = 140)
    }
    val bytes = FitEncoder.encode(1_700_000_000_000L, 5, samples)
    val decoded = FitDecoder.decode(bytes)
    assertNull(decoded.samples.last().virtualSpeedKph)
    assertNull(decoded.samples.last().virtualDistanceM)
    assertNull(decoded.samples.last().virtualAltitudeM)
  }
```

- [x] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.domain.fit.FitEncoderTest"`
Expected: FAIL — decoded virtual fields are null in the first test.

- [x] **Step 3: Extend FitEncoder**

`recordFields` becomes:

```kotlin
    val recordFields = listOf(
      FitField(253, 4, BASE_TYPE_UINT32),
      FitField(7, 2, BASE_TYPE_UINT16),
      FitField(4, 1, BASE_TYPE_UINT8),
      FitField(3, 1, BASE_TYPE_UINT8),
      FitField(6, 2, BASE_TYPE_UINT16),  // speed, m/s * 1000
      FitField(5, 4, BASE_TYPE_UINT32),  // distance, cm
      FitField(2, 2, BASE_TYPE_UINT16)   // altitude, (m + 500) * 5
    )
```

The record data loop becomes:

```kotlin
    normalizedSamples.forEach { sample ->
      val cadence = if (sample.cadenceRpm > 0) sample.cadenceRpm else null
      val hr = if (sample.hrBpm > 0) sample.hrBpm else null
      val speed = sample.virtualSpeedKph?.let { (it / 3.6 * 1000).toInt() }
      val distance = sample.virtualDistanceM?.let { (it * 100).toInt() }
      val altitude = sample.virtualAltitudeM?.let { ((it + 500.0) * 5).toInt() }
      dataBytes.addAll(
        buildDataMessage(
          2,
          recordFields,
          listOf(
            fitStartTimestamp + sample.timeSec, sample.powerWatts, cadence, hr,
            speed, distance, altitude
          )
        )
      )
    }
```

(`encodeValue` already writes the invalid sentinel for `null`, so simulation-off rides stay valid.)

`sessionFields` gains total_distance:

```kotlin
    val sessionFields = listOf(
      FitField(253, 4, BASE_TYPE_UINT32),
      FitField(2, 4, BASE_TYPE_UINT32),
      FitField(5, 1, BASE_TYPE_ENUM),
      FitField(7, 4, BASE_TYPE_UINT32),
      FitField(8, 4, BASE_TYPE_UINT32),
      FitField(9, 4, BASE_TYPE_UINT32),  // total_distance, cm
      FitField(15, 1, BASE_TYPE_UINT8),
      FitField(16, 1, BASE_TYPE_UINT8),
      FitField(17, 1, BASE_TYPE_UINT8),
      FitField(18, 2, BASE_TYPE_UINT16),
      FitField(19, 2, BASE_TYPE_UINT16)
    )
```

and the session data message adds the value in the matching position (after `totalTimerMs`):

```kotlin
    val totalDistanceCm = normalizedSamples
      .lastOrNull { it.virtualDistanceM != null }
      ?.virtualDistanceM?.let { (it * 100).toInt() }
    // ... in the buildDataMessage(3, sessionFields, listOf(...)):
        listOf(
          fitEndTimestamp,
          fitStartTimestamp,
          sport,
          totalElapsedMs,
          totalTimerMs,
          totalDistanceCm,
          avgCadence?.toInt(),
          avgHr?.toInt(),
          maxHr,
          avgPower?.toInt(),
          maxPower
        )
```

- [x] **Step 4: Extend FitDecoder**

Replace the `records` triple bookkeeping with explicit locals. In the data-message branch, add locals and field cases:

```kotlin
        var timestamp: Long? = null
        var power: Int? = null
        var cadence: Int? = null
        var hr: Int? = null
        var speedMms: Int? = null
        var distanceCm: Long? = null
        var altitudeRaw: Int? = null
        for (f in def.fields) {
          if (def.globalNum == RECORD_MSG || f.num == 253) {
            val v = readValue(bytes, pos, f, def.littleEndian)
            when (f.num) {
              253 -> timestamp = v
              7 -> power = v?.toInt()
              4 -> cadence = v?.toInt()
              3 -> hr = v?.toInt()
              6 -> speedMms = v?.toInt()
              5 -> distanceCm = v
              2 -> altitudeRaw = v?.toInt()
            }
          }
          pos += f.size
        }
```

Change the `records` list element type to a private data class and the final mapping:

```kotlin
  private data class RecordRow(
    val ts: Long,
    val power: Int?,
    val cadence: Int?,
    val hr: Int?,
    val speedMms: Int?,
    val distanceCm: Long?,
    val altitudeRaw: Int?
  )
```

```kotlin
    val samples = records.map { r ->
      TelemetrySample(
        timeSec = (r.ts - t0).toInt(),
        powerWatts = r.power ?: 0,
        cadenceRpm = r.cadence ?: 0,
        hrBpm = r.hr ?: 0,
        virtualSpeedKph = r.speedMms?.let { it / 1000.0 * 3.6 },
        virtualDistanceM = r.distanceCm?.let { it / 100.0 },
        virtualAltitudeM = r.altitudeRaw?.let { it / 5.0 - 500.0 }
      )
    }
```

- [x] **Step 5: Run all FIT tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.domain.fit.*"`
Expected: PASS — new round-trip tests and all pre-existing encoder/decoder tests (real-file fixtures in `FitDecoderTest` must still decode).

- [x] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/trainerloop/domain/fit/FitEncoder.kt \
  android/app/src/main/java/com/trainerloop/domain/fit/FitDecoder.kt \
  android/app/src/test/java/com/trainerloop/domain/fit/FitEncoderTest.kt
git commit -m "feat(fit): speed/distance/altitude record fields + session total distance"
```

---

### Task 7: Distance + ascent in summaries (complete screen, session detail)

**Files:**
- Modify: `android/app/src/main/java/com/trainerloop/domain/WorkoutSummaryMath.kt`
- Modify: `android/app/src/main/java/com/trainerloop/ui/complete/WorkoutCompleteViewModel.kt`
- Modify: `android/app/src/main/java/com/trainerloop/ui/complete/WorkoutCompleteScreen.kt`
- Modify: `android/app/src/main/java/com/trainerloop/ui/history/SessionDetailScreen.kt`
- Test: `android/app/src/test/java/com/trainerloop/domain/WorkoutSummaryMathVirtualTest.kt` (new file)

**Interfaces:**
- Consumes: `TelemetrySample` virtual fields (Task 3).
- Produces: `WorkoutSummaryMath.totalDistanceKm(samples: List<TelemetrySample>): Double` and `WorkoutSummaryMath.totalAscentM(samples: List<TelemetrySample>): Int`.

- [x] **Step 1: Write the failing test**

```kotlin
package com.trainerloop.domain

import com.trainerloop.data.model.TelemetrySample
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutSummaryMathVirtualTest {
  private fun sample(t: Int, dist: Double?, alt: Double?) = TelemetrySample(
    timeSec = t, powerWatts = 200, cadenceRpm = 90, hrBpm = 140,
    virtualDistanceM = dist, virtualAltitudeM = alt
  )

  @Test
  fun `distance is the last recorded cumulative value`() {
    val samples = listOf(sample(1, 10.0, 0.0), sample(2, 20.0, 0.0), sample(3, 5500.0, 0.0))
    assertEquals(5.5, WorkoutSummaryMath.totalDistanceKm(samples), 1e-9)
  }

  @Test
  fun `ascent counts only positive altitude deltas`() {
    val samples = listOf(
      sample(1, 0.0, 0.0), sample(2, 0.0, 10.0), sample(3, 0.0, 4.0), sample(4, 0.0, 12.0)
    )
    assertEquals(18, WorkoutSummaryMath.totalAscentM(samples)) // +10, -6 ignored, +8
  }

  @Test
  fun `sessions without virtual data report zero`() {
    val samples = listOf(sample(1, null, null), sample(2, null, null))
    assertEquals(0.0, WorkoutSummaryMath.totalDistanceKm(samples), 1e-9)
    assertEquals(0, WorkoutSummaryMath.totalAscentM(samples))
  }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.domain.WorkoutSummaryMathVirtualTest"`
Expected: FAIL — unresolved references.

- [x] **Step 3: Implement in WorkoutSummaryMath**

```kotlin
  fun totalDistanceKm(samples: List<TelemetrySample>): Double =
    (samples.lastOrNull { it.virtualDistanceM != null }?.virtualDistanceM ?: 0.0) / 1000.0

  fun totalAscentM(samples: List<TelemetrySample>): Int {
    var ascent = 0.0
    var prev: Double? = null
    samples.forEach { s ->
      val alt = s.virtualAltitudeM ?: return@forEach
      prev?.let { if (alt > it) ascent += alt - it }
      prev = alt
    }
    return ascent.toInt()
  }
```

- [x] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.domain.WorkoutSummaryMathVirtualTest"`
Expected: PASS

- [x] **Step 5: Surface in the complete screen**

`WorkoutCompleteUiState` gains:

```kotlin
  val distanceKm: Double = 0.0,
  val ascentM: Int = 0,
```

In `computeSummary()` add to the locals and the `copy(...)`:

```kotlin
    val distanceKm = WorkoutSummaryMath.totalDistanceKm(samples)
    val ascentM = WorkoutSummaryMath.totalAscentM(samples)
    // ...
      distanceKm = distanceKm,
      ascentM = ascentM
```

In `WorkoutCompleteScreen.kt`, after the `StatRow("Total Work", ...)` line (~128) add:

```kotlin
          if (uiState.distanceKm > 0) {
            StatRow("Distance", "%.1f km".format(uiState.distanceKm))
            StatRow("Elevation Gain", "${uiState.ascentM} m")
          }
```

- [x] **Step 6: Surface in the session detail screen**

`SessionDetailScreen.kt` already decodes samples from `s.samplesJson` (~line 89). In the same composable scope as the existing `StatRow` block (~line 131–135), compute once with `remember` and append rows after `StatRow("Avg Cadence", ...)`:

```kotlin
          val distanceKm = remember(samples) { WorkoutSummaryMath.totalDistanceKm(samples) }
          if (distanceKm > 0) {
            StatRow("Distance", "%.1f km".format(distanceKm))
            StatRow("Elevation Gain", "${WorkoutSummaryMath.totalAscentM(samples)} m")
          }
```

(If the decoded `samples` variable isn't in scope at the StatRow block, hoist the existing decode so both the chart and these rows share it — don't decode twice. Import `com.trainerloop.domain.WorkoutSummaryMath` and `androidx.compose.runtime.remember` as needed.)

- [x] **Step 7: Build + full test run**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [x] **Step 8: Commit**

```bash
git add android/app/src/main/java/com/trainerloop/domain/WorkoutSummaryMath.kt \
  android/app/src/test/java/com/trainerloop/domain/WorkoutSummaryMathVirtualTest.kt \
  android/app/src/main/java/com/trainerloop/ui/complete/WorkoutCompleteViewModel.kt \
  android/app/src/main/java/com/trainerloop/ui/complete/WorkoutCompleteScreen.kt \
  android/app/src/main/java/com/trainerloop/ui/history/SessionDetailScreen.kt
git commit -m "feat(sim): distance and elevation gain in ride summaries"
```

---

### Task 8: Elevation overlay on the workout chart + live tiles

**Files:**
- Modify: `android/app/src/main/java/com/trainerloop/ui/components/WorkoutChart.kt`
- Modify: `android/app/src/main/java/com/trainerloop/ui/workout/WorkoutScreen.kt`

**Interfaces:**
- Consumes: `WorkoutUiState.elevationProfile / currentVirtualSpeedKph / currentGradePercent / virtualDistanceM` (Task 5).
- Produces: `WorkoutChart` gains `elevationProfile: DoubleArray? = null` parameter. UI only — no unit test; verify visually (Step 4).

- [x] **Step 1: Add the overlay to WorkoutChart**

Signature:

```kotlin
fun WorkoutChart(
  segments: List<WorkoutSegment>,
  samples: List<TelemetrySample>,
  elapsedSec: Int,
  ftp: Int,
  modifier: Modifier = Modifier,
  elevationProfile: DoubleArray? = null
) {
```

Above the `Canvas`, next to the other color vals (~line 93):

```kotlin
  val elevationColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f)
```

Inside the Canvas, right after the `yForHr` helper definition and *before* the gridlines (so terrain sits behind everything):

```kotlin
        // Soft terrain silhouette across the bottom 30% of the chart.
        if (elevationProfile != null && elevationProfile.isNotEmpty()) {
          val minAlt = elevationProfile.min()
          val altSpan = (elevationProfile.max() - minAlt).coerceAtLeast(1.0)
          val bandHeight = chartHeight * 0.3f
          val elevPath = Path()
          elevPath.moveTo(xForTime(winStart), chartBottom)
          val elevStep = (winSpan / 200).coerceAtLeast(1)
          var t = winStart
          while (t <= winEnd) {
            val alt = elevationProfile[t.coerceIn(0, elevationProfile.lastIndex)]
            val y = chartBottom - ((alt - minAlt) / altSpan).toFloat() * bandHeight
            elevPath.lineTo(xForTime(t), y)
            t += elevStep
          }
          elevPath.lineTo(xForTime(winEnd), chartBottom)
          elevPath.close()
          drawPath(elevPath, color = elevationColor)
        }
```

- [x] **Step 2: Pass the profile and add live tiles in WorkoutScreen**

At the `WorkoutChart(` call (~line 352) add:

```kotlin
          elevationProfile = uiState.elevationProfile,
```

After the existing `BigMetric` Row's closing brace (~line 329), add a second conditional row:

```kotlin
      uiState.currentVirtualSpeedKph?.let { speedKph ->
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          BigMetric(
            label = "Speed",
            value = "%.1f".format(speedKph),
            unit = "km/h",
            modifier = Modifier.weight(1f)
          )
          BigMetric(
            label = "Grade",
            value = "%.1f".format(uiState.currentGradePercent ?: 0.0),
            unit = "%",
            modifier = Modifier.weight(1f)
          )
          BigMetric(
            label = "Distance",
            value = "%.1f".format((uiState.virtualDistanceM ?: 0.0) / 1000.0),
            unit = "km",
            modifier = Modifier.weight(1f)
          )
        }
      }
```

The immersive chart (`ImmersiveWorkoutChart`, ~line 639) is deliberately left without the overlay — add later if the immersive mode wants it.

- [x] **Step 3: Build + full test run**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [x] **Step 4: Manual verification (device or emulator)**

Install the debug build, start any interval workout with the trainer (or the scenario simulator):
- terrain silhouette visible behind the interval blocks, climbing during hard intervals;
- Speed/Grade/Distance tiles updating each second, speed dropping when grade rises;
- Settings → Virtual Ride toggle off → tiles and overlay gone on the next workout;
- finish a short ride → Distance + Elevation Gain rows on the complete screen; FIT upload on intervals.icu shows speed/distance/altitude.

- [x] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/trainerloop/ui/components/WorkoutChart.kt \
  android/app/src/main/java/com/trainerloop/ui/workout/WorkoutScreen.kt
git commit -m "feat(sim): elevation overlay on workout chart + live speed/grade/distance tiles"
```
