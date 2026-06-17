package com.trainerloop.ui

import android.app.Application
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
import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.Workout
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.data.model.WorkoutSource
import com.trainerloop.ui.connect.ConnectScreen
import com.trainerloop.ui.library.WorkoutLibraryScreen
import com.trainerloop.ui.navigation.Screen
import com.trainerloop.ui.settings.SettingsScreen
import com.trainerloop.ui.summary.SessionSummaryScreen
import com.trainerloop.ui.summary.SessionSummaryViewModel
import com.trainerloop.ui.workout.WorkoutScreen

@Composable
fun TrainerLoopApp(
  navController: NavHostController = rememberNavController()
) {
  NavHost(
    navController = navController,
    startDestination = Screen.Devices.route
  ) {
    composable(Screen.Workouts.route) {
      WorkoutLibraryScreen(
        onStartWorkout = { workout ->
          navController.navigate("workout_player/1")
        }
      )
    }

    composable(
      route = Screen.WorkoutDetail.route,
      arguments = listOf(navArgument("workoutId") { type = NavType.StringType })
    ) {
      PlaceholderScreen("Workout Preview")
    }

    composable(
      route = Screen.WorkoutPlayer.route,
      arguments = listOf(navArgument("sessionId") { type = NavType.IntType })
    ) {
      WorkoutScreen(
        workout = sampleWorkout,
        onFinish = { navController.popBackStack() }
      )
    }

    composable(
      route = Screen.WorkoutComplete.route,
      arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
    ) { backStackEntry ->
      val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
      SessionSummaryScreen(
        viewModel = SessionSummaryViewModel(
          application = androidx.compose.ui.platform.LocalContext.current.applicationContext as Application,
          sessionId = sessionId,
          workoutName = "Workout",
          samples = emptyList()
        ),
        onDone = { navController.popBackStack() }
      )
    }

    composable(Screen.Devices.route) {
      ConnectScreen(
        onNavigateToLibrary = { navController.navigate(Screen.Workouts.route) }
      )
    }

    composable(Screen.Profile.route) {
      SettingsScreen()
    }
  }
}

// Temporary sample workout for development
private val sampleWorkout = Workout(
  id = "sample",
  name = "Sweet Spot",
  description = "Aerobic sweet spot training",
  source = WorkoutSource.MANUAL,
  segments = listOf(
    WorkoutSegment.FreeRide(id = "warmup", durationSec = 300, label = "Warm Up", phase = SegmentPhase.WARMUP),
    WorkoutSegment.Step(id = "ss1", durationSec = 300, label = "Sweet Spot", phase = SegmentPhase.WORK, isWork = true, targetRange = TargetRange(200, 210)),
    WorkoutSegment.Step(id = "ss2", durationSec = 300, label = "Sweet Spot", phase = SegmentPhase.WORK, isWork = true, targetRange = TargetRange(210, 220)),
    WorkoutSegment.Ramp(id = "ramp", durationSec = 120, label = "Ramp", phase = SegmentPhase.WORK, isWork = true, startPower = 220, endPower = 250),
    WorkoutSegment.Step(id = "ss3", durationSec = 180, label = "Hard", phase = SegmentPhase.WORK, isWork = true, targetRange = TargetRange(250, 260)),
    WorkoutSegment.FreeRide(id = "cd", durationSec = 300, label = "Cool Down", phase = SegmentPhase.COOLDOWN)
  )
)

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
