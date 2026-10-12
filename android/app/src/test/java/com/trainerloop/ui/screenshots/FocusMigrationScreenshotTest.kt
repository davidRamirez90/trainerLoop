package com.trainerloop.ui.screenshots

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import com.github.takahirom.roborazzi.captureRoboImage
import com.trainerloop.ble.model.BleDevice
import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.SessionData
import com.trainerloop.data.model.SessionSummary
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.data.model.Workout
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.data.model.WorkoutSource
import com.trainerloop.domain.WorkoutSummaryMath
import com.trainerloop.ui.complete.WorkoutCompleteScreen
import com.trainerloop.ui.complete.WorkoutCompleteUiState
import com.trainerloop.ui.complete.WorkoutCompleteViewModel
import com.trainerloop.ui.components.FocusNavigationBar
import com.trainerloop.ui.devices.DevicesScreen
import com.trainerloop.ui.devices.DevicesUiState
import com.trainerloop.ui.devices.DevicesViewModel
import com.trainerloop.ui.history.HistoryScreenContent
import com.trainerloop.ui.history.SessionDetailContent
import com.trainerloop.ui.history.SessionDetailUiState
import com.trainerloop.ui.home.HomeScreenContent
import com.trainerloop.ui.home.HomeUiState
import com.trainerloop.ui.library.LibraryUiState
import com.trainerloop.ui.library.WorkoutCategory
import com.trainerloop.ui.library.WorkoutLibraryContent
import com.trainerloop.ui.library.WorkoutLibraryViewModel
import com.trainerloop.ui.library.WorkoutListItem
import com.trainerloop.ui.navigation.Screen
import com.trainerloop.ui.settings.SettingsScreen
import com.trainerloop.ui.settings.SettingsUiState
import com.trainerloop.ui.settings.SettingsViewModel
import com.trainerloop.ui.theme.LocalReducedMotion
import com.trainerloop.ui.theme.TrainerLoopTheme
import com.trainerloop.ui.workout.WorkoutPlayerActions
import com.trainerloop.ui.workout.WorkoutScreenContent
import com.trainerloop.ui.workout.WorkoutUiState
import com.trainerloop.ui.workout.detail.WorkoutDetailScreen
import com.trainerloop.ui.workout.detail.WorkoutDetailViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-xxhdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FocusMigrationScreenshotTest {
  @get:Rule val compose = createComposeRule()
  private val today = LocalDate.of(2026, 10, 11)
  private val workout = Workout("focus-threshold", "Threshold 3 × 10", "Three steady threshold efforts.",
    WorkoutSource.MANUAL, listOf(
      WorkoutSegment.Ramp("warmup", 600, "Warm up", SegmentPhase.WARMUP, false, 100, 200),
      WorkoutSegment.Step("work1", 600, "Threshold", SegmentPhase.WORK, true, TargetRange(235, 250)),
      WorkoutSegment.Step("rest1", 180, "Recover", SegmentPhase.RECOVERY, false, TargetRange(100, 125)),
      WorkoutSegment.Step("work2", 600, "Threshold", SegmentPhase.WORK, true, TargetRange(235, 250)),
      WorkoutSegment.Step("rest2", 180, "Recover", SegmentPhase.RECOVERY, false, TargetRange(100, 125)),
      WorkoutSegment.Step("work3", 600, "Threshold", SegmentPhase.WORK, true, TargetRange(235, 250)),
      WorkoutSegment.Ramp("cooldown", 300, "Cool down", SegmentPhase.COOLDOWN, false, 150, 80)
    ))
  private val session = SessionSummary("sample-ride", "endurance", "Endurance", "2026-10-09T08:00:00Z",
    "2026-10-09T08:30:00Z", 1800, true, 168, 198, 88, 132)
  private val samples = (0..900 step 5).map { TelemetrySample(it, 160 + it % 80, 88, 132) }
  private val home = HomeUiState(riderName = "Alex Morgan", ftp = 250, weightKg = 72.5,
    trainerModel = "KICKR CORE", isTrainerConnected = true, isHrConnected = true,
    plannedName = workout.name, plannedWorkout = workout, recentSession = session)
  private val player = WorkoutUiState(isRunning = true, elapsedSec = 900, activeSec = 900,
    segmentIndex = 1, segmentStartSec = 600, segmentEndSec = 1200, elapsedInSegmentSec = 300,
    targetRange = TargetRange(235, 250), currentPowerWatts = 243, currentCadenceRpm = 89,
    currentHrBpm = 147, sensorDropout = false, samples = samples, segments = workout.segments, inZoneSec = 270)

  private fun set(dark: Boolean = false, route: String? = null, fontScale: Float = 1f, content: @Composable () -> Unit) {
    compose.setContent {
      TrainerLoopTheme(darkTheme = dark) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalReducedMotion provides true, LocalDensity provides Density(density.density, fontScale)) {
          Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
              Box(Modifier.weight(1f)) { content() }
              if (route != null) FocusNavigationBar(route) {}
            }
          }
        }
      }
    }
    compose.waitForIdle()
  }

  private fun shot(name: String) {
    compose.waitForIdle()
    compose.onAllNodes(isRoot()).onLast().captureRoboImage("src/test/screenshots/focus-" + name + ".png")
  }

  @Composable
  private fun HomeContent(state: HomeUiState = home, onProfile: () -> Unit = {},
    onHistory: () -> Unit = {}, onSession: (String) -> Unit = {}, onRetry: () -> Unit = {}) {
    HomeScreenContent(state, {}, {}, {}, onProfile, {}, onRetry, onHistory, onSession, today)
  }

  @Test fun homeLight() { set(route = "home") { HomeContent() }; shot("home-light") }
  @Test fun homeDark() { set(true, "home") { HomeContent() }; shot("home-dark") }
  @Test fun homeEmpty() {
    set(route = "home") { HomeContent(HomeUiState(riderName = "Alex Morgan")) }; shot("home-empty")
  }
  @Test fun homeLoading() {
    set(route = "home") { HomeContent(home.copy(plannedWorkout = null, plannedLoading = true)) }
    shot("home-loading")
    compose.onNodeWithText("Open workout →").assertIsNotEnabled()
  }
  @Test fun homeError() {
    set(route = "home") { HomeContent(home.copy(plannedWorkout = null, plannedError = "Couldn't reach intervals.icu")) }
    shot("home-error")
  }
  @Test @Config(qualifiers = "en-rUS-w360dp-h800dp-xxhdpi") fun homeLargeText() {
    set(route = "home", fontScale = 1.5f) { HomeContent() }; shot("home-large-text")
  }
  @Test fun homeDestinationsAndRetryRemainReachable() {
    var destination = ""
    set(route = "home") { HomeContent(home.copy(plannedError = "Offline"),
      onProfile = { destination = "profile" }, onHistory = { destination = "history" },
      onSession = { destination = it }, onRetry = { destination = "retry" }) }
    compose.onNodeWithContentDescription("Open profile").performClick()
    assertEquals("profile", destination)
    compose.onNodeWithText("Retry plan").performClick()
    assertEquals("retry", destination)
    compose.onNodeWithText("View history →").performScrollTo().performClick()
    assertEquals("history", destination)
    compose.onNodeWithText("Endurance").performScrollTo().performClick()
    assertEquals(session.id, destination)
    assertFalse(Screen.Profile in Screen.bottomTabs)
  }

  private fun library(dark: Boolean, empty: Boolean = false) {
    val vm = mockk<WorkoutLibraryViewModel>(relaxed = true)
    val item = WorkoutListItem(workout, WorkoutCategory.THRESHOLD, WorkoutSummaryMath.workoutStats(workout, 250))
    set(dark, "workouts") {
      WorkoutLibraryContent(LibraryUiState(filteredWorkouts = if (empty) emptyList() else listOf(item),
        canSync = true), 250, vm, {}, {}, {})
    }
    shot(if (empty) "library-empty" else if (dark) "library-dark" else "library-light")
  }
  @Test fun libraryLight() = library(false)
  @Test fun libraryDark() = library(true)
  @Test fun libraryEmpty() = library(false, true)
  @Test fun workoutMenu() {
    val vm = mockk<WorkoutLibraryViewModel>(relaxed = true)
    val item = WorkoutListItem(workout, WorkoutCategory.THRESHOLD, WorkoutSummaryMath.workoutStats(workout, 250))
    set(route = "workouts") {
      WorkoutLibraryContent(LibraryUiState(filteredWorkouts = listOf(item), deletableIds = setOf(workout.id)),
        250, vm, {}, {}, {})
    }
    compose.onNodeWithContentDescription("More").performClick()
    compose.onNodeWithText("Duplicate").assertIsDisplayed()
    compose.onNodeWithText("Delete").assertIsDisplayed()
  }
  @Test fun preview() {
    set { WorkoutDetailScreen(workout, {}, {}, 250, WorkoutDetailViewModel(workout, 250)) }
    shot("preview")
  }
  @Test fun playerRunning() {
    set { WorkoutScreenContent(workout, player, 250, {}, WorkoutPlayerActions()) }; shot("player-running")
  }
  @Test fun playerDark() {
    set(true) { WorkoutScreenContent(workout, player, 250, {}, WorkoutPlayerActions()) }; shot("player-dark")
  }
  @Test fun playerIdle() {
    set { WorkoutScreenContent(workout, player.copy(isRunning = false, elapsedSec = 0, segmentIndex = 0,
      segmentEndSec = 600, currentPowerWatts = 0, currentHrBpm = 0, currentCadenceRpm = 0,
      samples = emptyList(), sensorDropout = true), 250, {}, WorkoutPlayerActions()) }
    shot("player-idle")
  }
  @Test fun playerPaused() {
    set { WorkoutScreenContent(workout, player.copy(isRunning = false), 250, {}, WorkoutPlayerActions()) }
    shot("player-paused")
  }
  @Test @Config(qualifiers = "en-rUS-w891dp-h411dp-land-xxhdpi") fun playerLandscape() {
    set { WorkoutScreenContent(workout, player, 250, {}, WorkoutPlayerActions()) }; shot("player-landscape")
  }
  @Test fun playerStopDialog() {
    set { WorkoutScreenContent(workout, player, 250, {}, WorkoutPlayerActions()) }
    compose.onNodeWithContentDescription("Back").performClick()
    shot("stop-dialog")
  }

  private fun completion(ramp: Boolean = false, push: Boolean = false) {
    val vm = mockk<WorkoutCompleteViewModel>(relaxed = true)
    every { vm.samples } returns samples
    every { vm.uiState } returns MutableStateFlow(WorkoutCompleteUiState(workoutName = workout.name,
      durationSec = 3060, avgPower = 203, maxPower = 250, avgCadence = 88, avgHr = 142,
      normalizedPower = 220, intensityFactor = .88, tss = 66, calories = 650, totalWorkKj = 621,
      ftpDecided = push, ftpAccepted = push, showIcuFtpPrompt = push,
      isRampTest = ramp, rampTestNewFtp = if (ramp) 265 else null, rampTestPreviousFtp = 250))
    set { WorkoutCompleteScreen(vm, {}, {}) }
    shot(if (push) "ftp-push" else if (ramp) "ftp-result" else "completion")
  }
  @Test fun completion() = completion(false)
  @Test fun ftpResult() = completion(true)
  @Test fun ftpPush() = completion(true, true)
  @Test fun history() {
    set(route = "history") { HistoryScreenContent(listOf(session), {}, today) }; shot("history")
  }
  @Test fun historyEmpty() {
    set(route = "history") { HistoryScreenContent(emptyList(), {}, today) }; shot("history-empty")
  }
  @Test fun sessionDetail() {
    val data = SessionData(session.id, session.workoutId, session.workoutName, session.startedAt,
      session.endedAt, session.durationSec, Json.encodeToString(ListSerializer(TelemetrySample.serializer()), samples),
      "", true, session.avgPower, session.maxPower, session.avgCadence, session.avgHr)
    set { SessionDetailContent(SessionDetailUiState(session = data, icuConfigured = true), 250, {}, {}) }
    shot("session-detail")
  }
  private fun settings(dark: Boolean = false, dialog: String? = null) {
    val vm = mockk<SettingsViewModel>(relaxed = true)
    every { vm.uiState } returns MutableStateFlow(SettingsUiState(name = "Alex Morgan", weightKg = "72.5"))
    set(dark) { SettingsScreen(viewModel = vm) }
    if (dialog != null) compose.onNodeWithText(dialog).performScrollTo().performClick()
    shot(dialog?.lowercase()?.replace(" ", "-") ?: if (dark) "profile-dark" else "profile-light")
  }
  @Test fun profile() = settings()
  @Test fun profileDark() = settings(true)
  @Test fun powerZones() = settings(dialog = "Power Zones")
  @Test fun hrZones() = settings(dialog = "Heart Rate Zones")
  @Test fun about() = settings(dialog = "About")

  private fun devices(permission: Boolean) {
    val vm = mockk<DevicesViewModel>(relaxed = true)
    every { vm.uiState } returns MutableStateFlow(DevicesUiState(hasPermissions = permission,
      isBluetoothOn = true, isLocationOn = true,
      connectedTrainer = if (permission) BleDevice("00:11:22:33:44:55", "KICKR CORE", emptyList(), -55) else null))
    set { DevicesScreen(viewModel = vm) }
    shot(if (permission) "devices" else "devices-permission")
  }
  @Test fun devices() = devices(true)
  @Test fun devicePermission() = devices(false)
}
