package com.trainerloop.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutChartTest {

  private val ftp = 200

  private val gray = Color(0xFF9CA3AF)
  private val blue = Color(0xFF60A5FA)
  private val green = Color(0xFF4ADE80)
  private val amber = Color(0xFFFBBF24)
  private val orange = Color(0xFFFB923C)
  private val red = Color(0xFFF87171)

  @Test
  fun `below 55 percent is gray`() {
    assertEquals(gray.copy(alpha = 0.55f), zoneColor(targetWatts = 100, ftp = ftp))
  }

  @Test
  fun `55 to 75 percent is blue`() {
    assertEquals(blue.copy(alpha = 0.55f), zoneColor(targetWatts = 120, ftp = ftp))
  }

  @Test
  fun `75 to 90 percent is green`() {
    assertEquals(green.copy(alpha = 0.55f), zoneColor(targetWatts = 160, ftp = ftp))
  }

  @Test
  fun `90 to 105 percent is amber`() {
    assertEquals(amber.copy(alpha = 0.55f), zoneColor(targetWatts = 190, ftp = ftp))
  }

  @Test
  fun `105 to 120 percent is orange`() {
    assertEquals(orange.copy(alpha = 0.55f), zoneColor(targetWatts = 220, ftp = ftp))
  }

  @Test
  fun `above 120 percent is red`() {
    assertEquals(red.copy(alpha = 0.55f), zoneColor(targetWatts = 260, ftp = ftp))
  }

  @Test
  fun `zero ftp falls back to gray without dividing by zero`() {
    assertEquals(gray.copy(alpha = 0.55f), zoneColor(targetWatts = 200, ftp = 0))
  }
}
