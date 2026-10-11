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
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.staticCompositionLocalOf

// Focus roles cover Material components, dialogs, sheets, and system chrome.
internal val LightColorScheme = lightColorScheme(
  primary = FocusForest,
  onPrimary = Color.White,
  primaryContainer = FocusSage,
  onPrimaryContainer = FocusForest,
  inversePrimary = FocusSage,
  secondary = Color(0xFF42634F),
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFE5ECD9),
  onSecondaryContainer = FocusInk,
  tertiary = Color(0xFF775E39),
  onTertiary = Color.White,
  tertiaryContainer = Color(0xFFF0E4CD),
  onTertiaryContainer = Color(0xFF392D1D),
  background = FocusPaper,
  onBackground = FocusInk,
  surface = Color.White,
  onSurface = FocusInk,
  surfaceVariant = Color(0xFFEBEEE4),
  onSurfaceVariant = Color(0xFF526157),
  surfaceTint = FocusForest,
  inverseSurface = FocusForest,
  inverseOnSurface = Color.White,
  error = Red40,
  onError = Color.White,
  errorContainer = Red90,
  onErrorContainer = Red20,
  outline = Color(0xFF738175),
  outlineVariant = Color(0xFFCFD8C9),
  scrim = FocusNight,
  surfaceBright = Color.White,
  surfaceContainer = Color(0xFFEBEEE4),
  surfaceContainerHigh = Color(0xFFE5EAD9),
  surfaceContainerHighest = Color(0xFFDEE5D5),
  surfaceContainerLow = Color(0xFFF0F2E9),
  surfaceContainerLowest = Color.White,
  surfaceDim = Color(0xFFE8ECE0)
)

internal val DarkColorScheme = darkColorScheme(
  primary = FocusSage,
  onPrimary = FocusForest,
  primaryContainer = Color(0xFF294A37),
  onPrimaryContainer = FocusSage,
  inversePrimary = FocusForest,
  secondary = Color(0xFFB8D2BD),
  onSecondary = FocusNight,
  secondaryContainer = Color(0xFF30473A),
  onSecondaryContainer = Color(0xFFE5ECD9),
  tertiary = Color(0xFFE1C89F),
  onTertiary = Color(0xFF392D1D),
  tertiaryContainer = Color(0xFF4C3D27),
  onTertiaryContainer = Color(0xFFF0E4CD),
  background = FocusNight,
  onBackground = FocusPaper,
  surface = Color(0xFF19281F),
  onSurface = FocusPaper,
  surfaceVariant = Color(0xFF223329),
  onSurfaceVariant = Color(0xFFBAC8BB),
  surfaceTint = FocusSage,
  inverseSurface = FocusPaper,
  inverseOnSurface = FocusInk,
  error = Red80,
  onError = Red20,
  errorContainer = Red20,
  onErrorContainer = Red90,
  outline = Color(0xFF8E9C8D),
  outlineVariant = Color(0xFF43574A),
  scrim = FocusNight,
  surfaceBright = Color(0xFF3A4E40),
  surfaceContainer = Color(0xFF223329),
  surfaceContainerHigh = Color(0xFF2C4032),
  surfaceContainerHighest = Color(0xFF3A4E40),
  surfaceContainerLow = Color(0xFF19281F),
  surfaceContainerLowest = FocusNight,
  surfaceDim = FocusNight
)

private val TrainerLoopShapes = Shapes(
  extraSmall = RoundedCornerShape(8.dp),
  small = RoundedCornerShape(12.dp),
  medium = RoundedCornerShape(24.dp),
  large = RoundedCornerShape(24.dp),
  extraLarge = RoundedCornerShape(28.dp)
)

val LocalTrainerLoopDarkTheme = staticCompositionLocalOf { false }

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
    LocalTrainerLoopDarkTheme provides darkTheme,
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
