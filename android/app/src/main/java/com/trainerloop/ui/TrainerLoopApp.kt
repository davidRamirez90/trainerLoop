package com.trainerloop.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.domain.RampTest
import com.trainerloop.domain.WorkoutResolver
import com.trainerloop.ui.devices.DevicesScreen
import com.trainerloop.app.trainerLoopApp
import com.trainerloop.ui.history.HistoryScreen
import com.trainerloop.ui.history.SessionDetailScreen
import com.trainerloop.ui.home.HomeScreen
import com.trainerloop.ui.library.WorkoutLibraryScreen
import com.trainerloop.ui.navigation.navigateToTab
import com.trainerloop.ui.navigation.Screen
import com.trainerloop.ui.navigation.TabFadeThroughTransitions
import com.trainerloop.ui.navigation.playerEnter
import com.trainerloop.ui.navigation.playerExit
import com.trainerloop.ui.navigation.sharedAxisXEnter
import com.trainerloop.ui.navigation.sharedAxisXExit
import com.trainerloop.ui.navigation.sharedAxisXPopEnter
import com.trainerloop.ui.navigation.sharedAxisXPopExit
import com.trainerloop.ui.navigation.tabFadeThrough
import com.trainerloop.ui.settings.SettingsScreen
import com.trainerloop.ui.theme.LocalReducedMotion
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
  val reducedMotion = LocalReducedMotion.current
  val tabTransitions = tabFadeThrough(reducedMotion)
  val showBottomBar = currentRoute in Screen.bottomTabs.map { it.route }
  SystemBarIconAppearance(
    statusBarSurface = MaterialTheme.colorScheme.background,
    navigationBarSurface = if (showBottomBar) {
      MaterialTheme.colorScheme.background
    } else {
      MaterialTheme.colorScheme.background
    }
  )

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    // The root owns the status-bar inset for screens without their own app bar,
    // and the bottom bar owns the navigation-bar inset through NavigationBar.
    contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top),
    bottomBar = {
      if (showBottomBar) {
        com.trainerloop.ui.components.FocusNavigationBar(currentRoute) { route ->
          navController.navigateToTab(route)
        }
      }
    }
  ) { padding ->
    NavHost(
      modifier = Modifier.padding(padding),
      navController = navController,
      startDestination = Screen.Home.route,
      enterTransition = {
        defaultEnterTransition(
          reducedMotion = reducedMotion,
          tabTransitions = tabTransitions
        )
      },
      exitTransition = {
        defaultExitTransition(
          reducedMotion = reducedMotion,
          tabTransitions = tabTransitions
        )
      },
      popEnterTransition = {
        defaultPopEnterTransition(
          reducedMotion = reducedMotion,
          tabTransitions = tabTransitions
        )
      },
      popExitTransition = {
        defaultPopExitTransition(
          reducedMotion = reducedMotion,
          tabTransitions = tabTransitions
        )
      }
    ) {
      composable(Screen.Home.route) {
        val context = LocalContext.current
        HomeScreen(
          onNavigateToDevices = { navController.navigate(Screen.Devices.route) },
          onNavigateToWorkouts = { navController.navigateToTab(Screen.Workouts.route) },
          onNavigateToHistory = { navController.navigateToTab(Screen.History.route) },
          onSessionClick = { id -> navController.navigate(Screen.SessionDetail.createRoute(id)) },
          onNavigateToProfile = {
            navController.navigate(Screen.Profile.route) { launchSingleTop = true }
          },
          onStartPlanned = { workout ->
            com.trainerloop.ui.library.ImportedWorkoutStore.add(context, workout)
            navController.navigate(
              Screen.WorkoutPlayer.createRoute(
                sessionId = System.currentTimeMillis(),
                workoutId = workout.id
              )
            )
          },
          onChooseWorkout = { navController.navigateToTab(Screen.Workouts.route) }
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
        SettingsScreen(onBack = { navController.popBackStack() })
      }

      composable(
        route = Screen.WorkoutDetail.route,
        arguments = listOf(navArgument("workoutId") { type = NavType.StringType })
      ) { backStackEntry ->
        val context = LocalContext.current
        val app = context.trainerLoopApp
        val workoutId = backStackEntry.arguments?.getString("workoutId") ?: return@composable
        val profile by app.profileRepository.profile.collectAsStateWithLifecycle(
          initialValue = app.profileRepository.getProfileSync()
        )
        val ftp = profile.ftp
        val workout = remember(workoutId, ftp) {
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
          ftp = ftp,
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
        ),
        enterTransition = { playerEnter(reducedMotion) },
        exitTransition = { tabTransitions.exit },
        popEnterTransition = { playerEnter(reducedMotion) },
        popExitTransition = { playerExit(reducedMotion) }
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
        val workoutFactory = remember(
          workout, app.ftmsManager, app.hrManager, app.ftmsControlManager, profile
        ) {
          WorkoutViewModelFactory(
            workout = workout,
            ftmsManagerFlow = app.ftmsManager,
            hrManagerFlow = app.hrManager,
            ftmsControlManagerFlow = app.ftmsControlManager,
            userProfile = profile
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
              context, data.startTimeMs, data.samples, data.completedNaturally
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
        val samplesPath = payload?.get<String>(FINISH_SAMPLES_KEY)
        val samples = remember(samplesPath) {
          samplesPath?.let(::readSamplesFile) ?: emptyList()
        }
        val completed = payload?.get<Boolean>(FINISH_COMPLETED_KEY) ?: false
        val completeFactory = remember(sessionId, completed) {
          WorkoutCompleteViewModelFactory(
            application = context.applicationContext as Application,
            sessionId = sessionId,
            workoutId = workoutId,
            workoutName = workoutName,
            samples = samples,
            startTimeMs = startTimeMs,
            completed = completed
          )
        }

        WorkoutCompleteScreen(
          viewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = completeFactory),
          onDiscard = {
            payload?.get<String>(FINISH_SAMPLES_KEY)?.let { File(it).delete() }
            navController.popBackStack(Screen.Home.route, inclusive = false)
          },
          onDone = {
            payload?.get<String>(FINISH_SAMPLES_KEY)?.let { File(it).delete() }
            navController.popBackStack(Screen.Home.route, inclusive = false)
          }
        )
      }

      dialog(
        route = Screen.Devices.route,
        dialogProperties = DialogProperties(usePlatformDefaultWidth = false)
      ) {
        DevicesScreen(
          onBack = { navController.popBackStack() }
        )
      }
    }
  }
}

@Composable
private fun SystemBarIconAppearance(
  statusBarSurface: Color,
  navigationBarSurface: Color
) {
  val view = LocalView.current
  val activity = view.context.findActivity() ?: return
  SideEffect {
    WindowCompat.getInsetsController(activity.window, view).apply {
      isAppearanceLightStatusBars = statusBarSurface.luminance() > 0.5f
      isAppearanceLightNavigationBars = navigationBarSurface.luminance() > 0.5f
    }
  }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}

private const val FINISH_SAMPLES_KEY = "finish_samples"
private const val FINISH_COMPLETED_KEY = "finish_completed"

private fun AnimatedContentTransitionScope<NavBackStackEntry>.defaultEnterTransition(
  reducedMotion: Boolean,
  tabTransitions: TabFadeThroughTransitions
): EnterTransition {
  val initialRoute = initialState.destination.route
  val targetRoute = targetState.destination.route
  return when {
    targetRoute == Screen.WorkoutPlayer.route -> playerEnter(reducedMotion)
    initialRoute.isBottomTabRoute() && targetRoute.isBottomTabRoute() -> tabTransitions.enter
    else -> sharedAxisXEnter(reducedMotion)
  }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.defaultExitTransition(
  reducedMotion: Boolean,
  tabTransitions: TabFadeThroughTransitions
): ExitTransition {
  val initialRoute = initialState.destination.route
  val targetRoute = targetState.destination.route
  return when {
    targetRoute == Screen.WorkoutPlayer.route -> tabTransitions.exit
    initialRoute == Screen.WorkoutPlayer.route -> tabTransitions.exit
    initialRoute.isBottomTabRoute() && targetRoute.isBottomTabRoute() -> tabTransitions.exit
    else -> sharedAxisXExit(reducedMotion)
  }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.defaultPopEnterTransition(
  reducedMotion: Boolean,
  tabTransitions: TabFadeThroughTransitions
): EnterTransition {
  val initialRoute = initialState.destination.route
  val targetRoute = targetState.destination.route
  return when {
    targetRoute == Screen.WorkoutPlayer.route -> playerEnter(reducedMotion)
    initialRoute == Screen.WorkoutPlayer.route -> tabTransitions.enter
    initialRoute.isBottomTabRoute() && targetRoute.isBottomTabRoute() -> tabTransitions.enter
    else -> sharedAxisXPopEnter(reducedMotion)
  }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.defaultPopExitTransition(
  reducedMotion: Boolean,
  tabTransitions: TabFadeThroughTransitions
): ExitTransition {
  val initialRoute = initialState.destination.route
  val targetRoute = targetState.destination.route
  return when {
    initialRoute == Screen.WorkoutPlayer.route -> playerExit(reducedMotion)
    initialRoute.isBottomTabRoute() && targetRoute.isBottomTabRoute() -> tabTransitions.exit
    else -> sharedAxisXPopExit(reducedMotion)
  }
}

private fun String?.isBottomTabRoute(): Boolean =
  Screen.bottomTabs.any { it.route == this }

private fun NavHostController.storeFinishPayload(
  context: Context,
  startTimeMs: Long,
  samples: List<TelemetrySample>,
  completed: Boolean = false
) {
  val samplesFile = context.cacheDir.resolve("finish_samples_$startTimeMs.json")
  samplesFile.writeText(Json.encodeToString(ListSerializer(TelemetrySample.serializer()), samples))
  currentBackStackEntry?.savedStateHandle?.set(
    FINISH_SAMPLES_KEY,
    samplesFile.absolutePath
  )
  currentBackStackEntry?.savedStateHandle?.set(FINISH_COMPLETED_KEY, completed)
}

private fun readSamplesFile(path: String): List<TelemetrySample> =
  readPayloadFile(path)?.let { json ->
    runCatching {
      Json.decodeFromString(ListSerializer(TelemetrySample.serializer()), json)
    }.getOrDefault(emptyList())
  } ?: emptyList()

private fun readPayloadFile(path: String): String? = runCatching {
  File(path).readText()
}.getOrNull()
