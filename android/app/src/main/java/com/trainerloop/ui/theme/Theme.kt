package com.trainerloop.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// Full role coverage so no color falls back to the Material baseline palette.
internal val LightColorScheme = lightColorScheme(
  primary = Ocean40,
  onPrimary = Foam,
  primaryContainer = Sky80,
  onPrimaryContainer = DarkBackground,
  inversePrimary = Sky80,
  secondary = Sand40,
  onSecondary = Foam,
  secondaryContainer = Sky80,
  onSecondaryContainer = DarkBackground,
  tertiary = Coral40,
  onTertiary = Foam,
  tertiaryContainer = Coral90,
  onTertiaryContainer = DarkBackground,
  background = WarmOffWhite,
  onBackground = DarkBackground,
  surface = Foam,
  onSurface = DarkBackground,
  surfaceVariant = PaleSand,
  onSurfaceVariant = Neutral30,
  surfaceTint = Ocean40,
  inverseSurface = DarkRaised,
  inverseOnSurface = Foam,
  error = Red40,
  onError = Foam,
  errorContainer = Red90,
  onErrorContainer = Red20,
  outline = Neutral40,
  outlineVariant = Sand60,
  scrim = DarkBackground,
  surfaceBright = Foam,
  surfaceContainer = PaleSand,
  surfaceContainerHigh = Sand80,
  surfaceContainerHighest = Sand60,
  surfaceContainerLow = Sand95,
  surfaceContainerLowest = Foam,
  surfaceDim = Sand90
)

internal val DarkColorScheme = darkColorScheme(
  primary = Sky80,
  onPrimary = DarkBackground,
  primaryContainer = Ocean20,
  onPrimaryContainer = Foam,
  inversePrimary = Ocean40,
  secondary = Sand80,
  onSecondary = DarkBackground,
  secondaryContainer = Sky80,
  onSecondaryContainer = DarkBackground,
  tertiary = Coral80,
  onTertiary = DarkBackground,
  tertiaryContainer = Coral20,
  onTertiaryContainer = Foam,
  background = DarkBackground,
  onBackground = Foam,
  surface = DarkCard,
  onSurface = Foam,
  surfaceVariant = DarkGrouped,
  onSurfaceVariant = Neutral80,
  surfaceTint = Sky80,
  inverseSurface = Foam,
  inverseOnSurface = DarkBackground,
  error = Red80,
  onError = DarkBackground,
  errorContainer = Red20,
  onErrorContainer = Red90,
  outline = Neutral60,
  outlineVariant = Neutral40,
  scrim = DarkBackground,
  surfaceBright = DarkRaisedHigh,
  surfaceContainer = DarkGrouped,
  surfaceContainerHigh = DarkRaised,
  surfaceContainerHighest = DarkRaisedHigh,
  surfaceContainerLow = DarkCard,
  surfaceContainerLowest = DarkBackground,
  surfaceDim = DarkBackground
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
  val trainerLoopColors = if (darkTheme) DarkTrainerLoopColors else LightTrainerLoopColors
  val context = LocalContext.current
  val reducedMotion = remember(context) {
    Settings.Global.getFloat(
      context.contentResolver,
      Settings.Global.ANIMATOR_DURATION_SCALE,
      1f
    ) == 0f
  }

  CompositionLocalProvider(
    LocalReducedMotion provides reducedMotion,
    LocalTrainerLoopColors provides trainerLoopColors
  ) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      shapes = TrainerLoopShapes,
      content = content
    )
  }
}
