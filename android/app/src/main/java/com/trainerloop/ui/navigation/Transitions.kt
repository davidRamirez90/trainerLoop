package com.trainerloop.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import com.trainerloop.ui.theme.MotionSpec

private const val FadeThroughDurationMillis = 200
private const val ReducedMotionDurationMillis = 150

data class TabFadeThroughTransitions(
  val enter: EnterTransition,
  val exit: ExitTransition
)

fun tabFadeThrough(reducedMotion: Boolean): TabFadeThroughTransitions =
  TabFadeThroughTransitions(
    enter = navigationFadeIn(reducedMotion),
    exit = navigationFadeOut(reducedMotion)
  )

fun sharedAxisXEnter(reducedMotion: Boolean): EnterTransition =
  if (reducedMotion) {
    navigationFadeIn(reducedMotion = true)
  } else {
    slideInHorizontally(
      animationSpec = MotionSpec.defaultSpring(),
      initialOffsetX = { fullWidth -> fullWidth / 4 }
    ) + navigationFadeIn(reducedMotion = false)
  }

fun sharedAxisXExit(reducedMotion: Boolean): ExitTransition =
  if (reducedMotion) {
    navigationFadeOut(reducedMotion = true)
  } else {
    slideOutHorizontally(
      animationSpec = MotionSpec.defaultSpring(),
      targetOffsetX = { fullWidth -> -fullWidth / 4 }
    ) + navigationFadeOut(reducedMotion = false)
  }

fun sharedAxisXPopEnter(reducedMotion: Boolean): EnterTransition =
  if (reducedMotion) {
    navigationFadeIn(reducedMotion = true)
  } else {
    slideInHorizontally(
      animationSpec = MotionSpec.defaultSpring(),
      initialOffsetX = { fullWidth -> -fullWidth / 4 }
    ) + navigationFadeIn(reducedMotion = false)
  }

fun sharedAxisXPopExit(reducedMotion: Boolean): ExitTransition =
  if (reducedMotion) {
    navigationFadeOut(reducedMotion = true)
  } else {
    slideOutHorizontally(
      animationSpec = MotionSpec.defaultSpring(),
      targetOffsetX = { fullWidth -> fullWidth / 4 }
    ) + navigationFadeOut(reducedMotion = false)
  }

fun playerEnter(reducedMotion: Boolean): EnterTransition =
  if (reducedMotion) {
    navigationFadeIn(reducedMotion = true)
  } else {
    slideInVertically(
      animationSpec = MotionSpec.defaultSpring(),
      initialOffsetY = { fullHeight -> fullHeight }
    ) + navigationFadeIn(reducedMotion = false)
  }

fun playerExit(reducedMotion: Boolean): ExitTransition =
  if (reducedMotion) {
    navigationFadeOut(reducedMotion = true)
  } else {
    slideOutVertically(
      animationSpec = MotionSpec.defaultSpring(),
      targetOffsetY = { fullHeight -> fullHeight }
    ) + navigationFadeOut(reducedMotion = false)
  }

private fun navigationFadeIn(reducedMotion: Boolean): EnterTransition =
  fadeIn(
    animationSpec = tween(
      durationMillis = if (reducedMotion) {
        ReducedMotionDurationMillis
      } else {
        FadeThroughDurationMillis
      }
    )
  )

private fun navigationFadeOut(reducedMotion: Boolean): ExitTransition =
  fadeOut(
    animationSpec = tween(
      durationMillis = if (reducedMotion) {
        ReducedMotionDurationMillis
      } else {
        FadeThroughDurationMillis
      }
    )
  )
