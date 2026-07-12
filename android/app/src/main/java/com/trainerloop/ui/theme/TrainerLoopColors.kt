package com.trainerloop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class TrainerLoopColors(
  val ready: Color,
  val onReady: Color,
  val coach: Color,
  val onCoach: Color,
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
  val chartCursor: Color
)

internal val LightTrainerLoopColors = TrainerLoopColors(
  ready = Sun80,
  onReady = DarkBackground,
  coach = Coral80,
  onCoach = DarkBackground,
  connected = Kelp40,
  onConnected = Foam,
  warning = Amber40,
  onWarning = Foam,
  stale = Neutral40,
  onStale = Foam,
  heroAction = DarkBackground,
  onHeroAction = Foam,
  chartPower = Ocean40,
  chartHeartRate = Red40,
  chartCadence = Kelp40,
  chartElevation = Coral40,
  chartGrid = Sand60,
  chartCursor = DarkBackground
)

internal val DarkTrainerLoopColors = TrainerLoopColors(
  ready = Sun20,
  onReady = Foam,
  coach = Coral20,
  onCoach = Foam,
  connected = Kelp20,
  onConnected = Foam,
  warning = Amber20,
  onWarning = Foam,
  stale = Neutral30,
  onStale = Foam,
  heroAction = Foam,
  onHeroAction = DarkBackground,
  chartPower = Sky80,
  chartHeartRate = Red80,
  chartCadence = Kelp80,
  chartElevation = Coral80,
  chartGrid = Neutral40,
  chartCursor = Foam
)

val LocalTrainerLoopColors = staticCompositionLocalOf { LightTrainerLoopColors }

val MaterialTheme.trainerLoopColors: TrainerLoopColors
  @Composable
  @ReadOnlyComposable
  get() = LocalTrainerLoopColors.current
