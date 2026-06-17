package com.trainerloop.data.model

data class Workout(
  val id: String,
  val name: String,
  val description: String?,
  val source: WorkoutSource,
  val segments: List<WorkoutSegment>
)

enum class WorkoutSource { MANUAL, IMPORTED }
