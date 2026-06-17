package com.trainerloop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.trainerloop.ui.navigation.Screen

@Composable
fun TrainerLoopApp(
  navController: NavHostController = rememberNavController()
) {
  NavHost(
    navController = navController,
    startDestination = Screen.Library.route
  ) {
    composable(Screen.Library.route) {
      PlaceholderScreen("Workout Library")
    }

    composable(
      route = Screen.WorkoutPreview.route,
      arguments = listOf(navArgument("workoutId") { type = NavType.StringType })
    ) {
      PlaceholderScreen("Workout Preview")
    }

    composable(
      route = Screen.Workout.route,
      arguments = listOf(navArgument("sessionId") { type = NavType.IntType })
    ) {
      PlaceholderScreen("Workout Player")
    }

    composable(
      route = Screen.SessionSummary.route,
      arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
    ) {
      PlaceholderScreen("Session Summary")
    }

    composable(Screen.Connect.route) {
      PlaceholderScreen("Device Connection")
    }

    composable(Screen.Settings.route) {
      PlaceholderScreen("Settings")
    }
  }
}

@Composable
private fun PlaceholderScreen(title: String) {
  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.headlineLarge
    )
  }
}
