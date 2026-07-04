package com.trainerloop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// Full role coverage so nothing falls back to the purple Material baseline.
private val LightColorScheme = lightColorScheme(
  primary = Green40,
  onPrimary = Neutral99,
  primaryContainer = Green95,
  onPrimaryContainer = Green10,
  secondary = Blue40,
  onSecondary = Neutral99,
  // Green pills for selected chips / nav indicator (brand over baseline blue)
  secondaryContainer = Green95,
  onSecondaryContainer = Green10,
  tertiary = Amber40,
  onTertiary = Neutral99,
  tertiaryContainer = Amber90,
  onTertiaryContainer = Amber20,
  background = Neutral95,
  onBackground = Neutral10,
  surface = Neutral95,
  onSurface = Neutral10,
  // White cards on the soft gray background for depth
  surfaceVariant = Neutral99,
  onSurfaceVariant = Neutral30,
  surfaceContainerLowest = Neutral99,
  surfaceContainerLow = Neutral99,
  surfaceContainer = Neutral99,
  surfaceContainerHigh = Neutral90,
  surfaceContainerHighest = Neutral90,
  outline = Neutral40,
  outlineVariant = Neutral85,
  error = Red40,
  onError = Neutral99,
  errorContainer = Red90,
  onErrorContainer = Red20
)

private val DarkColorScheme = darkColorScheme(
  primary = Green60,
  onPrimary = Neutral10,
  primaryContainer = Green20,
  onPrimaryContainer = Green95,
  secondary = Blue80,
  onSecondary = Neutral10,
  secondaryContainer = Green20,
  onSecondaryContainer = Green95,
  tertiary = Amber80,
  onTertiary = Neutral10,
  tertiaryContainer = Amber20,
  onTertiaryContainer = Amber90,
  background = Neutral10,
  onBackground = Neutral95,
  surface = Neutral10,
  onSurface = Neutral95,
  surfaceVariant = Neutral20,
  onSurfaceVariant = Neutral90,
  surfaceContainerLowest = Neutral10,
  surfaceContainerLow = Neutral12,
  surfaceContainer = Neutral15,
  surfaceContainerHigh = Neutral20,
  surfaceContainerHighest = Neutral20,
  outline = Neutral40,
  outlineVariant = Neutral30,
  error = Red80,
  onError = Neutral10,
  errorContainer = Red20,
  onErrorContainer = Red90
)

private val TrainerLoopShapes = Shapes(
  extraSmall = RoundedCornerShape(8.dp),
  small = RoundedCornerShape(12.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(20.dp),
  extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun TrainerLoopTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    shapes = TrainerLoopShapes,
    content = content
  )
}
