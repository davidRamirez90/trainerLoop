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
