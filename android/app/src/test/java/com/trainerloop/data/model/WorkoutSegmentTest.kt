package com.trainerloop.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutSegmentTest {
  @Test
  fun `workout total duration is sum of segments`() {
    val workout = sampleWorkout()
    val total = workout.segments.sumOf { it.durationSec }
    assertEquals(600, total)
  }

  private fun sampleWorkout(): Workout = Workout(
    id = "sample",
    name = "Sample Workout",
    description = null,
    source = WorkoutSource.MANUAL,
    segments = listOf(
      WorkoutSegment.Step(
        id = "warmup",
        durationSec = 300,
        label = "Warm Up",
        phase = SegmentPhase.WARMUP,
        isWork = false,
        targetRange = TargetRange(100, 150)
      ),
      WorkoutSegment.Ramp(
        id = "ramp",
        durationSec = 180,
        label = "Ramp",
        phase = SegmentPhase.WORK,
        isWork = true,
        startPower = 150,
        endPower = 250
      ),
      WorkoutSegment.FreeRide(
        id = "free",
        durationSec = 120,
        label = "Free Ride",
        phase = SegmentPhase.RECOVERY
      )
    )
  )
}
