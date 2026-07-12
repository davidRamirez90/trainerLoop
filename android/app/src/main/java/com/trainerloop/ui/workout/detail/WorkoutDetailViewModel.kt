package com.trainerloop.ui.workout.detail

import androidx.lifecycle.ViewModel
import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.Workout
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.domain.WorkoutMath
import com.trainerloop.domain.WorkoutStats
import com.trainerloop.domain.WorkoutSummaryMath
import com.trainerloop.ui.theme.ZoneColors

enum class WorkoutCategory(val label: String) {
  ENDURANCE("Endurance"),
  SWEET_SPOT("Sweet Spot"),
  THRESHOLD("Threshold"),
  VO2_MAX("VO2 Max")
}

data class IntervalRow(
  val color: androidx.compose.ui.graphics.Color?,
  val name: String,
  val durationSec: Int,
  val targetFtpPct: String
)

class WorkoutDetailViewModel(
  val workout: Workout,
  private val ftp: Int = 250
) : ViewModel() {

  val stats: WorkoutStats = WorkoutSummaryMath.workoutStats(workout, ftp)

  val totalDurationSec: Int = WorkoutMath.totalDurationSec(workout.segments)

  val category: WorkoutCategory = when (workout.id) {
    "endurance" -> WorkoutCategory.ENDURANCE
    "sweet_spot" -> WorkoutCategory.SWEET_SPOT
    "pyramid" -> WorkoutCategory.THRESHOLD
    else -> {
      when {
        stats.intensityFactor < 0.75 -> WorkoutCategory.ENDURANCE
        stats.intensityFactor < 0.90 -> WorkoutCategory.SWEET_SPOT
        stats.intensityFactor < 1.05 -> WorkoutCategory.THRESHOLD
        else -> WorkoutCategory.VO2_MAX
      }
    }
  }

  val intervals: List<IntervalRow> = workout.segments.map { segment ->
    IntervalRow(
      color = phaseColor(segment),
      name = segment.label ?: "${segment.phase.name.lowercase().replaceFirstChar { it.uppercase() }}",
      durationSec = segment.durationSec,
      targetFtpPct = targetFtpPct(segment, ftp)
    )
  }

  private fun phaseColor(segment: WorkoutSegment): androidx.compose.ui.graphics.Color? = when (segment.phase) {
    SegmentPhase.WARMUP -> com.trainerloop.ui.theme.Amber80
    SegmentPhase.WORK -> ZoneColors.forTarget(
      targetWatts = segmentTargetPower(segment),
      ftp = ftp,
      dark = true
    ).line
    // Recovery is an interaction/brand cue resolved from MaterialTheme in the composable.
    SegmentPhase.RECOVERY -> null
    SegmentPhase.COOLDOWN -> com.trainerloop.ui.theme.Amber80
  }

  private fun segmentTargetPower(segment: WorkoutSegment): Int = when (segment) {
    is WorkoutSegment.Step -> (segment.targetRange.low + segment.targetRange.high) / 2
    is WorkoutSegment.Ramp -> (segment.startPower + segment.endPower) / 2
    is WorkoutSegment.FreeRide -> 0
  }

  private fun targetFtpPct(segment: WorkoutSegment, ftp: Int): String {
    if (ftp == 0) return "—"
    return when (segment) {
      is WorkoutSegment.Step -> {
        val low = (segment.targetRange.low * 100 / ftp)
        val high = (segment.targetRange.high * 100 / ftp)
        if (low == high) "$low%" else "$low–$high%"
      }
      is WorkoutSegment.Ramp -> {
        val low = (segment.startPower * 100 / ftp)
        val high = (segment.endPower * 100 / ftp)
        "$low–$high%"
      }
      is WorkoutSegment.FreeRide -> "Free ride"
    }
  }
}
