package com.trainerloop.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.trainerloop.ui.theme.TrainerLoopTheme
import org.junit.Rule
import org.junit.Test

class MessagingTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun emptyState_rendersTitleAndBody() {
    composeTestRule.setContent {
      TrainerLoopTheme {
        EmptyState(
          icon = Icons.Default.Info,
          title = "No workouts yet",
          body = "Import a workout or start a free ride to get going."
        )
      }
    }

    composeTestRule.onNodeWithText("No workouts yet").assertExists()
    composeTestRule.onNodeWithText("Import a workout or start a free ride to get going.")
      .assertExists()
  }

  @Test
  fun emptyState_rendersTitleAndBody_darkTheme() {
    composeTestRule.setContent {
      TrainerLoopTheme(darkTheme = true) {
        EmptyState(
          icon = Icons.Default.Info,
          title = "No workouts yet",
          body = "Import a workout or start a free ride to get going."
        )
      }
    }

    composeTestRule.onNodeWithText("No workouts yet").assertExists()
    composeTestRule.onNodeWithText("Import a workout or start a free ride to get going.")
      .assertExists()
  }

  @Test
  fun inlineMessage_info_rendersText() {
    composeTestRule.setContent {
      TrainerLoopTheme {
        InlineMessage(severity = MessageSeverity.Info, text = "Syncing your last ride.")
      }
    }

    composeTestRule.onNodeWithText("Syncing your last ride.").assertExists()
  }

  @Test
  fun inlineMessage_warning_rendersText() {
    composeTestRule.setContent {
      TrainerLoopTheme {
        InlineMessage(severity = MessageSeverity.Warning, text = "Heart rate sensor disconnected.")
      }
    }

    composeTestRule.onNodeWithText("Heart rate sensor disconnected.").assertExists()
  }

  @Test
  fun inlineMessage_error_rendersText() {
    composeTestRule.setContent {
      TrainerLoopTheme {
        InlineMessage(severity = MessageSeverity.Error, text = "Unable to save session.")
      }
    }

    composeTestRule.onNodeWithText("Unable to save session.").assertExists()
  }
}
