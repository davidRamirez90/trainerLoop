package com.trainerloop.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.Workout
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.data.model.WorkoutSource
import com.trainerloop.ui.connect.ConnectScreen
import com.trainerloop.ui.home.HomeScreen
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
  val currentBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = currentBackStackEntry?.destination?.route

  Scaffold(
    bottomBar = {
      if (currentRoute in Screen.bottomTabs.map { it.route }) {
        NavigationBar {
          Screen.bottomTabs.forEach { screen ->
            val selected = currentRoute == screen.route
            NavigationBarItem(
              selected = selected,
              onClick = {
                if (!selected) {
                  navController.navigate(screen.route) {
                    popUpTo(Screen.Home.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                  }
                }
              },
              icon = { Icon(imageVector = screen.icon, contentDescription = screen.label) },
              label = { Text(screen.label) }
            )
          }
        }
      }
    }
  ) { padding ->
    NavHost(
      modifier = Modifier.padding(padding),
      navController = navController,
      startDestination = Screen.Home.route
    ) {
      composable(Screen.Home.route) {
        HomeScreen(
          onNavigateToDevices = { navController.navigate(Screen.Devices.route) },
          onNavigateToWorkouts = { navController.navigate(Screen.Workouts.route) },
          onStartFreeRide = { navController.navigate("workout_player/1") }
        )
      }

      composable(Screen.Workouts.route) {
        WorkoutLibraryScreen(
          onStartWorkout = {
            navController.navigate("workout_player/1")
          }
        )
      }

      composable(Screen.Ride.route) {
        PlaceholderScreen("Ride")
      }

      composable(Screen.History.route) {
        PlaceholderScreen("History")
      }

      composable(Screen.Profile.route) {
        SettingsScreen()
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
    }
  }
}

private val Screen.icon: ImageVector
  get() = when (this) {
    Screen.Home -> Icons.Default.Home
    Screen.Workouts -> Icons.Default.FitnessCenter
    Screen.Ride -> Icons.AutoMirrored.Filled.DirectionsBike
    Screen.History -> Icons.Default.History
    Screen.Profile -> Icons.Default.Person
    else -> Icons.AutoMirrored.Filled.DirectionsRun
  }

private val Screen.label: String
  get() = when (this) {
    Screen.Home -> "Home"
    Screen.Workouts -> "Workouts"
    Screen.Ride -> "Ride"
    Screen.History -> "History"
    Screen.Profile -> "Profile"
    else -> ""
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
