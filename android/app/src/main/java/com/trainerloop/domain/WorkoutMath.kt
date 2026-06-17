package com.trainerloop.domain

import com.trainerloop.data.model.*

object WorkoutMath {
  fun totalDurationSec(segments: List<WorkoutSegment>): Int =
    segments.sumOf { it.durationSec }

  fun segmentIndexAt(segments: List<WorkoutSegment>, elapsedSec: Int): Int {
    var remaining = elapsedSec
    segments.forEachIndexed { index, segment ->
      if (remaining < segment.durationSec) return index
      remaining -= segment.durationSec
    }
    return segments.lastIndex.coerceAtLeast(0)
  }

  fun targetRangeAt(segments: List<WorkoutSegment>, elapsedSec: Int): TargetRange {
    val index = segmentIndexAt(segments, elapsedSec)
    val segment = segments.getOrNull(index) ?: return TargetRange(0, 0)
    val segmentStart = segments.take(index).sumOf { it.durationSec }
    val elapsedInSegment = (elapsedSec - segmentStart).coerceIn(0, segment.durationSec)
    return when (segment) {
      is WorkoutSegment.Step -> segment.targetRange
      is WorkoutSegment.Ramp -> {
        val ratio = if (segment.durationSec == 0) 0.0
        else elapsedInSegment / segment.durationSec.toDouble()
        val power = (segment.startPower + (segment.endPower - segment.startPower) * ratio).toInt()
        TargetRange(power, power)
      }
      is WorkoutSegment.FreeRide -> TargetRange(0, 0)
    }
  }
}
