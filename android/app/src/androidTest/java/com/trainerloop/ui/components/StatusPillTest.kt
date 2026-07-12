package com.trainerloop.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.trainerloop.ui.theme.TrainerLoopTheme
import org.junit.Rule
import org.junit.Test

/**
 * StatusPill must never rely on hue alone: each state carries a distinct,
 * human-readable label that a screen reader (and colorblind users) can
 * consume regardless of the container color.
 */
class StatusPillTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private fun setPill(state: StatusPillState, label: String, darkTheme: Boolean = false) {
    composeTestRule.setContent {
      TrainerLoopTheme(darkTheme = darkTheme) {
        StatusPill(state = state, label = label, icon = Icons.Default.Bluetooth)
      }
    }
  }

  @Test
  fun connected_rendersLabelText() {
    setPill(StatusPillState.Connected, "Connected")
    composeTestRule.onNodeWithText("Connected").assertExists()
  }

  @Test
  fun scanning_rendersLabelText() {
    setPill(StatusPillState.Scanning, "Scanning")
    composeTestRule.onNodeWithText("Scanning").assertExists()
  }

  @Test
  fun warning_rendersLabelText() {
    setPill(StatusPillState.Warning, "Warning")
    composeTestRule.onNodeWithText("Warning").assertExists()
  }

  @Test
  fun reconnecting_rendersLabelText() {
    setPill(StatusPillState.Reconnecting, "Reconnecting")
    composeTestRule.onNodeWithText("Reconnecting").assertExists()
  }

  @Test
  fun unavailable_rendersLabelText() {
    setPill(StatusPillState.Unavailable, "Unavailable")
    composeTestRule.onNodeWithText("Unavailable").assertExists()
  }

  @Test
  fun success_rendersLabelText() {
    setPill(StatusPillState.Success, "Success")
    composeTestRule.onNodeWithText("Success").assertExists()
  }

  @Test
  fun connected_rendersLabelText_darkTheme() {
    setPill(StatusPillState.Connected, "Connected", darkTheme = true)
    composeTestRule.onNodeWithText("Connected").assertExists()
  }

  @Test
  fun warning_rendersLabelText_darkTheme() {
    setPill(StatusPillState.Warning, "Warning", darkTheme = true)
    composeTestRule.onNodeWithText("Warning").assertExists()
  }
}
