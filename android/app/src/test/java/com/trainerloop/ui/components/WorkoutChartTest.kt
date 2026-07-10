package com.trainerloop.ui.components

import androidx.compose.ui.graphics.Color
import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.ui.theme.ZoneColors
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutChartTest {

  private val ftp = 200

  private val slate = Color(0xFF64748B)
  private val blue = Color(0xFF3B82F6)
  private val green = Color(0xFF22C55E)
  private val amber = Color(0xFFF59E0B)
  private val orange = Color(0xFFF97316)
  private val red = Color(0xFFEF4444)

  private val segments = listOf(
    WorkoutSegment.FreeRide("warmup", 60, null, SegmentPhase.WARMUP),
    WorkoutSegment.FreeRide("work", 120, null, SegmentPhase.WORK)
  )

  private val bounds = listOf(
    Triple(0, 60, segments[0]),
    Triple(60, 180, segments[1])
  )

  @Test
  fun `below 55 percent is gray`() {
    assertEquals(slate, ZoneColors.forTarget(targetWatts = 100, ftp = ftp, dark = true).fill)
  }

  @Test
  fun `55 to 75 percent is blue`() {
    assertEquals(blue, ZoneColors.forTarget(targetWatts = 120, ftp = ftp, dark = true).fill)
  }

  @Test
  fun `75 to 90 percent is green`() {
    assertEquals(green, ZoneColors.forTarget(targetWatts = 160, ftp = ftp, dark = true).fill)
  }

  @Test
  fun `90 to 105 percent is amber`() {
    assertEquals(amber, ZoneColors.forTarget(targetWatts = 190, ftp = ftp, dark = true).fill)
  }

  @Test
  fun `105 to 120 percent is orange`() {
    assertEquals(orange, ZoneColors.forTarget(targetWatts = 220, ftp = ftp, dark = true).fill)
  }

  @Test
  fun `above 120 percent is red`() {
    assertEquals(red, ZoneColors.forTarget(targetWatts = 260, ftp = ftp, dark = true).fill)
  }

  @Test
  fun `zero ftp falls back to gray without dividing by zero`() {
    assertEquals(slate, ZoneColors.forTarget(targetWatts = 200, ftp = 0, dark = true).fill)
  }

  @Test
  fun `full window covers the total duration`() {
    val window = computeWorkoutChartWindow(
      zoomToCurrent = false,
      totalDurationSec = 180,
      elapsedSec = 90,
      segments = segments,
      bounds = bounds
    )

    assertEquals(0f, window.startSec, 0f)
    assertEquals(180f, window.endSec, 0f)
  }

  @Test
  fun `focus window pads the current segment and clamps to duration`() {
    val window = computeWorkoutChartWindow(
      zoomToCurrent = true,
      totalDurationSec = 180,
      elapsedSec = 90,
      segments = segments,
      bounds = bounds
    )

    assertEquals(40f, window.startSec, 0f)
    assertEquals(180f, window.endSec, 0f)
  }
}
