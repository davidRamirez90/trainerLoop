package com.trainerloop.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.trainerloop.ui.theme.TrainerLoopTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Regression coverage for the four-tab bottom navigation bar (see
 * TrainerLoopApp.kt). Reconstructed here as a standalone composable using
 * the same [Screen.bottomTabs] source of truth, so it can be exercised
 * without a NavHostController.
 */
class BottomNavigationBarTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private fun bottomTabIcon(screen: Screen): ImageVector = when (screen) {
    Screen.Home -> Icons.Default.Home
    Screen.Workouts -> Icons.AutoMirrored.Filled.DirectionsBike
    Screen.History -> Icons.Default.History
    Screen.Profile -> Icons.Default.Person
    else -> Icons.AutoMirrored.Filled.DirectionsBike
  }

  private fun bottomTabLabel(screen: Screen): String = when (screen) {
    Screen.Home -> "Home"
    Screen.Workouts -> "Workouts"
    Screen.History -> "History"
    Screen.Profile -> "Profile"
    else -> ""
  }

  private fun setBottomNavContent(darkTheme: Boolean = false) {
    composeTestRule.setContent {
      TrainerLoopTheme(darkTheme = darkTheme) {
        var selectedRoute by remember { mutableStateOf(Screen.Home.route) }
        NavigationBar(
          containerColor = MaterialTheme.colorScheme.surface,
          contentColor = MaterialTheme.colorScheme.onSurface
        ) {
          Screen.bottomTabs.forEach { screen ->
            val selected = selectedRoute == screen.route
            NavigationBarItem(
              selected = selected,
              onClick = { selectedRoute = screen.route },
              icon = {
                Icon(imageVector = bottomTabIcon(screen), contentDescription = bottomTabLabel(screen))
              },
              label = { Text(bottomTabLabel(screen)) },
              alwaysShowLabel = true,
              colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
              )
            )
          }
        }
      }
    }
  }

  @Test
  fun fourTabs_render() {
    setBottomNavContent()

    composeTestRule.onNodeWithText("Home").assertExists()
    composeTestRule.onNodeWithText("Workouts").assertExists()
    composeTestRule.onNodeWithText("History").assertExists()
    composeTestRule.onNodeWithText("Profile").assertExists()
  }

  @Test
  fun selectingTab_marksItSelected_viaSemantics() {
    setBottomNavContent()

    // Home starts selected.
    composeTestRule.onNodeWithText("Home").assert(isSelected())

    composeTestRule.onNodeWithText("Workouts").performClick()

    composeTestRule.onNodeWithText("Workouts").assert(isSelected())
    composeTestRule.onNodeWithText("Home").assert(isNotSelected())
  }

  @Test
  fun fourTabs_allClickable() {
    setBottomNavContent()

    val clickableCount = composeTestRule
      .onAllNodes(hasClickAction())
      .fetchSemanticsNodes()
      .size
    assertEquals(4, clickableCount)
  }
}

private fun isNotSelected() =
  androidx.compose.ui.test.SemanticsMatcher.expectValue(
    androidx.compose.ui.semantics.SemanticsProperties.Selected,
    false
  )
