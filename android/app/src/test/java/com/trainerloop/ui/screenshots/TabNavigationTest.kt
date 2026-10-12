package com.trainerloop.ui.screenshots

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.trainerloop.ui.navigation.navigateToTab
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TabNavigationTest {
  @get:Rule val compose = createComposeRule()
  @Test fun chooseWorkoutThenTodayReturnsHome() {
    lateinit var nav: NavHostController
    compose.setContent {
      nav = rememberNavController()
      NavHost(nav, startDestination = "home") {
        composable("home") { Text("Home") }
        composable("workouts") { Text("Library") }
        composable("history") { Text("History") }
      }
    }
    repeat(3) {
      compose.runOnIdle { nav.navigateToTab("workouts") }
      compose.runOnIdle { assertEquals("workouts", nav.currentDestination?.route) }
      compose.runOnIdle { nav.navigateToTab("home") }
      compose.runOnIdle { assertEquals("home", nav.currentDestination?.route) }
      compose.runOnIdle { nav.navigateToTab("history") }
      compose.runOnIdle { assertEquals("history", nav.currentDestination?.route) }
      compose.runOnIdle { nav.navigateToTab("home") }
      compose.runOnIdle { assertEquals("home", nav.currentDestination?.route) }
    }
  }
}
