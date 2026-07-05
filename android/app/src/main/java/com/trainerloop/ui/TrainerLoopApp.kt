package com.trainerloop.ui

import android.app.Application
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.trainerloop.ui.devices.DevicesScreen
import com.trainerloop.app.trainerLoopApp
import com.trainerloop.ui.history.HistoryScreen
import com.trainerloop.ui.history.SessionDetailScreen
import com.trainerloop.ui.library.WorkoutBuilderScreen
import com.trainerloop.ui.home.HomeScreen
import com.trainerloop.ui.library.WorkoutLibraryScreen
import com.trainerloop.ui.navigation.Screen
import com.trainerloop.ui.settings.SettingsScreen
import com.trainerloop.ui.workout.detail.WorkoutDetailScreen
import com.trainerloop.ui.complete.WorkoutCompleteScreen
import com.trainerloop.ui.complete.WorkoutCompleteViewModelFactory
import com.trainerloop.ui.workout.WorkoutScreen
import com.trainerloop.ui.workout.WorkoutViewModelFactory

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
        val context = LocalContext.current
        HomeScreen(
          onNavigateToDevices = { navController.navigate(Screen.Devices.route) },
          onNavigateToWorkouts = { navController.navigate(Screen.Workouts.route) },
          onNavigateToBuilder = { navController.navigate(Screen.WorkoutBuilder.route) },
          onStartFreeRide = { navController.navigate(Screen.WorkoutPlayer.createRoute(sessionId = 1L)) },
          onStartPlanned = { workout ->
            context.trainerLoopApp.selectedWorkout = workout
            navController.navigate(Screen.WorkoutPlayer.createRoute(sessionId = 1L))
          }
        )
      }

      composable(Screen.WorkoutBuilder.route) {
        WorkoutBuilderScreen(
          onSaved = {
            navController.popBackStack()
            navController.navigate(Screen.Workouts.route) {
              popUpTo(Screen.Home.route) { saveState = true }
              launchSingleTop = true
            }
          },
          onBack = { navController.popBackStack() }
        )
      }

      composable(Screen.Workouts.route) {
        val context = LocalContext.current
        WorkoutLibraryScreen(
          onWorkoutSelected = { workout ->
            context.trainerLoopApp.selectedWorkout = workout
            navController.navigate(Screen.WorkoutDetail.createRoute(workout.id))
          }
        )
      }

      composable(Screen.History.route) {
        HistoryScreen(
          onSessionClick = { sessionId ->
            navController.navigate(Screen.SessionDetail.createRoute(sessionId))
          }
        )
      }

      composable(
        route = Screen.SessionDetail.route,
        arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
      ) { backStackEntry ->
        val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
        SessionDetailScreen(
          sessionId = sessionId,
          onBack = { navController.popBackStack() }
        )
      }

      composable(Screen.Profile.route) {
        SettingsScreen()
      }

      composable(
        route = Screen.WorkoutDetail.route,
        arguments = listOf(navArgument("workoutId") { type = NavType.StringType })
      ) {
        val context = LocalContext.current
        val workout = context.trainerLoopApp.selectedWorkout
        if (workout == null) {
          LaunchedEffect(Unit) { navController.popBackStack() }
          return@composable
        }
        WorkoutDetailScreen(
          workout = workout,
          onStartWorkout = { navController.navigate(Screen.WorkoutPlayer.createRoute(sessionId = 1L)) },
          onBack = { navController.popBackStack() }
        )
      }

      composable(
        route = Screen.WorkoutPlayer.route,
        arguments = listOf(navArgument("sessionId") { type = NavType.IntType })
      ) {
        val context = LocalContext.current
        val app = context.trainerLoopApp
        val workout = app.selectedWorkout ?: sampleWorkout
        // Pass the StateFlows (not the .value snapshot) so the ViewModel
        // observes manager changes and re-wires when a manager attaches
        // after the screen was first composed. Previously the control
        // manager was passed as a snapshot, so if the trainer connected
        // after the player was opened the ViewModel never saw it and
        // ERG control silently no-op'd.
        WorkoutScreen(
          workout = workout,
          viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
            factory = WorkoutViewModelFactory(
              workout = workout,
              ftmsManagerFlow = app.ftmsManager,
              hrManagerFlow = app.hrManager,
              ftmsControlManagerFlow = app.ftmsControlManager
            )
          ),
          onSessionFinished = { data ->
            app.pendingSessionSamples = data.samples
            navController.navigate(
              Screen.WorkoutComplete.createRoute(
                sessionId = data.startTimeMs.toString(),
                workoutId = data.workoutId,
                workoutName = data.workoutName,
                startTimeMs = data.startTimeMs
              )
            )
          },
          onExit = { navController.popBackStack() }
        )
      }

      composable(
        route = Screen.WorkoutComplete.route,
        arguments = listOf(
          navArgument("sessionId") { type = NavType.StringType },
          navArgument("workoutId") { type = NavType.StringType },
          navArgument("workoutName") { type = NavType.StringType },
          navArgument("startTimeMs") { type = NavType.LongType }
        )
      ) { backStackEntry ->
        val context = LocalContext.current
        val app = context.trainerLoopApp
        val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
        val workoutId = backStackEntry.arguments?.getString("workoutId") ?: "unknown"
        val workoutName = backStackEntry.arguments?.getString("workoutName") ?: "Workout"
        val startTimeMs = backStackEntry.arguments?.getLong("startTimeMs") ?: System.currentTimeMillis()
        val samples = app.pendingSessionSamples ?: emptyList()
        app.pendingSessionSamples = null

        WorkoutCompleteScreen(
          viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
            factory = WorkoutCompleteViewModelFactory(
              application = context.applicationContext as Application,
              sessionId = sessionId,
              workoutId = workoutId,
              workoutName = workoutName,
              samples = samples,
              startTimeMs = startTimeMs
            )
          ),
          onDiscard = { navController.popBackStack(Screen.Home.route, inclusive = false) },
          onDone = { navController.popBackStack(Screen.Home.route, inclusive = false) }
        )
      }

      composable(Screen.Devices.route) {
        DevicesScreen(
          onBack = { navController.popBackStack() }
        )
      }
    }
  }
}

private val Screen.icon: ImageVector
  get() = when (this) {
    Screen.Home -> Icons.Default.Home
    Screen.Workouts -> Icons.Default.FitnessCenter
    Screen.History -> Icons.Default.History
    Screen.Profile -> Icons.Default.Person
    else -> Icons.AutoMirrored.Filled.DirectionsRun
  }

private val Screen.label: String
  get() = when (this) {
    Screen.Home -> "Home"
    Screen.Workouts -> "Workouts"
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
