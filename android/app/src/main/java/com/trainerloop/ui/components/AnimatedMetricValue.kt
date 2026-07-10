package com.trainerloop.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material3.Text
import com.trainerloop.ui.theme.LocalReducedMotion
import com.trainerloop.ui.theme.MotionSpec
import com.trainerloop.ui.theme.reducedMotionAware

@Composable
fun AnimatedMetricValue(
  value: Int,
  showDashWhenZero: Boolean,
  style: TextStyle,
  color: Color,
  modifier: Modifier = Modifier
) {
  val reducedMotion = LocalReducedMotion.current
  val springSpec = reducedMotionAware(MotionSpec.defaultSpring<IntOffset>())

  AnimatedContent(
    targetState = value,
    transitionSpec = {
      metricValueTransition(
        increasing = targetState > initialState,
        reducedMotion = reducedMotion,
        springSpec = springSpec
      )
    },
    modifier = modifier,
    contentAlignment = Alignment.Center,
    label = "Metric value"
  ) { targetValue ->
    Text(
      text = if (showDashWhenZero && targetValue == 0) "—" else targetValue.toString(),
      style = style,
      color = color
    )
  }
}

private fun AnimatedContentTransitionScope<Int>.metricValueTransition(
  increasing: Boolean,
  reducedMotion: Boolean,
  springSpec: FiniteAnimationSpec<IntOffset>
): ContentTransform {
  if (reducedMotion) {
    return fadeIn(animationSpec = tween(150)) togetherWith
      fadeOut(animationSpec = tween(150))
  }

  val enterOffset = if (increasing) 1 else -1
  val exitOffset = -enterOffset
  val contentTransform = (
    slideInVertically(
      animationSpec = springSpec,
      initialOffsetY = { fullHeight -> fullHeight * enterOffset }
    ) + fadeIn(animationSpec = tween(150))
    ) togetherWith (
    slideOutVertically(
      animationSpec = springSpec,
      targetOffsetY = { fullHeight -> fullHeight * exitOffset }
    ) + fadeOut(animationSpec = tween(150))
    )
  return contentTransform.using(null)
}
