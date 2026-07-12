package com.trainerloop.ui.components

import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.WorkoutSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanProfileGeometryTest {

  private val ftp = 200

  private fun step(id: String, durationSec: Int, watts: Int) = WorkoutSegment.Step(
    id = id,
    durationSec = durationSec,
    label = null,
    phase = SegmentPhase.WORK,
    isWork = true,
    targetRange = TargetRange(watts, watts)
  )

  @Test
  fun `two steps produce one run tracing a staircase`() {
    val bands = zoneBands(
      listOf(step("a", 300, 120), step("b", 300, 180)),
      ftp, winStartSec = 0f, winEndSec = 600f, stepSec = 3f
    )
    val runs = planProfileRuns(bands)
    assertEquals(1, runs.size)
    val run = runs.single()
    // Staircase: (0,120) (300,120) (300,180) (600,180)
    assertEquals(PlanProfilePoint(0f, 120), run.first())
    assertEquals(PlanProfilePoint(600f, 180), run.last())
    assertTrue(run.contains(PlanProfilePoint(300f, 120)))
    assertTrue(run.contains(PlanProfilePoint(300f, 180)))
    // Time never decreases (steps are vertical, never diagonal-backward).
    run.zipWithNext().forEach { (a, b) -> assertTrue(a.timeSec <= b.timeSec) }
  }

  @Test
  fun `free ride in the middle splits the outline into two runs`() {
    val bands = zoneBands(
      listOf(
        step("a", 300, 150),
        WorkoutSegment.FreeRide("f", 300, null, SegmentPhase.WORK),
        step("b", 300, 200)
      ),
      ftp, winStartSec = 0f, winEndSec = 900f, stepSec = 3f
    )
    val runs = planProfileRuns(bands)
    assertEquals(2, runs.size)
    assertEquals(300f, runs[0].last().timeSec)
    assertEquals(600f, runs[1].first().timeSec)
  }

  @Test
  fun `ramp produces a single monotonic staircase run`() {
    val bands = zoneBands(
      listOf(
        WorkoutSegment.Ramp(
          id = "r", durationSec = 300, label = null,
          phase = SegmentPhase.WORK, isWork = true,
          startPower = 100, endPower = 240
        )
      ),
      ftp, winStartSec = 0f, winEndSec = 300f, stepSec = 3f
    )
    val runs = planProfileRuns(bands)
    assertEquals(1, runs.size)
    val run = runs.single()
    assertTrue(run.size > 2)
    run.zipWithNext().forEach { (a, b) ->
      assertTrue(a.timeSec <= b.timeSec)
      assertTrue(a.watts <= b.watts)
    }
  }

  @Test
  fun `empty bands produce no runs and zero peak`() {
    assertTrue(planProfileRuns(emptyList()).isEmpty())
    assertEquals(0, planPeakWatts(emptyList()))
  }

  @Test
  fun `peak watts is the highest band target`() {
    val bands = zoneBands(
      listOf(step("a", 300, 150), step("b", 60, 420)),
      ftp, winStartSec = 0f, winEndSec = 360f, stepSec = 3f
    )
    assertEquals(420, planPeakWatts(bands))
  }
}
