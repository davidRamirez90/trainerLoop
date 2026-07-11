package com.trainerloop.ui.components

import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.WorkoutSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneBandsTest {

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
  fun `flat interval collapses to a single band`() {
    val segments = listOf(step("a", 600, 150)) // 75% FTP -> zone 3
    val bands = zoneBands(segments, ftp, winStartSec = 0f, winEndSec = 600f, stepSec = 3f)
    assertEquals(1, bands.size)
    assertEquals(3, bands[0].zone)
    assertEquals(150, bands[0].targetWatts)
    assertEquals(0f, bands[0].startSec)
    assertEquals(600f, bands[0].endSec)
  }

  @Test
  fun `two flat intervals produce two contiguous bands`() {
    val segments = listOf(step("a", 300, 120), step("b", 300, 180))
    val bands = zoneBands(segments, ftp, winStartSec = 0f, winEndSec = 600f, stepSec = 3f)
    assertEquals(2, bands.size)
    assertEquals(bands[0].endSec, bands[1].startSec)
    assertEquals(120, bands[0].targetWatts)
    assertEquals(180, bands[1].targetWatts)
  }

  @Test
  fun `ramp produces contiguous bands with rising targets`() {
    val segments = listOf(
      WorkoutSegment.Ramp(
        id = "r",
        durationSec = 300,
        label = null,
        phase = SegmentPhase.WORK,
        isWork = true,
        startPower = 100,
        endPower = 240
      )
    )
    val bands = zoneBands(segments, ftp, winStartSec = 0f, winEndSec = 300f, stepSec = 3f)
    assertTrue("ramp should produce multiple bands", bands.size > 1)
    // Contiguous: no gaps, no overlaps.
    bands.zipWithNext().forEach { (a, b) -> assertEquals(a.endSec, b.startSec) }
    // Monotonic targets and zones.
    assertTrue(bands.first().targetWatts < bands.last().targetWatts)
    assertTrue(bands.first().zone < bands.last().zone)
    // Full coverage of the window.
    assertEquals(0f, bands.first().startSec)
    assertEquals(300f, bands.last().endSec)
  }

  @Test
  fun `window subset only covers the window`() {
    val segments = listOf(step("a", 600, 150))
    val bands = zoneBands(segments, ftp, winStartSec = 100f, winEndSec = 200f, stepSec = 3f)
    assertEquals(100f, bands.first().startSec)
    assertEquals(200f, bands.last().endSec)
  }

  @Test
  fun `free ride yields no bands`() {
    val segments = listOf(WorkoutSegment.FreeRide("f", 300, null, SegmentPhase.WARMUP))
    val bands = zoneBands(segments, ftp, winStartSec = 0f, winEndSec = 300f, stepSec = 3f)
    assertTrue(bands.isEmpty())
  }
}
