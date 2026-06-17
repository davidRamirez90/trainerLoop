package com.trainerloop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
  primary = Green40,
  onPrimary = Neutral95,
  primaryContainer = Green80,
  secondary = Blue40,
  onSecondary = Neutral95,
  secondaryContainer = Blue80,
  tertiary = Amber40,
  background = Neutral95,
  onBackground = Neutral10,
  surface = Neutral95,
  onSurface = Neutral10,
  surfaceVariant = Neutral90,
  onSurfaceVariant = Neutral30,
  error = Red40,
  onError = Neutral95,
  errorContainer = Red80
)

private val DarkColorScheme = darkColorScheme(
  primary = Green60,
  onPrimary = Neutral10,
  primaryContainer = Green20,
  secondary = Blue80,
  onSecondary = Neutral10,
  secondaryContainer = Blue20,
  tertiary = Amber80,
  background = Neutral10,
  onBackground = Neutral95,
  surface = Neutral10,
  onSurface = Neutral95,
  surfaceVariant = Neutral30,
  onSurfaceVariant = Neutral90,
  error = Red80,
  onError = Neutral10,
  errorContainer = Red40
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
    content = content
  )
}
