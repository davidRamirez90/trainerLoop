package com.trainerloop.ui

import android.app.Application
import android.content.Context
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.domain.RampTest
import com.trainerloop.domain.WorkoutResolver
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
import java.io.File
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

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
          onStartFreeRide = {
            navController.navigate(
              Screen.WorkoutPlayer.createRoute(
                sessionId = System.currentTimeMillis(),
                workoutId = WorkoutResolver.FREE_RIDE_ID
              )
            )
          },
          onStartPlanned = { workout ->
            if (com.trainerloop.ui.library.ImportedWorkoutStore.load(context).none { it.id == workout.id }) {
              com.trainerloop.ui.library.ImportedWorkoutStore.add(context, workout)
            }
            navController.navigate(
              Screen.WorkoutPlayer.createRoute(
                sessionId = System.currentTimeMillis(),
                workoutId = workout.id
              )
            )
          },
          onGpxRoutes = { navController.navigate(Screen.Routes.route) }
        )
      }

      composable(Screen.Routes.route) {
        com.trainerloop.ui.routes.RoutesScreen(
          onRouteClick = { id -> navController.navigate(Screen.RouteDetail.createRoute(id)) },
          onBack = { navController.popBackStack() }
        )
      }

      composable(
        route = Screen.RouteDetail.route,
        arguments = listOf(navArgument("routeId") { type = NavType.StringType })
      ) { backStackEntry ->
        val routeId = backStackEntry.arguments?.getString("routeId") ?: return@composable
        com.trainerloop.ui.routes.RouteDetailScreen(
          routeId = routeId,
          onStartRide = { id -> navController.navigate(Screen.FreeRide.createRoute(id)) },
          onBack = { navController.popBackStack() }
        )
      }

      composable(
        route = Screen.FreeRide.route,
        arguments = listOf(navArgument("routeId") { type = NavType.StringType })
      ) { backStackEntry ->
        val context = LocalContext.current
        val app = context.trainerLoopApp
        val routeId = backStackEntry.arguments?.getString("routeId") ?: return@composable
        var route by androidx.compose.runtime.remember {
          androidx.compose.runtime.mutableStateOf<com.trainerloop.data.model.Route?>(null)
        }
        LaunchedEffect(routeId) {
          route = com.trainerloop.data.repository.RouteRepository
            .create(com.trainerloop.data.source.local.AppDatabase.getInstance(context))
            .getById(routeId)
        }
        val loaded = route ?: return@composable
        val profile = remember { app.profileRepository.getProfileSync() }
        val freeRideFactory = remember(
          loaded, routeId, app.ftmsManager, app.hrManager, app.ftmsControlManager,
          app.clickManager, profile
        ) {
          com.trainerloop.ui.freeride.FreeRideViewModelFactory(
            route = loaded,
            routeId = routeId,
            ftmsManagerFlow = app.ftmsManager,
            hrManagerFlow = app.hrManager,
            ftmsControlManagerFlow = app.ftmsControlManager,
            clickManagerFlow = app.clickManager,
            userProfile = profile
          )
        }
        com.trainerloop.ui.freeride.FreeRideScreen(
          viewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = freeRideFactory),
          onSessionFinished = { data ->
            navController.storeFinishPayload(
              context, data.startTimeMs, data.samples, data.coachJson,
              data.completedNaturally, "FREE_RIDE", routeId
            )
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
        val app = context.trainerLoopApp
        WorkoutLibraryScreen(
          onWorkoutSelected = { workout ->
            navController.navigate(Screen.WorkoutDetail.createRoute(workout.id))
          },
          onStartRampTest = {
            val ftp = app.profileRepository.getProfileSync().ftp
            navController.navigate(
              Screen.WorkoutPlayer.createRoute(
                sessionId = System.currentTimeMillis(),
                workoutId = RampTest.generate(ftp).id
              )
            )
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
      ) { backStackEntry ->
        val context = LocalContext.current
        val app = context.trainerLoopApp
        val workoutId = backStackEntry.arguments?.getString("workoutId") ?: return@composable
        val ftp = remember { app.profileRepository.getProfileSync().ftp }
        val workout = remember(workoutId) {
          WorkoutResolver.resolve(
            workoutId,
            ftp,
            com.trainerloop.ui.library.ImportedWorkoutStore.load(context)
          )
        }
        if (workout == null) {
          LaunchedEffect(Unit) { navController.popBackStack() }
          return@composable
        }
        WorkoutDetailScreen(
          workout = workout,
          onStartWorkout = {
            navController.navigate(
              Screen.WorkoutPlayer.createRoute(
                sessionId = System.currentTimeMillis(),
                workoutId = workout.id
              )
            )
          },
          onBack = { navController.popBackStack() }
        )
      }

      composable(
        route = Screen.WorkoutPlayer.route,
        arguments = listOf(
          navArgument("sessionId") { type = NavType.LongType },
          navArgument("workoutId") { type = NavType.StringType }
        )
      ) { backStackEntry ->
        val context = LocalContext.current
        val app = context.trainerLoopApp
        val profile = remember { app.profileRepository.getProfileSync() }
        val workoutId = backStackEntry.arguments?.getString("workoutId") ?: return@composable
        val workout = remember(workoutId, profile.ftp) {
          WorkoutResolver.resolve(
            workoutId,
            profile.ftp,
            com.trainerloop.ui.library.ImportedWorkoutStore.load(context)
          )
        }
        if (workout == null) {
          LaunchedEffect(Unit) {
            android.util.Log.e("TrainerLoopApp", "Unable to resolve workoutId=$workoutId")
            navController.popBackStack()
          }
          return@composable
        }
        val coachProfile = remember(profile.selectedCoachProfileId) {
          com.trainerloop.data.source.local.CoachProfileLoader.load(
            context, profile.selectedCoachProfileId
          )
        }
        val workoutFactory = remember(
          workout, app.ftmsManager, app.hrManager, app.ftmsControlManager, profile, coachProfile
        ) {
          WorkoutViewModelFactory(
            workout = workout,
            ftmsManagerFlow = app.ftmsManager,
            hrManagerFlow = app.hrManager,
            ftmsControlManagerFlow = app.ftmsControlManager,
            userProfile = profile,
            coachProfile = coachProfile
          )
        }
        // Pass the StateFlows (not the .value snapshot) so the ViewModel
        // observes manager changes and re-wires when a manager attaches
        // after the screen was first composed. Previously the control
        // manager was passed as a snapshot, so if the trainer connected
        // after the player was opened the ViewModel never saw it and
        // ERG control silently no-op'd.
        WorkoutScreen(
          workout = workout,
          viewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = workoutFactory),
          onSessionFinished = { data ->
            navController.storeFinishPayload(
              context, data.startTimeMs, data.samples, data.coachJson, data.completedNaturally
            )
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
        val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
        val workoutId = backStackEntry.arguments?.getString("workoutId") ?: "unknown"
        val workoutName = backStackEntry.arguments?.getString("workoutName") ?: "Workout"
        val startTimeMs = backStackEntry.arguments?.getLong("startTimeMs") ?: System.currentTimeMillis()
        val payload = navController.previousBackStackEntry?.savedStateHandle
        val samples = payload?.get<String>(FINISH_SAMPLES_KEY)?.let(::readSamplesFile) ?: emptyList()
        val coachJson = payload?.get<String>(FINISH_COACH_KEY)?.let(::readAndDeleteFile) ?: ""
        val sessionType = payload?.get<String>(FINISH_TYPE_KEY) ?: "WORKOUT"
        val freeRideRouteId = payload?.get<String>(FINISH_ROUTE_KEY)
        val completed = payload?.get<Boolean>(FINISH_COMPLETED_KEY) ?: false
        val completeFactory = remember(sessionId, completed) {
          WorkoutCompleteViewModelFactory(
            application = context.applicationContext as Application,
            sessionId = sessionId,
            workoutId = workoutId,
            workoutName = workoutName,
            samples = samples,
            startTimeMs = startTimeMs,
            coachJson = coachJson,
            sessionType = sessionType,
            routeId = freeRideRouteId,
            completed = completed
          )
        }

        WorkoutCompleteScreen(
          viewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = completeFactory),
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

private const val FINISH_SAMPLES_KEY = "finish_samples"
private const val FINISH_COACH_KEY = "finish_coach"
private const val FINISH_TYPE_KEY = "finish_type"
private const val FINISH_ROUTE_KEY = "finish_route"
private const val FINISH_COMPLETED_KEY = "finish_completed"

private fun NavHostController.storeFinishPayload(
  context: Context,
  startTimeMs: Long,
  samples: List<TelemetrySample>,
  coachJson: String,
  completed: Boolean = false,
  sessionType: String = "WORKOUT",
  routeId: String? = null
) {
  val samplesFile = context.cacheDir.resolve("finish_samples_$startTimeMs.json")
  val coachFile = context.cacheDir.resolve("finish_coach_$startTimeMs.json")
  samplesFile.writeText(Json.encodeToString(ListSerializer(TelemetrySample.serializer()), samples))
  coachFile.writeText(coachJson)
  currentBackStackEntry?.savedStateHandle?.set(
    FINISH_SAMPLES_KEY,
    samplesFile.absolutePath
  )
  currentBackStackEntry?.savedStateHandle?.set(FINISH_COACH_KEY, coachFile.absolutePath)
  currentBackStackEntry?.savedStateHandle?.set(FINISH_TYPE_KEY, sessionType)
  currentBackStackEntry?.savedStateHandle?.set(FINISH_ROUTE_KEY, routeId)
  currentBackStackEntry?.savedStateHandle?.set(FINISH_COMPLETED_KEY, completed)
}

private fun readSamplesFile(path: String): List<TelemetrySample> =
  readAndDeleteFile(path)?.let { json ->
    runCatching {
      Json.decodeFromString(ListSerializer(TelemetrySample.serializer()), json)
    }.getOrDefault(emptyList())
  } ?: emptyList()

private fun readAndDeleteFile(path: String): String? = runCatching {
  File(path).readText().also { File(path).delete() }
}.getOrNull()

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
