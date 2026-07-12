package com.trainerloop.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class ZoneColorsContrastTest {

  private val darkBackground = DarkColorScheme.background
  private val darkCard = DarkColorScheme.surface
  private val lightCard = LightColorScheme.surface

  @Test
  fun `all zone tokens meet contrast requirements`() {
    (1..6).forEach { zone ->
      val dark = ZoneColors.forZone(zone, dark = true)
      assertContrastAtLeast(dark.fill, darkBackground, 3.0, "dark Z$zone fill/background")
      assertContrastAtLeast(dark.fill, darkCard, 3.0, "dark Z$zone fill/card")
      assertContrastAtLeast(dark.line, darkBackground, 3.0, "dark Z$zone line/background")
      assertContrastAtLeast(dark.onFill, dark.fill, 4.5, "dark Z$zone onFill/fill")
      assertEquals(1f, dark.fill.alpha)
      assertEquals(1f, dark.line.alpha)
      assertEquals(1f, dark.onFill.alpha)

      val light = ZoneColors.forZone(zone, dark = false)
      assertContrastAtLeast(light.fill, lightCard, 3.0, "light Z$zone fill/card")
      assertContrastAtLeast(light.line, lightCard, 3.0, "light Z$zone line/card")
      assertContrastAtLeast(light.onFill, light.fill, 4.5, "light Z$zone onFill/fill")
      assertEquals(1f, light.fill.alpha)
      assertEquals(1f, light.line.alpha)
      assertEquals(1f, light.onFill.alpha)
    }
  }

  @Test
  fun `zone index follows FTP bands`() {
    assertEquals(1, ZoneColors.zoneIndex(54, 100))
    assertEquals(2, ZoneColors.zoneIndex(55, 100))
    assertEquals(2, ZoneColors.zoneIndex(74, 100))
    assertEquals(3, ZoneColors.zoneIndex(75, 100))
    assertEquals(3, ZoneColors.zoneIndex(89, 100))
    assertEquals(4, ZoneColors.zoneIndex(90, 100))
    assertEquals(4, ZoneColors.zoneIndex(104, 100))
    assertEquals(5, ZoneColors.zoneIndex(105, 100))
    assertEquals(5, ZoneColors.zoneIndex(119, 100))
    assertEquals(6, ZoneColors.zoneIndex(120, 100))
    assertEquals(1, ZoneColors.zoneIndex(200, 0))
    assertEquals(1, ZoneColors.zoneIndex(200, -1))
  }

  private fun assertContrastAtLeast(foreground: Color, background: Color, minimum: Double, label: String) {
    val actual = contrastRatio(foreground, background)
    assertTrue("$label was $actual, expected at least $minimum", actual >= minimum)
  }

  private fun contrastRatio(first: Color, second: Color): Double {
    val firstLuminance = relativeLuminance(first)
    val secondLuminance = relativeLuminance(second)
    val lighter = maxOf(firstLuminance, secondLuminance)
    val darker = minOf(firstLuminance, secondLuminance)
    return (lighter + 0.05) / (darker + 0.05)
  }

  private fun relativeLuminance(color: Color): Double {
    fun linearize(channel: Float): Double {
      val value = channel.toDouble()
      return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }

    return 0.2126 * linearize(color.red) +
      0.7152 * linearize(color.green) +
      0.0722 * linearize(color.blue)
  }
}
