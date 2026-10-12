package com.trainerloop.domain

import com.trainerloop.data.model.Workout
import com.trainerloop.ui.library.BuiltInWorkouts

object WorkoutResolver {
  fun resolve(workoutId: String, ftp: Int, imported: List<Workout>): Workout? = when {
    RampTest.isRampTest(workoutId) -> RampTest.generate(ftp)
    else -> BuiltInWorkouts.all(ftp).find { it.id == workoutId }
      ?: imported.find { it.id == workoutId }
  }
}
