package com.trainerloop.ui.haptics

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/** Semantic haptic cues used by the workout UI. */
object Haptics {
  fun intervalChange(view: View) {
    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    view.postDelayed(
      { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) },
      DOUBLE_TICK_DELAY_MS
    )
  }

  fun countdownTick(view: View) {
    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
  }

  fun workoutComplete(view: View) {
    performConfirm(view)
    view.postDelayed(
      { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) },
      COMPLETE_DELAY_MS
    )
  }

  fun ergToggle(view: View) {
    performConfirm(view)
  }

  fun biasDetent(view: View) {
    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
  }

  private fun performConfirm(view: View) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    } else {
      view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
  }

  private const val DOUBLE_TICK_DELAY_MS = 60L
  private const val COMPLETE_DELAY_MS = 100L
}
