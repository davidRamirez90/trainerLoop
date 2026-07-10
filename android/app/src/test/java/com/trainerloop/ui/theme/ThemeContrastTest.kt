package com.trainerloop.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class ThemeContrastTest {

  @Test
  fun `light and dark scheme roles meet WCAG contrast`() {
    assertSchemeContrast("light", LightColorScheme)
    assertSchemeContrast("dark", DarkColorScheme)
  }

  @Test
  fun `dark scheme uses the intended surface ladder`() {
    val backgroundLuminance = relativeLuminance(DarkColorScheme.background)
    val cardLuminance = relativeLuminance(DarkColorScheme.surfaceVariant)
    val containerLuminance = relativeLuminance(DarkColorScheme.surfaceContainer)
    val highLuminance = relativeLuminance(DarkColorScheme.surfaceContainerHigh)

    assertTrue(
      "dark background must be darker than standard cards",
      backgroundLuminance < cardLuminance
    )
    assertTrue(
      "dark surfaceContainer must be darker than elevated surfaces",
      containerLuminance < highLuminance
    )
  }

  private fun assertSchemeContrast(name: String, scheme: ColorScheme) {
    assertContrastAtLeast(scheme.onPrimary, scheme.primary, "$name onPrimary/primary")
    assertContrastAtLeast(
      scheme.onPrimaryContainer,
      scheme.primaryContainer,
      "$name onPrimaryContainer/primaryContainer"
    )
    assertContrastAtLeast(
      scheme.onSecondaryContainer,
      scheme.secondaryContainer,
      "$name onSecondaryContainer/secondaryContainer"
    )
    assertContrastAtLeast(scheme.onBackground, scheme.background, "$name onBackground/background")
    assertContrastAtLeast(
      scheme.onSurfaceVariant,
      scheme.surfaceVariant,
      "$name onSurfaceVariant/surfaceVariant"
    )
    assertContrastAtLeast(
      scheme.onTertiaryContainer,
      scheme.tertiaryContainer,
      "$name onTertiaryContainer/tertiaryContainer"
    )
    assertContrastAtLeast(scheme.onError, scheme.error, "$name onError/error")
  }

  private fun assertContrastAtLeast(foreground: Color, background: Color, label: String) {
    val actual = contrastRatio(foreground, background)
    assertTrue("$label was $actual, expected at least 4.5", actual >= 4.5)
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
