package com.trainerloop.documentation

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.trainerloop.app.MainActivity
import com.trainerloop.app.TrainerLoopApplication
import com.trainerloop.data.model.SessionData
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.data.model.UserProfile
import com.trainerloop.data.repository.SessionRepository
import com.trainerloop.data.source.local.AppDatabase
import com.trainerloop.domain.RampTest
import com.trainerloop.ui.TrainerLoopApp
import com.trainerloop.ui.library.BuiltInWorkouts
import com.trainerloop.ui.library.ImportedWorkoutStore
import com.trainerloop.ui.navigation.Screen
import com.trainerloop.ui.theme.TrainerLoopTheme
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/** Opt-in documentation capture. Uses real app UI and labeled synthetic ride data. */
class FlowScreenshotCaptureTest {
  private val rule = createAndroidComposeRule<MainActivity>()
  @get:Rule val captureRule: RuleChain = RuleChain.outerRule(object : ExternalResource() {
    override fun before() {
      assumeTrue("Opt-in capture only", InstrumentationRegistry.getArguments().getString("captureFlows") == "true")
    }
  }).around(rule)
  private lateinit var nav: NavHostController
  private lateinit var app: TrainerLoopApplication
  private lateinit var output: File
  private val startMs = Instant.parse("2026-10-10T09:00:00Z").toEpochMilli()
  private val samples = (1..1200).map { t ->
    TelemetrySample(t, 180 + (t % 90), 84 + (t % 7), 138 + (t % 18))
  }

  @Test
  fun captureCurrentFlowInventory() {
    app = rule.activity.application as TrainerLoopApplication
    output = File(app.getExternalFilesDir(null), "flow-captures").apply { mkdirs() }
    runBlocking(Dispatchers.IO) {
      app.profileRepository.updateProfile { UserProfile(name = "Rider", ftp = 250, weightKg = 75.0) }
      SessionRepository.create(AppDatabase.getInstance(app)).save(
        SessionData("documentation-ride", "sweet_spot", "Sweet Spot · sample ride",
          Instant.ofEpochMilli(startMs).toString(), Instant.ofEpochMilli(startMs + 1200_000).toString(),
          1200, Json.encodeToString(samples), "", true, 224, 269, 87, 146)
      )
    }
    val workout = BuiltInWorkouts.all(250).first()
    ImportedWorkoutStore.add(app, workout.copy(id = "documentation-workout", name = "Documentation sample"))
    rule.runOnUiThread {
      rule.activity.setContent {
        TrainerLoopTheme {
          val controller = rememberNavController()
          SideEffect { nav = controller }
          TrainerLoopApp(controller)
        }
      }
    }
    settle()
    capture("home")
    go(Screen.Workouts.route)
    capture("workouts")
    rule.onNodeWithContentDescription("Import workout file").performClick()
    systemCapture("import")
    back()
    settle()
    rule.onAllNodesWithContentDescription("More")[0].performClick()
    capture("menu")
    back()
    go(Screen.History.route)
    capture("history")
    go(Screen.SessionDetail.createRoute("documentation-ride"))
    capture("session")
    go(Screen.Profile.route)
    capture("profile")
    openProfileDialog("Power Zones", "power")
    openProfileDialog("Heart Rate Zones", "hr")
    openProfileDialog("About", "about")
    go(Screen.Home.route)
    go(Screen.Devices.route, reset = false)
    capture("devices")
    back()
    go(Screen.WorkoutDetail.createRoute(workout.id))
    capture("preview")
    rule.onNodeWithText("Start Workout").performClick()
    capture("ready")
    capture("player")
    rule.onNodeWithContentDescription("Start").performClick()
    Thread.sleep(3000)
    capture("running")
    rule.onNodeWithContentDescription("Pause").performClick()
    capture("paused")
    selectPage("Ride stats")
    capture("stats")
    selectPage("Chart")
    // Drag the real sheet from the transport row, then collapse it again.
    swipeControls(expand = true)
    capture("controls")
    swipeControls(expand = false)
    rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
    Thread.sleep(2500)
    capture("landscape")
    rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    Thread.sleep(2500)
    rule.onNodeWithContentDescription("Stop").performClick()
    capture("stop")
    rule.onNodeWithText("End", substring = false).performClick()
    settle()
    // Seed the real completion route's normal file payload for data-dependent views.
    openCompletion(workout.id, workout.name, "documentation-completion")
    capture("complete")
    rule.onNodeWithText("Share FIT").performScrollTo().performClick()
    systemCapture("share")
    back()
    settle()
    capture("complete-actions")
    go(Screen.Workouts.route)
    openCompletion(RampTest.WORKOUT_ID, "FTP Ramp Test", "documentation-ftp")
    capture("ftp")
    // Placeholder credentials are local fixture values. No external write is performed.
    runBlocking(Dispatchers.IO) {
      app.profileRepository.updateIntervalsIcuCredentials("documentation-only", "not-a-real-api-key")
    }
    rule.onNodeWithText("Accept FTP").performScrollTo().performClick()
    capture("ftpPush")
    runBlocking(Dispatchers.IO) { app.profileRepository.updateIntervalsIcuCredentials("", "") }
  }

  private fun openProfileDialog(label: String, id: String) {
    rule.onNodeWithText(label).performScrollTo().performClick()
    capture(id)
    rule.onNodeWithText("Close").performClick()
  }

  private fun go(route: String, reset: Boolean = true) {
    rule.runOnUiThread {
      nav.navigate(route) {
        if (reset) popUpTo(Screen.Home.route) { inclusive = false }
        launchSingleTop = true
      }
    }
    settle()
  }

  private fun openCompletion(workoutId: String, name: String, sessionId: String) {
    go(Screen.WorkoutPlayer.createRoute(System.currentTimeMillis(), workoutId))
    val file = File(app.filesDir, "$sessionId-samples.json").apply { writeText(Json.encodeToString(samples)) }
    rule.runOnUiThread {
      nav.currentBackStackEntry!!.savedStateHandle.set("finish_samples", file.absolutePath)
      nav.currentBackStackEntry!!.savedStateHandle.set("finish_completed", true)
      nav.navigate(Screen.WorkoutComplete.createRoute(sessionId, workoutId, name, startMs))
    }
    settle()
  }

  private fun selectPage(title: String) {
    rule.onNode(SemanticsMatcher("Pager action: $title") { node ->
      node.config.getOrNull(SemanticsActions.OnClick)?.label == "Show $title page"
    }).performClick()
  }

  private fun swipeControls(expand: Boolean) {
    val center = rule.onNodeWithContentDescription("Start").fetchSemanticsNode().boundsInRoot.center
    val frame = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()!!
    val endY = if (expand) (center.y - 550).toInt() else frame.height - 100
    frame.recycle()
    InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
      "input swipe ${center.x.toInt()} ${center.y.toInt()} ${center.x.toInt()} $endY 500"
    ).close()
    Thread.sleep(1500)
  }

  private fun settle() {
    rule.waitForIdle()
    Thread.sleep(1100)
  }

  private fun back() {
    InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 4").close()
    Thread.sleep(1100)
  }

  private fun systemCapture(id: String) {
    Thread.sleep(2200)
    saveScreenshot(id)
  }

  private fun capture(id: String) {
    settle()
    saveScreenshot(id)
  }

  private fun saveScreenshot(id: String) {
    val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
      ?: error("No screenshot for $id")
    File(output, "$id.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
    screenshot.recycle()
    android.util.Log.i("FlowCapture", "CAPTURED $id")
  }
}
