package com.trainerloop.ui.workout.detail

import androidx.lifecycle.ViewModel
import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.Workout
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.domain.WorkoutMath
import com.trainerloop.domain.WorkoutStats
import com.trainerloop.domain.WorkoutSummaryMath

enum class WorkoutCategory(val label: String) {
  ENDURANCE("Endurance"),
  SWEET_SPOT("Sweet Spot"),
  THRESHOLD("Threshold"),
  VO2_MAX("VO2 Max")
}

data class IntervalRow(
  val color: androidx.compose.ui.graphics.Color,
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
      color = phaseColor(segment.phase),
      name = segment.label ?: "${segment.phase.name.lowercase().replaceFirstChar { it.uppercase() }}",
      durationSec = segment.durationSec,
      targetFtpPct = targetFtpPct(segment, ftp)
    )
  }

  private fun phaseColor(phase: SegmentPhase): androidx.compose.ui.graphics.Color = when (phase) {
    SegmentPhase.WARMUP -> com.trainerloop.ui.theme.Amber80
    SegmentPhase.WORK -> com.trainerloop.ui.theme.Green60
    SegmentPhase.RECOVERY -> com.trainerloop.ui.theme.Blue80
    SegmentPhase.COOLDOWN -> com.trainerloop.ui.theme.Amber80
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
