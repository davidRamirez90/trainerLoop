package com.trainerloop.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.trainerloop.ui.theme.TrainerLoopTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MetricTileTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private fun stateDescriptionMatcher() = SemanticsMatcher("has stateDescription") { node ->
    node.config.getOrNull(SemanticsProperties.StateDescription) != null
  }

  @Test
  fun unavailable_rendersEmDash() {
    composeTestRule.setContent {
      TrainerLoopTheme {
        MetricTile(
          label = "Power",
          value = "250",
          unit = "W",
          state = MetricTileState.Unavailable
        )
      }
    }

    composeTestRule.onNodeWithText("—").assertExists()
  }

  @Test
  fun unavailable_hasUnavailableStateDescription() {
    composeTestRule.setContent {
      TrainerLoopTheme {
        MetricTile(
          label = "Power",
          value = "250",
          unit = "W",
          modifier = Modifier.testTag("metric-tile"),
          state = MetricTileState.Unavailable
        )
      }
    }

    composeTestRule.onNodeWithTag("metric-tile").assert(
      SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Unavailable")
    )
  }

  @Test
  fun stale_hasStaleStateDescription() {
    composeTestRule.setContent {
      TrainerLoopTheme {
        MetricTile(
          label = "Power",
          value = "250",
          unit = "W",
          modifier = Modifier.testTag("metric-tile"),
          state = MetricTileState.Stale
        )
      }
    }

    composeTestRule.onNodeWithTag("metric-tile").assert(
      SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Stale")
    )
  }

  @Test
  fun available_hasNoStateDescription() {
    composeTestRule.setContent {
      TrainerLoopTheme {
        MetricTile(
          label = "Power",
          value = "250",
          unit = "W",
          modifier = Modifier.testTag("metric-tile"),
          state = MetricTileState.Available
        )
      }
    }

    composeTestRule.onNodeWithTag("metric-tile").assert(stateDescriptionMatcher().not())
  }

  @Test
  fun geometry_identicalAcrossStates() {
    // All three states use the same TrainerLoopCard scaffolding and text
    // slots (label/value/unit), so a fixed-size tile should report the same
    // bounds regardless of state — only color and text content vary.
    composeTestRule.setContent {
      TrainerLoopTheme {
        MetricTile(
          label = "Power",
          value = "250",
          unit = "W",
          modifier = Modifier.size(120.dp).testTag("available-tile"),
          state = MetricTileState.Available
        )
      }
    }
    val availableWidth = composeTestRule.onNodeWithTag("available-tile")
      .fetchSemanticsNode().size.width

    composeTestRule.setContent {
      TrainerLoopTheme {
        MetricTile(
          label = "Power",
          value = "250",
          unit = "W",
          modifier = Modifier.size(120.dp).testTag("unavailable-tile"),
          state = MetricTileState.Unavailable
        )
      }
    }
    val unavailableWidth = composeTestRule.onNodeWithTag("unavailable-tile")
      .fetchSemanticsNode().size.width

    assertEquals(availableWidth, unavailableWidth)
  }
}
