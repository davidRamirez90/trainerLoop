package com.trainerloop.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import com.trainerloop.ui.theme.MotionSpec
import com.trainerloop.ui.theme.reducedMotionAware

fun Modifier.pressable(
  interactionSource: MutableInteractionSource? = null,
  pressedScale: Float = 0.97f
): Modifier = composed {
  val source = interactionSource ?: remember { MutableInteractionSource() }
  val isPressed by source.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (isPressed) pressedScale else 1f,
    animationSpec = reducedMotionAware(MotionSpec.fastSpring<Float>()),
    label = "pressScale"
  )

  val pressGesture = if (interactionSource == null) {
    Modifier.pointerInput(source) {
      awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val press = PressInteraction.Press(down.position)
        source.emit(press)
        val up = waitForUpOrCancellation()
        source.emit(
          if (up == null) {
            PressInteraction.Cancel(press)
          } else {
            PressInteraction.Release(press)
          }
        )
      }
    }
  } else {
    Modifier
  }

  pressGesture.graphicsLayer {
    scaleX = scale
    scaleY = scale
  }
}
