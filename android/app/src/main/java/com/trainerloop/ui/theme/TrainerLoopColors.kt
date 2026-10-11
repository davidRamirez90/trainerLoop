package com.trainerloop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class TrainerLoopColors(
  val heroSurface: Color,
  val onHeroSurface: Color,
  val heroSecondary: Color,
  val ready: Color,
  val onReady: Color,
  val assessment: Color,
  val onAssessment: Color,
  val connected: Color,
  val onConnected: Color,
  val warning: Color,
  val onWarning: Color,
  val stale: Color,
  val onStale: Color,
  val heroAction: Color,
  val onHeroAction: Color,
  val chartPower: Color,
  val chartHeartRate: Color,
  val chartCadence: Color,
  val chartElevation: Color,
  val chartGrid: Color,
  val chartCursor: Color,
  val chartPlanOutline: Color,
  val chartPlanFill: Color
)

internal val LightTrainerLoopColors = TrainerLoopColors(
  heroSurface = FocusForest,
  onHeroSurface = Color.White,
  heroSecondary = FocusHeroText,
  ready = FocusSage,
  onReady = FocusForest,
  assessment = Color(0xFFEBEEE4),
  onAssessment = FocusForest,
  connected = FocusForest,
  onConnected = Color.White,
  warning = Amber40,
  onWarning = Color.White,
  stale = Color(0xFF526157),
  onStale = Color.White,
  heroAction = FocusSage,
  onHeroAction = FocusForest,
  chartPower = FocusForest,
  chartHeartRate = Red40,
  chartCadence = Color(0xFF736126),
  chartElevation = Color(0xFF805D37),
  chartGrid = Color(0xFFCFD8C9),
  chartCursor = FocusInk,
  chartPlanOutline = Color(0xFF42634F),
  chartPlanFill = Color(0xFF6E9A83)
)

internal val DarkTrainerLoopColors = TrainerLoopColors(
  heroSurface = FocusForest,
  onHeroSurface = Color.White,
  heroSecondary = FocusHeroText,
  ready = FocusSage,
  onReady = FocusForest,
  assessment = Color(0xFF30473A),
  onAssessment = FocusSage,
  connected = FocusSage,
  onConnected = FocusForest,
  warning = Amber80,
  onWarning = FocusNight,
  stale = Color(0xFFBAC8BB),
  onStale = FocusNight,
  heroAction = FocusSage,
  onHeroAction = FocusForest,
  chartPower = FocusSage,
  chartHeartRate = Red80,
  chartCadence = Color(0xFFD7C78D),
  chartElevation = Color(0xFFE1C89F),
  chartGrid = Color(0xFF43574A),
  chartCursor = FocusPaper,
  chartPlanOutline = Color(0xFFB8D2BD),
  chartPlanFill = Color(0xFF6E9A83)
)

val LocalTrainerLoopColors = staticCompositionLocalOf { LightTrainerLoopColors }

val MaterialTheme.trainerLoopColors: TrainerLoopColors
  @Composable
  @ReadOnlyComposable
  get() = LocalTrainerLoopColors.current
