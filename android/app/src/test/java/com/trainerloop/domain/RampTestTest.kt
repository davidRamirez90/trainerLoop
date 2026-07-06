package com.trainerloop.domain

import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.data.model.WorkoutSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RampTestTest {

  @Test
  fun `generate builds warmup ramp then 1-minute steps from 100W in 20W increments`() {
    val workout = RampTest.generate(250)

    val warmup = workout.segments.first() as WorkoutSegment.Ramp
    assertEquals(300, warmup.durationSec)
    assertEquals(50, warmup.startPower)
    assertEquals(100, warmup.endPower)
    assertEquals(SegmentPhase.WARMUP, warmup.phase)

    val steps = workout.segments.drop(1).map { it as WorkoutSegment.Step }
    assertEquals(100, steps[0].targetRange.low)
    assertEquals(120, steps[1].targetRange.low)
    assertEquals(140, steps[2].targetRange.low)
    assertTrue(steps.all { it.durationSec == 60 && it.phase == SegmentPhase.WORK })
  }

  @Test
  fun `generate steps reach the ceiling`() {
    // ceiling = max(2.5*250, 250+200) = 625 → last step at 620
    val steps = RampTest.generate(250).segments.drop(1).map { it as WorkoutSegment.Step }
    assertEquals(620, steps.last().targetRange.low)

    // low FTP → ceiling = ftp+200 = 300 → last step at 300
    val lowSteps = RampTest.generate(100).segments.drop(1).map { it as WorkoutSegment.Step }
    assertEquals(300, lowSteps.last().targetRange.low)
  }

  @Test
  fun `computeFtp is 75 percent of best rolling 60s average`() {
    // 60s at 100W then 60s at 300W → best window = 300 → ftp = 225
    val samples = (0 until 60).map { sample(it, 100) } + (60 until 120).map { sample(it, 300) }
    assertEquals(225, RampTest.computeFtp(samples))
  }

  @Test
  fun `computeFtp uses the best window not the last`() {
    // peak in the middle: 60s @ 200, 60s @ 320, 60s @ 100 → best = 320 → 240
    val samples = (0 until 60).map { sample(it, 200) } +
      (60 until 120).map { sample(it, 320) } +
      (120 until 180).map { sample(it, 100) }
    assertEquals(240, RampTest.computeFtp(samples))
  }

  @Test
  fun `computeFtp returns null for fewer than 60 seconds of samples`() {
    assertNull(RampTest.computeFtp(emptyList()))
    assertNull(RampTest.computeFtp((0 until 59).map { sample(it, 200) }))
  }

  @Test
  fun `isRampTest matches only the well-known id`() {
    assertTrue(RampTest.isRampTest(RampTest.WORKOUT_ID))
    assertTrue(!RampTest.isRampTest("sweet-spot"))
  }

  private fun sample(t: Int, watts: Int) =
    TelemetrySample(timeSec = t, powerWatts = watts, cadenceRpm = 90, hrBpm = 140)
}
