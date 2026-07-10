package com.trainerloop.domain

import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.Workout
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.data.model.WorkoutSource
import com.trainerloop.ui.library.BuiltInWorkouts

object WorkoutResolver {
  const val FREE_RIDE_ID = "free-ride"
  private const val FREE_RIDE_MAX_SEC = 12 * 3600

  fun resolve(workoutId: String, ftp: Int, imported: List<Workout>): Workout? = when {
    workoutId == FREE_RIDE_ID -> Workout(
      id = FREE_RIDE_ID,
      name = "Free Ride",
      description = "Open-ended ride — stop whenever you like",
      source = WorkoutSource.MANUAL,
      segments = listOf(
        WorkoutSegment.FreeRide(
          id = "free", durationSec = FREE_RIDE_MAX_SEC, label = "Free Ride",
          phase = SegmentPhase.WORK
        )
      )
    )
    RampTest.isRampTest(workoutId) -> RampTest.generate(ftp)
    else -> BuiltInWorkouts.all().find { it.id == workoutId }
      ?: imported.find { it.id == workoutId }
  }
}
