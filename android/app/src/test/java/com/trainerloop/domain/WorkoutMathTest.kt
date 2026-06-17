package com.trainerloop.domain

import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.WorkoutSegment
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutMathTest {
  @Test
  fun `total duration is sum of segments`() {
    val segments = listOf(
      WorkoutSegment.Step(
        id = "s1",
        durationSec = 120,
        label = null,
        phase = SegmentPhase.WARMUP,
        isWork = false,
        targetRange = TargetRange(100, 100)
      ),
      WorkoutSegment.Ramp(
        id = "r1",
        durationSec = 180,
        label = null,
        phase = SegmentPhase.WORK,
        isWork = true,
        startPower = 150,
        endPower = 250
      ),
      WorkoutSegment.FreeRide(
        id = "f1",
        durationSec = 300,
        label = null,
        phase = SegmentPhase.RECOVERY
      )
    )
    assertEquals(600, WorkoutMath.totalDurationSec(segments))
  }

  @Test
  fun `segment index at elapsed time`() {
    val segments = listOf(
      WorkoutSegment.Step(
        id = "s1",
        durationSec = 120,
        label = null,
        phase = SegmentPhase.WARMUP,
        isWork = false,
        targetRange = TargetRange(100, 100)
      ),
      WorkoutSegment.Ramp(
        id = "r1",
        durationSec = 180,
        label = null,
        phase = SegmentPhase.WORK,
        isWork = true,
        startPower = 150,
        endPower = 250
      )
    )
    assertEquals(0, WorkoutMath.segmentIndexAt(segments, 0))
    assertEquals(0, WorkoutMath.segmentIndexAt(segments, 119))
    assertEquals(1, WorkoutMath.segmentIndexAt(segments, 120))
    assertEquals(1, WorkoutMath.segmentIndexAt(segments, 299))
    assertEquals(1, WorkoutMath.segmentIndexAt(segments, 1000))
  }

  @Test
  fun `ramp interpolates halfway`() {
    val segments = listOf(
      WorkoutSegment.Ramp(
        id = "r",
        durationSec = 60,
        label = null,
        phase = SegmentPhase.WORK,
        isWork = true,
        startPower = 100,
        endPower = 200
      )
    )
    assertEquals(TargetRange(150, 150), WorkoutMath.targetRangeAt(segments, 30))
  }

  @Test
  fun `step target range`() {
    val segments = listOf(
      WorkoutSegment.Step(
        id = "s",
        durationSec = 60,
        label = null,
        phase = SegmentPhase.WORK,
        isWork = true,
        targetRange = TargetRange(200, 220)
      )
    )
    assertEquals(TargetRange(200, 220), WorkoutMath.targetRangeAt(segments, 30))
  }

  @Test
  fun `free ride target range is zero`() {
    val segments = listOf(
      WorkoutSegment.FreeRide(
        id = "f",
        durationSec = 60,
        label = null,
        phase = SegmentPhase.RECOVERY
      )
    )
    assertEquals(TargetRange(0, 0), WorkoutMath.targetRangeAt(segments, 30))
  }
}
