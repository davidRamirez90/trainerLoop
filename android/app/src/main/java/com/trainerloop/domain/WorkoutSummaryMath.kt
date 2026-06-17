package com.trainerloop.domain

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

  fun workoutStats(workout: Workout, ftp: Int = 250): WorkoutStats {
    val np = normalizedPower(workout)
    val ifactor = intensityFactor(np, ftp)
    val activeSec = WorkoutMath.totalDurationSec(workout.segments)
    return WorkoutStats(
      durationSec = activeSec,
      normalizedPower = np,
      intensityFactor = ifactor,
      tss = tss(np, ftp, activeSec)
    )
  }

  fun segmentAveragePower(segment: WorkoutSegment): Double = when (segment) {
    is WorkoutSegment.Step -> (segment.targetRange.low + segment.targetRange.high) / 2.0
    is WorkoutSegment.Ramp -> (segment.startPower + segment.endPower) / 2.0
    is WorkoutSegment.FreeRide -> 0.0
  }
}

data class WorkoutStats(
  val durationSec: Int,
  val normalizedPower: Int,
  val intensityFactor: Double,
  val tss: Int
)
