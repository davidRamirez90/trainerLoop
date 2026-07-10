package com.trainerloop.domain

import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.data.model.Workout
import com.trainerloop.data.model.WorkoutSegment
import kotlin.math.pow

object WorkoutSummaryMath {

  fun averagePower(workout: Workout): Int {
    var totalWork = 0.0
    var totalSec = 0
    workout.segments.forEach { segment ->
      val avgPower = segmentAveragePower(segment)
      totalWork += avgPower * segment.durationSec
      totalSec += segment.durationSec
    }
    return if (totalSec > 0) (totalWork / totalSec).toInt() else 0
  }

  fun normalizedPower(workout: Workout): Int {
    val samples = mutableListOf<Double>()
    workout.segments.forEach { segment ->
      val avgPower = segmentAveragePower(segment)
      repeat(segment.durationSec.coerceAtLeast(0)) {
        samples.add(avgPower)
      }
    }
    if (samples.isEmpty()) return 0
    val windowSize = 30
    val rolling = samples.windowed(windowSize, 1, partialWindows = true)
      .map { window -> window.average() }
    val avgFourth = rolling.map { it.pow(4.0) }.average()
    return avgFourth.pow(0.25).toInt()
  }

  fun intensityFactor(np: Int, ftp: Int): Double =
    if (ftp == 0) 0.0 else np / ftp.toDouble()

  fun tss(np: Int, ftp: Int, activeSec: Int): Int {
    val ifactor = intensityFactor(np, ftp)
    return ((activeSec * np * ifactor) / (ftp * 3600.0) * 100.0).toInt()
  }

  fun workoutStats(workout: Workout, ftp: Int): WorkoutStats {
    val np = normalizedPower(workout)
    val ifactor = intensityFactor(np, ftp)
    val activeSec = WorkoutMath.totalDurationSec(workout.segments)
    val hasMeaningfulPowerTargets = !isFreeRideOnly(workout)
    return WorkoutStats(
      durationSec = activeSec,
      normalizedPower = np,
      intensityFactor = ifactor,
      tss = tss(np, ftp, activeSec),
      hasMeaningfulPowerTargets = hasMeaningfulPowerTargets
    )
  }

  /** True when the workout leaves all power decisions to the rider. */
  fun isFreeRideOnly(workout: Workout): Boolean =
    workout.segments.isNotEmpty() && workout.segments.all { it is WorkoutSegment.FreeRide }

  fun segmentAveragePower(segment: WorkoutSegment): Double = when (segment) {
    is WorkoutSegment.Step -> (segment.targetRange.low + segment.targetRange.high) / 2.0
    is WorkoutSegment.Ramp -> (segment.startPower + segment.endPower) / 2.0
    is WorkoutSegment.FreeRide -> 0.0
  }

  // Sample-based versions for post-ride analysis

  fun averagePower(samples: List<TelemetrySample>): Int {
    if (samples.isEmpty()) return 0
    return samples.map { it.powerWatts }.average().toInt()
  }

  fun maxPower(samples: List<TelemetrySample>): Int {
    return samples.maxOfOrNull { it.powerWatts } ?: 0
  }

  fun averageCadence(samples: List<TelemetrySample>): Int {
    if (samples.isEmpty()) return 0
    return samples.map { it.cadenceRpm }.average().toInt()
  }

  fun averageHr(samples: List<TelemetrySample>): Int {
    if (samples.isEmpty()) return 0
    return samples.map { it.hrBpm }.average().toInt()
  }

  /** Returns elapsed seconds spent in each power zone, indexed 0..5 for Z1..Z6. */
  fun zoneTimeSec(samples: List<TelemetrySample>, ftp: Int): IntArray {
    val secondsByZone = IntArray(6)
    var previousTimeSec: Int? = null

    samples.forEach { sample ->
      val sampleSeconds = previousTimeSec
        ?.let { (sample.timeSec - it).coerceAtLeast(1) }
        ?: sample.timeSec.coerceAtLeast(1)
      val zone = PowerZoneMath.zoneIndex(sample.powerWatts, ftp)
      secondsByZone[zone - 1] += sampleSeconds
      previousTimeSec = sample.timeSec
    }

    return secondsByZone
  }

  fun normalizedPower(samples: List<TelemetrySample>): Int {
    if (samples.isEmpty()) return 0
    val windowSize = 30
    val rolling = samples.windowed(windowSize, 1, partialWindows = true)
      .map { window -> window.map { it.powerWatts }.average() }
    val avgFourth = rolling.map { it.pow(4.0) }.average()
    return avgFourth.pow(0.25).toInt()
  }

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

  fun caloriesKcal(avgPower: Int, activeSec: Int): Int {
    if (activeSec <= 0) return 0
    return ((avgPower * activeSec) / 1000.0).toInt()
  }

  fun totalWorkKj(avgPower: Int, activeSec: Int): Int {
    if (activeSec <= 0) return 0
    return (avgPower * activeSec) / 1000
  }
}

data class WorkoutStats(
  val durationSec: Int,
  val normalizedPower: Int,
  val intensityFactor: Double,
  val tss: Int,
  val hasMeaningfulPowerTargets: Boolean = true
) {
  val plannedIntensityFactor: Double?
    get() = intensityFactor.takeIf { hasMeaningfulPowerTargets }

  val plannedTss: Int?
    get() = tss.takeIf { hasMeaningfulPowerTargets }
}
