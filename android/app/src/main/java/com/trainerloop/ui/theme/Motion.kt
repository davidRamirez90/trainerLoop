package com.trainerloop.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

object MotionSpec {
  // UI-initiated changes: no bounce, ~Apple response 0.35
  val default: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 300f)

  // After a user flick/drag only (pager settle, sheet dismiss)
  val momentum: SpringSpec<Float> = spring(dampingRatio = 0.8f, stiffness = 300f)

  // Instant-feel feedback (press-down states)
  val fast: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 700f)

  // Short fades used by navigation and metric transitions.
  val fadeThrough: TweenSpec<Float> = tween(durationMillis = 200)
  val reducedMotionFade: TweenSpec<Float> = tween(durationMillis = 150)

  fun <T> defaultSpring(): SpringSpec<T> =
    spring(dampingRatio = 1f, stiffness = 300f)

  fun <T> momentumSpring(): SpringSpec<T> =
    spring(dampingRatio = 0.8f, stiffness = 300f)

  fun <T> fastSpring(): SpringSpec<T> =
    spring(dampingRatio = 1f, stiffness = 700f)
}

val LocalReducedMotion: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

fun <T> resolveSpec(
  reduced: Boolean,
  spec: FiniteAnimationSpec<T>
): FiniteAnimationSpec<T> = if (reduced) snap() else spec

@Composable
fun <T> reducedMotionAware(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
  resolveSpec(LocalReducedMotion.current, spec)
