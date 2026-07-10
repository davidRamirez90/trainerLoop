package com.trainerloop.domain

import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.Workout
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.data.model.WorkoutSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSummaryMathTest {
  @Test
  fun `free ride workout has no meaningful power targets`() {
    assertTrue(WorkoutSummaryMath.isFreeRideOnly(freeRideWorkout()))
  }

  @Test
  fun `workout with a power target is not free ride only`() {
    val workout = freeRideWorkout().copy(
      segments = freeRideWorkout().segments + WorkoutSegment.Step(
        id = "target",
        durationSec = 60,
        label = "Target",
        phase = SegmentPhase.WORK,
        isWork = true,
        targetRange = TargetRange(180, 200)
      )
    )

    assertFalse(WorkoutSummaryMath.isFreeRideOnly(workout))
  }

  @Test
  fun `free ride workout suppresses planned IF and TSS`() {
    val stats = WorkoutSummaryMath.workoutStats(freeRideWorkout(), ftp = 250)

    assertNull(stats.plannedIntensityFactor)
    assertNull(stats.plannedTss)
  }

  private fun freeRideWorkout() = Workout(
    id = "free-ride",
    name = "Endurance Ride",
    description = null,
    source = WorkoutSource.MANUAL,
    segments = listOf(
      WorkoutSegment.FreeRide(
        id = "ride",
        durationSec = 3600,
        label = "Rider's choice",
        phase = SegmentPhase.WORK
      )
    )
  )
}
