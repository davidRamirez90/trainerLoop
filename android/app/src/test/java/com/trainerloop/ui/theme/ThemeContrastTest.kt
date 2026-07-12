package com.trainerloop.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class ThemeContrastTest {

  @Test
  fun `all light and dark Material content roles meet WCAG contrast`() {
    assertSchemeContrast("light", LightColorScheme)
    assertSchemeContrast("dark", DarkColorScheme)
  }

  @Test
  fun `all light and dark semantic content roles meet WCAG contrast`() {
    assertSemanticContrast("light", LightTrainerLoopColors)
    assertSemanticContrast("dark", DarkTrainerLoopColors)
  }

  @Test
  fun `dark elevation ladder rises monotonically`() {
    val ladder = listOf(
      "background" to DarkColorScheme.background,
      "card" to DarkColorScheme.surface,
      "grouped" to DarkColorScheme.surfaceContainer,
      "raised" to DarkColorScheme.surfaceContainerHigh
    )

    ladder.zipWithNext().forEach { (lower, higher) ->
      assertTrue(
        "dark ${lower.first} must be darker than ${higher.first}",
        relativeLuminance(lower.second) < relativeLuminance(higher.second)
      )
    }
  }

  @Test
  fun `plan profile outline reads against chart surfaces in both modes`() {
    listOf(
      Triple("light", LightTrainerLoopColors.chartPlanOutline, LightColorScheme.surface),
      Triple("dark", DarkTrainerLoopColors.chartPlanOutline, DarkColorScheme.surface)
    ).forEach { (mode, outline, surface) ->
      val ratio = contrastRatio(outline, surface)
      assertTrue(
        "$mode chartPlanOutline must reach 3:1 non-text contrast on surface (was $ratio)",
        ratio >= 3.0
      )
    }
  }

  private fun assertSchemeContrast(name: String, scheme: ColorScheme) {
    val pairs = listOf(
      "onPrimary/primary" to (scheme.onPrimary to scheme.primary),
      "onPrimaryContainer/primaryContainer" to
        (scheme.onPrimaryContainer to scheme.primaryContainer),
      "onSecondary/secondary" to (scheme.onSecondary to scheme.secondary),
      "onSecondaryContainer/secondaryContainer" to
        (scheme.onSecondaryContainer to scheme.secondaryContainer),
      "onTertiary/tertiary" to (scheme.onTertiary to scheme.tertiary),
      "onTertiaryContainer/tertiaryContainer" to
        (scheme.onTertiaryContainer to scheme.tertiaryContainer),
      "onBackground/background" to (scheme.onBackground to scheme.background),
      "onSurface/surface" to (scheme.onSurface to scheme.surface),
      "onSurface/surfaceBright" to (scheme.onSurface to scheme.surfaceBright),
      "onSurface/surfaceDim" to (scheme.onSurface to scheme.surfaceDim),
      "onSurface/surfaceContainerLowest" to
        (scheme.onSurface to scheme.surfaceContainerLowest),
      "onSurface/surfaceContainerLow" to
        (scheme.onSurface to scheme.surfaceContainerLow),
      "onSurface/surfaceContainer" to (scheme.onSurface to scheme.surfaceContainer),
      "onSurface/surfaceContainerHigh" to
        (scheme.onSurface to scheme.surfaceContainerHigh),
      "onSurface/surfaceContainerHighest" to
        (scheme.onSurface to scheme.surfaceContainerHighest),
      "onSurfaceVariant/surfaceVariant" to
        (scheme.onSurfaceVariant to scheme.surfaceVariant),
      "inverseOnSurface/inverseSurface" to
        (scheme.inverseOnSurface to scheme.inverseSurface),
      "onError/error" to (scheme.onError to scheme.error),
      "onErrorContainer/errorContainer" to
        (scheme.onErrorContainer to scheme.errorContainer)
    )

    pairs.forEach { (label, pair) ->
      assertContrastAtLeast(pair.first, pair.second, "$name $label")
    }
  }

  private fun assertSemanticContrast(name: String, colors: TrainerLoopColors) {
    val pairs = listOf(
      "onReady/ready" to (colors.onReady to colors.ready),
      "onCoach/coach" to (colors.onCoach to colors.coach),
      "onConnected/connected" to (colors.onConnected to colors.connected),
      "onWarning/warning" to (colors.onWarning to colors.warning),
      "onStale/stale" to (colors.onStale to colors.stale),
      "onHeroAction/heroAction" to (colors.onHeroAction to colors.heroAction)
    )

    pairs.forEach { (label, pair) ->
      assertContrastAtLeast(pair.first, pair.second, "$name $label")
    }
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
