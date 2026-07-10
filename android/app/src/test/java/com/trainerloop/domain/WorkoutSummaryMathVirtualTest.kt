package com.trainerloop.domain

import com.trainerloop.data.model.TelemetrySample
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutSummaryMathVirtualTest {
  private fun sample(t: Int, dist: Double?, alt: Double?) = TelemetrySample(
    timeSec = t, powerWatts = 200, cadenceRpm = 90, hrBpm = 140,
    virtualDistanceM = dist, virtualAltitudeM = alt
  )

  @Test
  fun `distance is the last recorded cumulative value`() {
    val samples = listOf(sample(1, 10.0, 0.0), sample(2, 20.0, 0.0), sample(3, 5500.0, 0.0))
    assertEquals(5.5, WorkoutSummaryMath.totalDistanceKm(samples), 1e-9)
  }

  @Test
  fun `ascent counts only positive altitude deltas`() {
    val samples = listOf(
      sample(1, 0.0, 0.0), sample(2, 0.0, 10.0), sample(3, 0.0, 4.0), sample(4, 0.0, 12.0)
    )
    assertEquals(18, WorkoutSummaryMath.totalAscentM(samples)) // +10, -6 ignored, +8
  }

  @Test
  fun `sessions without virtual data report zero`() {
    val samples = listOf(sample(1, null, null), sample(2, null, null))
    assertEquals(0.0, WorkoutSummaryMath.totalDistanceKm(samples), 1e-9)
    assertEquals(0, WorkoutSummaryMath.totalAscentM(samples))
  }

  @Test
  fun `zone time uses exact FTP boundaries`() {
    val ftp = 100
    val samples = listOf(55, 75, 90, 105, 120).mapIndexed { index, percent ->
      sample(index + 1, null, null).copy(powerWatts = percent)
    }

    assertEquals(intArrayOf(0, 1, 1, 1, 1, 1).toList(), WorkoutSummaryMath.zoneTimeSec(samples, ftp).toList())
  }

  @Test
  fun `zone time assigns all samples to Z1 when FTP is zero`() {
    val samples = listOf(sample(1, null, null), sample(2, null, null), sample(3, null, null))

    assertEquals(intArrayOf(3, 0, 0, 0, 0, 0).toList(), WorkoutSummaryMath.zoneTimeSec(samples, 0).toList())
  }
}
