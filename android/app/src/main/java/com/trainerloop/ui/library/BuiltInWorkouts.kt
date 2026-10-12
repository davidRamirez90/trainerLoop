package com.trainerloop.ui.library

import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.Workout
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.data.model.WorkoutSource

object BuiltInWorkouts {

  fun all(ftp: Int = 250): List<Workout> = listOf(
    Workout(
      id = "sweet_spot",
      name = "Sweet Spot",
      description = "Aerobic sweet spot training",
      source = WorkoutSource.MANUAL,
      segments = listOf(
        WorkoutSegment.FreeRide(id = "wu", durationSec = 300, label = "Warm Up", phase = SegmentPhase.WARMUP),
        WorkoutSegment.Step(id = "ss1", durationSec = 600, label = "Sweet Spot", phase = SegmentPhase.WORK, isWork = true, targetRange = TargetRange(220, 230)),
        WorkoutSegment.Step(id = "ss2", durationSec = 600, label = "Sweet Spot", phase = SegmentPhase.WORK, isWork = true, targetRange = TargetRange(225, 235)),
        WorkoutSegment.FreeRide(id = "cd", durationSec = 300, label = "Cool Down", phase = SegmentPhase.COOLDOWN)
      )
    ),
    Workout(
      id = "pyramid",
      name = "Power Pyramid",
      description = "Build into a peak then back down",
      source = WorkoutSource.MANUAL,
      segments = listOf(
        WorkoutSegment.FreeRide(id = "wu", durationSec = 300, label = "Warm Up", phase = SegmentPhase.WARMUP),
        WorkoutSegment.Step(id = "l1", durationSec = 180, label = "Endurance", phase = SegmentPhase.WORK, isWork = true, targetRange = TargetRange(170, 180)),
        WorkoutSegment.Step(id = "l2", durationSec = 180, label = "Tempo", phase = SegmentPhase.WORK, isWork = true, targetRange = TargetRange(200, 210)),
        WorkoutSegment.Step(id = "l3", durationSec = 120, label = "Threshold", phase = SegmentPhase.WORK, isWork = true, targetRange = TargetRange(240, 250)),
        WorkoutSegment.Step(id = "l4", durationSec = 180, label = "Tempo", phase = SegmentPhase.WORK, isWork = true, targetRange = TargetRange(200, 210)),
        WorkoutSegment.Step(id = "l5", durationSec = 180, label = "Endurance", phase = SegmentPhase.WORK, isWork = true, targetRange = TargetRange(170, 180)),
        WorkoutSegment.FreeRide(id = "cd", durationSec = 300, label = "Cool Down", phase = SegmentPhase.COOLDOWN)
      )
    ),
    Workout(
      id = "endurance",
      name = "Endurance Ride",
      description = "Long steady endurance ride",
      source = WorkoutSource.MANUAL,
      segments = listOf(
        WorkoutSegment.FreeRide(id = "wu", durationSec = 600, label = "Easy Start", phase = SegmentPhase.WARMUP),
        WorkoutSegment.Step(id = "main", durationSec = 3600, label = "Endurance", phase = SegmentPhase.WORK, isWork = true, targetRange = TargetRange(163, 188)),
        WorkoutSegment.FreeRide(id = "cd", durationSec = 600, label = "Cool Down", phase = SegmentPhase.COOLDOWN)
      )
    )
  ).map { workout ->
    workout.copy(segments = workout.segments.map { segment ->
      when (segment) {
        is WorkoutSegment.Step -> segment.copy(targetRange = TargetRange(
          (segment.targetRange.low * ftp / 250.0).toInt(),
          (segment.targetRange.high * ftp / 250.0).toInt()
        ))
        else -> segment
      }
    })
  }
}
