package com.trainerloop.ui.coach

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.trainerloop.data.model.CoachAction
import com.trainerloop.data.model.CoachSuggestion
import com.trainerloop.data.model.SuggestionStatus
import com.trainerloop.domain.coach.FeedbackCategory
import com.trainerloop.domain.coach.FeedbackItem
import com.trainerloop.ui.theme.TrainerLoopTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CoachFeedbackAreaTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun pendingSuggestion_staysVisibleWithLiveFeedback_andRoutesActions() {
    val suggestion = CoachSuggestion(
      id = "suggestion-1",
      action = CoachAction.AdjustIntensityDown(percent = 5),
      message = "Decrease intensity by 5%.",
      rationale = "Fatigue indicators suggest reducing intensity.",
      segmentIndex = 1,
      status = SuggestionStatus.PENDING
    )
    var acceptedId: String? = null
    var rejectedId: String? = null

    composeTestRule.setContent {
      TrainerLoopTheme {
        CoachFeedbackArea(
          pendingSuggestion = suggestion,
          liveFeedback = FeedbackItem(
            id = "feedback-1",
            timestampSec = 30,
            category = FeedbackCategory.PACING,
            severity = 1,
            message = "Settle into the target.",
            ruleId = "pacing-under"
          ),
          onAcceptSuggestion = { acceptedId = it },
          onRejectSuggestion = { rejectedId = it }
        )
      }
    }

    composeTestRule.onNodeWithText("Coach Suggestion").assertExists()
    composeTestRule.onNodeWithText(suggestion.message).assertExists()
    composeTestRule.onNodeWithText("Settle into the target.").assertExists()
    composeTestRule.onNodeWithText("Accept").performClick()
    composeTestRule.onNodeWithText("Reject").performClick()

    assertEquals(suggestion.id, acceptedId)
    assertEquals(suggestion.id, rejectedId)
    composeTestRule.onNodeWithText(suggestion.message).assertExists()
  }
}
