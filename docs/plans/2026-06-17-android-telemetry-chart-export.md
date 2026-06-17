# Android Migration — Telemetry, Live Chart, and Session Export Implementation Plan

> **REQUIRED SUB-SKILL:** Use the `executing-plans` skill to implement this plan task-by-task.

**Goal:** Close the three remaining integration gaps in the Kotlin Android app (no live telemetry, no live chart, no save/export) while reshaping the UI to match the provided design system: a dark, bottom-navigation app with a Home dashboard, a dedicated Devices screen, a Workout Library with previews, a tabbed Workout Player, and a Workout Complete summary screen.

**Architecture:** Introduce an `Application`-scoped `TrainerLoopApplication` that owns the single `FtmsManager`, `HrManager`, and `FtmsControlManager` instances so GATT connections survive navigation. Restructure navigation around a bottom bar (Home / Workouts / Ride / History / Profile). The Home dashboard shows the user header, connected-device cards, and quick-start actions. The Devices screen becomes the dedicated BLE pairing/management page. The Workout Library gains preview cards with mini-charts. Selecting a workout opens a Workout Detail screen with a full preview chart and interval list. Starting the workout launches the tabbed Workout Player, which streams telemetry through `TelemetryRecorder`, draws the live power/target chart, and writes ERG targets. When the workout ends, the app navigates to Workout Complete, which computes summary stats (TSS, IF, NP, avg/max power, HR, cadence, calories/work), shows a post-ride chart, and offers Discard / Save / Share FIT.

**Tech Stack:** Kotlin, Jetpack Compose, ViewModel, Navigation Compose, Coroutines/Flow, Room (existing), Android `BluetoothGatt` (existing).

---

## Current State & Root Causes

The broad migration plan in `docs/plans/2026-06-16-kotlin-android-migration.md` is mostly implemented at the file level, but the UI is still a rough scaffold and three critical UI→BLE→UI integration gaps remain:

1. **No live telemetry on the workout screen**
   - `ConnectScreen.kt` records a "connected" `BleDevice` but only mutates UI state. It never calls `FtmsManager.connect()`, `HrManager.connect()`, or `FtmsControlManager.connect()`.
   - The selected devices are not passed to `WorkoutScreen`.
   - `WorkoutViewModel` is constructed with only a `Workout`; it has no access to `FtmsManager`, `HrManager`, or `FtmsControlManager`.
   - `TelemetryRecorder` exists but is unused.
   - `WorkoutViewModel.setTelemetry(...)` is dead code.

2. **No live workout chart**
   - There is no chart composable in `android/app/src/main/java/com/trainerloop/ui/components/`.
   - `WorkoutScreen.kt` only shows `IntervalTimeline` plus metric cards.

3. **No save/export after the workout**
   - `WorkoutScreen.onFinish` pops the back stack instead of navigating to a summary.
   - `TrainerLoopApp` instantiates `SessionSummaryScreen` with hard-coded `samples = emptyList()`.
   - No path exists to forward the recorded `TelemetrySample` list from `WorkoutViewModel` to `SessionSummaryViewModel`.

Additionally, the current single-flow navigation (Connect → Library → Workout) does not match the design's bottom-tab structure and leaves no natural place for the dashboard, Devices management, or post-ride summary.

---

## Phase 0 — Shared BLE Connection Holder

### Task 0.1: Create `TrainerLoopApplication`

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/app/TrainerLoopApplication.kt`
- Modify: `android/app/src/main/AndroidManifest.xml`

**Step 1:** Add an `Application` subclass that hosts the active BLE managers and cross-screen data.

```kotlin
package com.trainerloop.app

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Context
import com.trainerloop.ble.FtmsControlManager
import com.trainerloop.ble.FtmsManager
import com.trainerloop.ble.HrManager
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.data.model.Workout

class TrainerLoopApplication : Application() {

  var ftmsManager: FtmsManager? = null
    private set
  var hrManager: HrManager? = null
    private set
  var ftmsControlManager: FtmsControlManager? = null
    private set

  var selectedWorkout: Workout? = null
  var pendingSessionSamples: List<TelemetrySample>? = null

  fun attachTrainer(device: BluetoothDevice) {
    ftmsManager = FtmsManager(this, device)
    ftmsControlManager = FtmsControlManager(this, device)
  }

  fun attachHr(device: BluetoothDevice) {
    hrManager = HrManager(this, device)
  }

  fun clearDevices() {
    ftmsManager = null
    ftmsControlManager = null
    hrManager = null
  }
}

val Context.trainerLoopApp: TrainerLoopApplication
  get() = applicationContext as TrainerLoopApplication
```

**Step 2:** Register the application in the manifest.

```xml
<application
  android:name=".TrainerLoopApplication"
  ... >
```

**Step 3:** Run a build to verify.

Run: `./gradlew :app:compileDebugKotlin`  
Expected: `BUILD SUCCESSFUL`

**Step 4:** Commit.

```bash
git add android/app/src/main/java/com/trainerloop/app/TrainerLoopApplication.kt android/app/src/main/AndroidManifest.xml
git commit -m "feat: add application-scoped BLE manager holder"
```

---

## Phase 1 — Bottom Navigation + Home Dashboard

### Task 1.1: Define bottom-tab screens

**Files:**
- Modify: `android/app/src/main/java/com/trainerloop/ui/navigation/Screen.kt`

**Step 1:** Replace the flat route enum with a sealed class that supports bottom tabs and nested flows.

```kotlin
package com.trainerloop.ui.navigation

sealed class Screen(val route: String) {
  // Bottom tabs
  object Home : Screen("home")
  object Workouts : Screen("workouts")
  object Ride : Screen("ride")
  object History : Screen("history")
  object Profile : Screen("profile")

  // Other flows
  object Devices : Screen("devices")
  object WorkoutDetail : Screen("workout_detail/{workoutId}")
  object WorkoutPlayer : Screen("workout_player/{sessionId}")
  object WorkoutComplete : Screen("workout_complete/{sessionId}/{workoutName}/{startTimeMs}")

  companion object {
    val bottomTabs = listOf(Home, Workouts, Ride, History, Profile)
  }
}
```

**Step 2:** Commit.

```bash
git add android/app/src/main/java/com/trainerloop/ui/navigation/Screen.kt
git commit -m "feat: add bottom-tab screen definitions"
```

---

### Task 1.2: Add `HomeScreen` dashboard

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/ui/home/HomeScreen.kt`
- Create: `android/app/src/main/java/com/trainerloop/ui/home/HomeViewModel.kt`

**Step 1:** Build a dashboard matching the design:
- Header with user avatar, name, FTP, weight.
- "Connected Devices" section with trainer + HR cards showing name, model, battery, live HR.
- "Quick Start" primary button: Start Free Ride.
- Action rows: Workout Library, Workout Builder.
- "Recent Workouts" section with the last saved session.

```kotlin
@Composable
fun HomeScreen(
  onNavigateToDevices: () -> Unit,
  onNavigateToWorkouts: () -> Unit,
  onStartFreeRide: () -> Unit,
  viewModel: HomeViewModel = viewModel()
) { ... }
```

**Step 2:** `HomeViewModel` reads the active BLE managers from `TrainerLoopApplication` and exposes `connectedTrainer`, `connectedHr`, `latestHrBpm`, and recent sessions from `SessionRepository`.

**Step 3:** Commit.

```bash
git add android/app/src/main/java/com/trainerloop/ui/home/HomeScreen.kt android/app/src/main/java/com/trainerloop/ui/home/HomeViewModel.kt
git commit -m "feat: add home dashboard screen"
```

---

### Task 1.3: Add bottom navigation scaffold

**Files:**
- Modify: `android/app/src/main/java/com/trainerloop/ui/TrainerLoopApp.kt`

**Step 1:** Wrap the `NavHost` in a `Scaffold` with a `NavigationBar` showing Home, Workouts, Ride, History, Profile.

```kotlin
@Composable
fun TrainerLoopApp(...) {
  val navController = rememberNavController()
  val currentBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = currentBackStackEntry?.destination?.route

  Scaffold(
    bottomBar = {
      if (currentRoute in Screen.bottomTabs.map { it.route }) {
        NavigationBar {
          Screen.bottomTabs.forEach { screen ->
            NavigationBarItem(
              selected = currentRoute == screen.route,
              onClick = { navController.navigate(screen.route) { popUpTo(Screen.Home.route) { saveState = true }; launchSingleTop = true; restoreState = true } },
              icon = { Icon(...) },
              label = { Text(...) }
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
    ) { ... }
  }
}
```

**Step 2:** Map tabs:
- `Home` → `HomeScreen`
- `Workouts` → `WorkoutLibraryScreen`
- `Ride` → placeholder or direct-to-devices if no workout active
- `History` → placeholder (future `HistoryScreen`)
- `Profile` → `SettingsScreen` restyled

**Step 3:** Commit.

```bash
git add android/app/src/main/java/com/trainerloop/ui/TrainerLoopApp.kt
git commit -m "feat: add bottom navigation scaffold"
```

---

## Phase 2 — Devices Screen

### Task 2.1: Repurpose `ConnectScreen` into `DevicesScreen`

**Files:**
- Rename: `android/app/src/main/java/com/trainerloop/ui/connect/ConnectScreen.kt` → `android/app/src/main/java/com/trainerloop/ui/devices/DevicesScreen.kt`
- Rename: `android/app/src/main/java/com/trainerloop/ui/connect/ConnectViewModel.kt` → `android/app/src/main/java/com/trainerloop/ui/devices/DevicesViewModel.kt`
- Modify all internal package references and `TrainerLoopApp` navigation.

**Step 1:** Update package names and class names to `DevicesScreen` / `DevicesViewModel`.

**Step 2:** Match the design layout:
- "Paired Devices" section at the top (trainer + HR with green checkmarks, battery, live HR).
- "Available Devices" section with other discovered devices.
- Large "Scan for Devices" button at the bottom.

**Step 3:** Implement real GATT connect/disconnect as in the original plan.

```kotlin
fun connectTrainer(context: Context, device: BleDevice) {
  val app = context.trainerLoopApp
  _uiState.value = _uiState.value.copy(isConnectingTrainer = true, error = null)
  val btDevice = resolveBluetoothDevice(context, device.address) ?: run { ... }
  app.attachTrainer(btDevice)
  viewModelScope.launch {
    val result = app.ftmsManager?.connect()
    if (result?.isSuccess == true) {
      app.ftmsControlManager?.connect()
      _uiState.value = _uiState.value.copy(connectedTrainer = device, isConnectingTrainer = false)
    } else {
      _uiState.value = _uiState.value.copy(isConnectingTrainer = false, error = ...)
    }
  }
}
```

**Step 4:** Commit.

```bash
git mv android/app/src/main/java/com/trainerloop/ui/connect/ConnectScreen.kt android/app/src/main/java/com/trainerloop/ui/devices/DevicesScreen.kt
git mv android/app/src/main/java/com/trainerloop/ui/connect/ConnectViewModel.kt android/app/src/main/java/com/trainerloop/ui/devices/DevicesViewModel.kt
git add android/app/src/main/java/com/trainerloop/ui/devices/ android/app/src/main/java/com/trainerloop/ui/TrainerLoopApp.kt
git commit -m "feat: repurpose connect screen into dedicated devices screen with real GATT connections"
```

---

## Phase 3 — Workout Library + Workout Detail

### Task 3.1: Enhance Workout Library cards

**Files:**
- Modify: `android/app/src/main/java/com/trainerloop/ui/library/WorkoutLibraryScreen.kt`
- Modify: `android/app/src/main/java/com/trainerloop/ui/components/IntervalTimeline.kt` or create `WorkoutMiniChart.kt`

**Step 1:** Add search bar and filter chips (All, Endurance, Sweet Spot, Threshold, VO2 Max) at the top.

**Step 2:** Each card shows:
- Mini workout chart (target profile)
- Title
- Duration
- IF / TSS (compute from normalized power estimate or hard-code per workout for now)

**Step 3:** Commit.

```bash
git add android/app/src/main/java/com/trainerloop/ui/library/WorkoutLibraryScreen.kt
git commit -m "feat: enhance workout library with search, filters, and mini charts"
```

---

### Task 3.2: Add `WorkoutDetailScreen`

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/ui/workout/detail/WorkoutDetailScreen.kt`
- Create: `android/app/src/main/java/com/trainerloop/ui/workout/detail/WorkoutDetailViewModel.kt`

**Step 1:** Show:
- Header: workout name, duration, IF, TSS.
- Full preview chart of the workout target profile.
- Description.
- Intervals list with phase color, name, duration, target FTP %.
- "Start Workout" primary button.

```kotlin
@Composable
fun WorkoutDetailScreen(
  workout: Workout,
  onStartWorkout: () -> Unit,
  onBack: () -> Unit
) { ... }
```

**Step 2:** Commit.

```bash
git add android/app/src/main/java/com/trainerloop/ui/workout/detail/WorkoutDetailScreen.kt android/app/src/main/java/com/trainerloop/ui/workout/detail/WorkoutDetailViewModel.kt
git commit -m "feat: add workout detail preview screen"
```

---

## Phase 4 — Wire Telemetry into the Workout Player

### Task 4.1: Update `WorkoutViewModel` to accept BLE managers

**Files:**
- Modify: `android/app/src/main/java/com/trainerloop/ui/workout/WorkoutViewModel.kt`
- Modify: `android/app/src/main/java/com/trainerloop/ui/workout/WorkoutViewModelFactory.kt`

**Step 1:** Change constructor to accept optional managers and a repository.

```kotlin
class WorkoutViewModel(
  private val workout: Workout,
  private val ftmsManager: FtmsManager? = null,
  private val hrManager: HrManager? = null,
  private val ftmsControlManager: FtmsControlManager? = null,
  private val sessionRepository: SessionRepository? = null
) : ViewModel() {
```

**Step 2:** Create `TelemetryRecorder` in `init` and start collecting.

```kotlin
private val telemetryRecorder: TelemetryRecorder? =
  if (ftmsManager != null && hrManager != null) {
    TelemetryRecorder(clock, ftmsManager, hrManager)
  } else null

init {
  telemetryRecorder?.startCollecting()

  viewModelScope.launch {
    telemetryRecorder?.latest?.collect { sample ->
      _uiState.value = _uiState.value.copy(
        currentPowerWatts = sample.powerWatts,
        currentCadenceRpm = sample.cadenceRpm,
        currentHrBpm = sample.hrBpm
      )
    }
  }

  viewModelScope.launch {
    telemetryRecorder?.samples?.collect { samples ->
      _uiState.value = _uiState.value.copy(samples = samples)
    }
  }

  // existing clock/coach collections ...
}
```

**Step 3:** Drive ERG writes from target changes.

```kotlin
private var ergWriteJob: Job? = null

init {
  // ...
  viewModelScope.launch {
    combine(
      clock.elapsedSec,
      _uiState.map { it.isRunning }.distinctUntilChanged(),
      _uiState.map { it.isErgEnabled }.distinctUntilChanged(),
      _uiState.map { it.targetRange }.distinctUntilChanged(),
      _uiState.map { it.intensityOffsetPct }.distinctUntilChanged()
    ) { elapsedSec, running, ergEnabled, targetRange, offset ->
      WorkoutControlTick(elapsedSec, running, ergEnabled, targetRange, offset)
    }.collect { tick ->
      handleControlTick(tick)
    }
  }
}

private data class WorkoutControlTick(
  val elapsedSec: Int,
  val running: Boolean,
  val ergEnabled: Boolean,
  val targetRange: TargetRange,
  val offsetPct: Int
)

private fun handleControlTick(tick: WorkoutControlTick) {
  if (!tick.running || tick.targetRange == TargetRange(0, 0)) return
  if (!tick.ergEnabled) {
    ftmsControlManager?.let { control ->
      ergWriteJob?.cancel()
      ergWriteJob = viewModelScope.launch { control.stopPause(stop = false) }
    }
    return
  }
  val mid = (tick.targetRange.low + tick.targetRange.high) / 2
  val factor = 1.0 + tick.offsetPct / 100.0
  val target = (mid * factor).toInt().coerceIn(0, 2000)
  ftmsControlManager?.let { control ->
    ergWriteJob?.cancel()
    ergWriteJob = viewModelScope.launch { control.setTargetPower(target) }
  }
}
```

**Step 4:** Remove the unused `setTelemetry(...)` method and synthetic sample creation in `tickCoach()`.

**Step 5:** Update `WorkoutViewModelFactory`.

```kotlin
class WorkoutViewModelFactory(
  private val workout: Workout,
  private val ftmsManager: FtmsManager? = null,
  private val hrManager: HrManager? = null,
  private val ftmsControlManager: FtmsControlManager? = null,
  private val sessionRepository: SessionRepository? = null
) : ViewModelProvider.Factory {

  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(WorkoutViewModel::class.java)) {
      return WorkoutViewModel(workout, ftmsManager, hrManager, ftmsControlManager, sessionRepository) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
  }
}
```

**Step 6:** Commit.

```bash
git add android/app/src/main/java/com/trainerloop/ui/workout/WorkoutViewModel.kt android/app/src/main/java/com/trainerloop/ui/workout/WorkoutViewModelFactory.kt
git commit -m "feat: wire BLE managers and telemetry recorder into WorkoutViewModel"
```

---

### Task 4.2: Pass real workout and managers from navigation

**Files:**
- Modify: `android/app/src/main/java/com/trainerloop/ui/TrainerLoopApp.kt`

**Step 1:** When a workout is selected in the library, store it in `TrainerLoopApplication` and navigate to detail.

```kotlin
composable(Screen.Workouts.route) {
  WorkoutLibraryScreen(
    onWorkoutSelected = { workout ->
      app.selectedWorkout = workout
      navController.navigate("workout_detail/${workout.id}")
    }
  )
}

composable("workout_detail/{workoutId}") {
  val workout = app.selectedWorkout ?: return@composable
  WorkoutDetailScreen(
    workout = workout,
    onStartWorkout = {
      navController.navigate(Screen.WorkoutPlayer.createRoute(sessionId = 1))
    },
    onBack = { navController.popBackStack() }
  )
}
```

**Step 2:** Update `WorkoutPlayer` route to retrieve managers from `TrainerLoopApplication`.

```kotlin
composable(Screen.WorkoutPlayer.route, arguments = ...) {
  val workout = app.selectedWorkout ?: sampleWorkout
  WorkoutScreen(
    workout = workout,
    viewModel = viewModel(
      factory = WorkoutViewModelFactory(
        workout = workout,
        ftmsManager = app.ftmsManager,
        hrManager = app.hrManager,
        ftmsControlManager = app.ftmsControlManager
      )
    ),
    onSessionFinished = { data -> ... }
  )
}
```

**Step 3:** Commit.

```bash
git add android/app/src/main/java/com/trainerloop/ui/TrainerLoopApp.kt
git commit -m "feat: pass selected workout and BLE managers to workout player"
```

---

### Task 4.3: Start/resume and pause/stop the trainer control point

**Files:**
- Modify: `android/app/src/main/java/com/trainerloop/ui/workout/WorkoutViewModel.kt`

**Step 1:** On `start()`, request control-point start.

```kotlin
fun start() {
  clock.start()
  updateFromClock()
  viewModelScope.launch {
    if (ftmsControlManager?.status?.value == FtmsControlStatus.READY) {
      ftmsControlManager?.startResume()
    }
  }
}
```

**Step 2:** On `pause()`, send pause.

```kotlin
fun pause() {
  clock.pause()
  viewModelScope.launch {
    ftmsControlManager?.stopPause(stop = false)
  }
}
```

**Step 3:** On `stop()`, send stop.

```kotlin
fun stop() {
  clock.stop()
  viewModelScope.launch {
    ftmsControlManager?.stopPause(stop = true)
  }
}
```

**Step 4:** Commit.

```bash
git add android/app/src/main/java/com/trainerloop/ui/workout/WorkoutViewModel.kt
git commit -m "feat: start/pause/stop trainer control point from workout controls"
```

---

## Phase 5 — Live Workout Chart

### Task 5.1: Create `WorkoutChart` composable

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/ui/components/WorkoutChart.kt`

**Step 1:** Implement a custom Canvas chart that draws:
- Target power as a stepped filled area across the full workout duration.
- Live actual power trace so far.
- A vertical line at current elapsed time.

```kotlin
package com.trainerloop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.data.model.Workout
import com.trainerloop.domain.WorkoutMath

@Composable
fun WorkoutChart(
  workout: Workout,
  samples: List<TelemetrySample>,
  elapsedSec: Int,
  modifier: Modifier = Modifier,
  maxPowerAxis: Int = 400
) {
  val totalDuration = remember(workout) { WorkoutMath.totalDurationSec(workout.segments) }
  Canvas(
    modifier = modifier
      .fillMaxWidth()
      .height(160.dp)
  ) {
    val width = size.width
    val height = size.height
    val padding = 8.dp.toPx()
    val chartHeight = height - padding * 2
    val chartTop = padding
    val chartBottom = height - padding

    fun xForTime(sec: Int): Float =
      if (totalDuration == 0) 0f else (sec / totalDuration.toFloat()) * width

    fun yForPower(power: Int): Float =
      chartBottom - (power / maxPowerAxis.toFloat()).coerceIn(0f, 1f) * chartHeight

    val step = (totalDuration / 200).coerceAtLeast(1)
    var sec = 0
    while (sec <= totalDuration) {
      val range = WorkoutMath.targetRangeAt(workout.segments, sec)
      val nextSec = (sec + step).coerceAtMost(totalDuration)
      val xStart = xForTime(sec)
      val xEnd = xForTime(nextSec)
      val yLow = yForPower(range.low)
      val yHigh = yForPower(range.high)
      drawRect(
        color = Color(0xFF4CAF78).copy(alpha = 0.25f),
        topLeft = Offset(xStart, yHigh),
        size = androidx.compose.ui.geometry.Size(xEnd - xStart, yLow - yHigh)
      )
      sec += step
    }

    if (samples.size >= 2) {
      val path = Path()
      samples.firstOrNull()?.let { first ->
        path.moveTo(xForTime(first.timeSec), yForPower(first.powerWatts))
      }
      samples.drop(1).forEach { sample ->
        path.lineTo(xForTime(sample.timeSec), yForPower(sample.powerWatts))
      }
      drawPath(path, color = Color(0xFF2196F3), style = Stroke(width = 3f))
    }

    val currentX = xForTime(elapsedSec)
    drawLine(
      color = Color.White,
      start = Offset(currentX, 0f),
      end = Offset(currentX, height),
      strokeWidth = 2f
    )
  }
}
```

**Step 2:** Commit.

```bash
git add android/app/src/main/java/com/trainerloop/ui/components/WorkoutChart.kt
git commit -m "feat: add live workout chart composable"
```

---

## Phase 6 — Workout Player UI (Tabbed Design)

### Task 6.1: Redesign `WorkoutScreen` to match the provided player design

**Files:**
- Modify: `android/app/src/main/java/com/trainerloop/ui/workout/WorkoutScreen.kt`
- Modify: `android/app/src/main/java/com/trainerloop/ui/components/MetricCard.kt`

**Step 1:** Create a top bar:
- Left: elapsed time with "DURATION" label.
- Center: ERG ON/OFF chip.
- Right: target power with "TARGET" label.

**Step 2:** Main metrics row with large values:
- Power (W)
- Heart Rate (bpm, with heart icon)
- Cadence (rpm)
- Time to interval (min)

**Step 3:** Chart section with current interval label (e.g., "Steady 75%") and the `WorkoutChart`.

**Step 4:** Footer info: elapsed, remaining, total.

**Step 5:** Intensity buttons: -5%, -1%, +1%, +5%.

**Step 6:** Controls: Pause / Skip (skip to next segment).

```kotlin
@Composable
fun WorkoutScreen(
  workout: Workout,
  viewModel: WorkoutViewModel = viewModel(factory = WorkoutViewModelFactory(workout)),
  onSessionFinished: (WorkoutFinishData) -> Unit
) { ... }
```

**Step 7:** Add `skipSegment()` to `WorkoutViewModel`.

```kotlin
fun skipSegment() {
  val nextSegmentStart = _uiState.value.segmentEndSec
  if (nextSegmentStart < WorkoutMath.totalDurationSec(workout.segments)) {
    seek(nextSegmentStart)
  }
}
```

**Step 8:** Commit.

```bash
git add android/app/src/main/java/com/trainerloop/ui/workout/WorkoutScreen.kt android/app/src/main/java/com/trainerloop/ui/components/MetricCard.kt android/app/src/main/java/com/trainerloop/ui/workout/WorkoutViewModel.kt
git commit -m "feat: redesign workout player with top bar, big metrics, chart, and skip"
```

---

### Task 6.2: Add swipeable stats/trainer tabs (optional but aligned with design)

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/ui/workout/WorkoutStatsPager.kt`
- Modify: `android/app/src/main/java/com/trainerloop/ui/workout/WorkoutScreen.kt`

**Step 1:** Create a pager with tabs: Main / Power / Trainer.

- **Main tab:** big metrics + chart + controls.
- **Power tab:** 3s Avg, Avg, NP, Max power; Avg/Max HR; Calories; Energy.
- **Trainer tab:** Resistance %, Power Smoothing, Temperature, Connection quality, Firmware, Control Mode.

**Step 2:** Use `androidx.compose.foundation.pager.HorizontalPager` (or `Accompanist Pager` if the BOM version does not include it).

**Step 3:** Commit.

```bash
git add android/app/src/main/java/com/trainerloop/ui/workout/WorkoutStatsPager.kt android/app/src/main/java/com/trainerloop/ui/workout/WorkoutScreen.kt
git commit -m "feat: add workout player stats and trainer tabs"
```

---

## Phase 7 — Workout Complete + Save/Export

### Task 7.1: Create `WorkoutCompleteScreen`

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/ui/complete/WorkoutCompleteScreen.kt`
- Rename/merge: `android/app/src/main/java/com/trainerloop/ui/summary/SessionSummaryScreen.kt` and `SessionSummaryViewModel.kt` into `complete/` (or keep summary VM as the data layer).

**Step 1:** Build the completion screen matching the design:
- Header: "Workout Complete", workout name, duration.
- Summary grid: TSS, IF, NP.
- Stats list: Avg Power, Avg Heart Rate, Calories, Total Work.
- Post-ride chart with tabs: Power / Heart Rate / Cadence.
- Bottom actions: Discard, Save, Share FIT.

```kotlin
@Composable
fun WorkoutCompleteScreen(
  viewModel: WorkoutCompleteViewModel,
  onDiscard: () -> Unit,
  onDone: () -> Unit
) { ... }
```

**Step 2:** Commit.

```bash
git add android/app/src/main/java/com/trainerloop/ui/complete/WorkoutCompleteScreen.kt
git commit -m "feat: add workout complete screen with summary and chart tabs"
```

---

### Task 7.2: Compute advanced summary metrics

**Files:**
- Modify: `android/app/src/main/java/com/trainerloop/ui/summary/SessionSummaryViewModel.kt` or create `android/app/src/main/java/com/trainerloop/ui/complete/WorkoutCompleteViewModel.kt`

**Step 1:** Compute TSS, IF, NP, calories, total work.

```kotlin
object WorkoutSummaryMath {
  fun normalizedPower(samples: List<TelemetrySample>): Int {
    if (samples.isEmpty()) return 0
    val windowSize = 30
    val rolling = samples.windowed(windowSize, 1, partialWindows = true)
      .map { window -> window.map { it.powerWatts }.average() }
    val avgFourth = rolling.map { kotlin.math.pow(it, 4.0) }.average()
    return kotlin.math.pow(avgFourth, 0.25).toInt()
  }

  fun intensityFactor(np: Int, ftp: Int): Double =
    if (ftp == 0) 0.0 else np / ftp.toDouble()

  fun tss(np: Int, ftp: Int, activeSec: Int): Int {
    val ifactor = intensityFactor(np, ftp)
    return ((activeSec * np * ifactor) / (ftp * 3600.0) * 100.0).toInt()
  }

  fun caloriesKcal(avgPower: Int, activeSec: Int): Int =
    ((avgPower * activeSec) / 1000.0 * 0.239).toInt()

  fun totalWorkKj(avgPower: Int, activeSec: Int): Int =
    (avgPower * activeSec) / 1000
}
```

**Step 2:** Read FTP from `ProfileRepository` / `DataStore`.

**Step 3:** Update UI state with these computed values.

**Step 4:** Commit.

```bash
git add android/app/src/main/java/com/trainerloop/ui/complete/WorkoutCompleteViewModel.kt
git commit -m "feat: compute TSS, IF, NP, calories, and total work for session summary"
```

---

### Task 7.3: Navigate to completion with recorded samples

**Files:**
- Modify: `android/app/src/main/java/com/trainerloop/ui/TrainerLoopApp.kt`
- Modify: `android/app/src/main/java/com/trainerloop/ui/workout/WorkoutViewModel.kt`
- Modify: `android/app/src/main/java/com/trainerloop/ui/workout/WorkoutScreen.kt`

**Step 1:** Add a finish event to `WorkoutViewModel`.

```kotlin
private val _finishEvent = MutableStateFlow<WorkoutFinishData?>(null)
val finishEvent: StateFlow<WorkoutFinishData?> = _finishEvent.asStateFlow()

data class WorkoutFinishData(
  val workoutName: String,
  val startTimeMs: Long,
  val samples: List<TelemetrySample>
)

private fun maybeEmitFinish() {
  val state = _uiState.value
  if (state.samples.isEmpty()) return
  _finishEvent.value = WorkoutFinishData(
    workoutName = workout.name,
    startTimeMs = state.startTimeMs,
    samples = state.samples
  )
}

fun consumeFinishEvent() {
  _finishEvent.value = null
}
```

**Step 2:** In `WorkoutScreen`, observe `finishEvent` and call `onSessionFinished`.

```kotlin
val finishData by viewModel.finishEvent.collectAsState()
LaunchedEffect(finishData) {
  finishData?.let {
    viewModel.consumeFinishEvent()
    onSessionFinished(it)
  }
}
```

**Step 3:** In `TrainerLoopApp`, store samples in `TrainerLoopApplication` and navigate to `WorkoutComplete`.

```kotlin
onSessionFinished = { data ->
  app.pendingSessionSamples = data.samples
  navController.navigate(
    Screen.WorkoutComplete.createRoute(
      sessionId = data.startTimeMs.toString(),
      workoutName = data.workoutName,
      startTimeMs = data.startTimeMs
    )
  )
}
```

**Step 4:** Update `Screen.WorkoutComplete` route and arguments.

```kotlin
object WorkoutComplete : Screen("workout_complete/{sessionId}/{workoutName}/{startTimeMs}") {
  fun createRoute(sessionId: String, workoutName: String, startTimeMs: Long): String {
    val encodedName = java.net.URLEncoder.encode(workoutName, "UTF-8")
    return "workout_complete/$sessionId/$encodedName/$startTimeMs"
  }
}
```

**Step 5:** In `TrainerLoopApp`, build `WorkoutCompleteViewModel` from pending samples.

```kotlin
composable(Screen.WorkoutComplete.route, arguments = ...) { backStackEntry ->
  val context = LocalContext.current
  val app = context.trainerLoopApp
  val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
  val workoutName = backStackEntry.arguments?.getString("workoutName")?.let {
    java.net.URLDecoder.decode(it, "UTF-8")
  } ?: "Workout"
  val startTimeMs = backStackEntry.arguments?.getLong("startTimeMs") ?: System.currentTimeMillis()
  val samples = app.pendingSessionSamples ?: emptyList()
  app.pendingSessionSamples = null

  WorkoutCompleteScreen(
    viewModel = WorkoutCompleteViewModel(
      application = context.applicationContext as Application,
      sessionId = sessionId,
      workoutName = workoutName,
      samples = samples,
      startTimeMs = startTimeMs
    ),
    onDiscard = { navController.popBackStack(Screen.Home.route, inclusive = false) },
    onDone = { navController.popBackStack(Screen.Home.route, inclusive = false) }
  )
}
```

**Step 6:** Commit.

```bash
git add android/app/src/main/java/com/trainerloop/ui/TrainerLoopApp.kt android/app/src/main/java/com/trainerloop/ui/workout/WorkoutViewModel.kt android/app/src/main/java/com/trainerloop/ui/workout/WorkoutScreen.kt android/app/src/main/java/com/trainerloop/ui/navigation/Screen.kt
git commit -m "feat: navigate to workout complete with recorded samples"
```

---

### Task 7.4: Save and share FIT

**Files:**
- Modify: `android/app/src/main/java/com/trainerloop/ui/complete/WorkoutCompleteViewModel.kt`
- Modify: `android/app/src/main/java/com/trainerloop/ui/components/FitShareHelper.kt`

**Step 1:** On init, save session to Room and create the FIT file (reuse existing `SessionSummaryViewModel` logic).

**Step 2:** Expose `onSave()`, `onShare()` handlers.

**Step 3:** `FitShareHelper` already supports share; ensure the MIME type is acceptable for Garmin/Strava. Use `application/octet-stream` as fallback if `application/fit` is not recognized by chooser apps.

```kotlin
intent.type = "application/fit"
```

**Step 4:** Commit.

```bash
git add android/app/src/main/java/com/trainerloop/ui/complete/WorkoutCompleteViewModel.kt android/app/src/main/java/com/trainerloop/ui/components/FitShareHelper.kt
git commit -m "feat: save session and share FIT from workout complete"
```

---

## Phase 8 — Profile / Settings Screen

### Task 8.1: Restyle `SettingsScreen` to match the profile design

**Files:**
- Modify: `android/app/src/main/java/com/trainerloop/ui/settings/SettingsScreen.kt`

**Step 1:** Add a header with avatar, name, FTP, weight.

**Step 2:** Group settings into rows: FTP Settings, Power Zones, Heart Rate Zones, Connected Apps, Trainer Settings, Units, Theme, Help & Support, About.

**Step 3:** Use `ProfileRepository` to load/save values.

**Step 4:** Commit.

```bash
git add android/app/src/main/java/com/trainerloop/ui/settings/SettingsScreen.kt
git commit -m "feat: restyle settings as profile screen"
```

---

## Phase 9 — Cleanup and Hardening

### Task 9.1: Remove dead code

**Files:**
- Modify: `android/app/src/main/java/com/trainerloop/ui/workout/WorkoutViewModel.kt`
- Delete: any leftover `connect/` directory after rename.

**Step 1:** Delete `setTelemetry(...)` and synthetic sample creation.

**Step 2:** Run lint.

Run: `./gradlew :app:lintDebug`  
Expected: `BUILD SUCCESSFUL`

**Step 3:** Commit.

```bash
git add android/app/src/main/java/com/trainerloop/ui/workout/WorkoutViewModel.kt
git commit -m "chore: remove dead telemetry stubs"
```

---

### Task 9.2: Add unit tests for WorkoutViewModel telemetry wiring

**Files:**
- Create: `android/app/src/test/java/com/trainerloop/ui/workout/WorkoutViewModelTest.kt`

**Step 1:** Test that fake managers update UI state.

```kotlin
@Test
fun `emitted telemetry updates current power cadence and hr`() = runTest {
  val ftms = FakeFtmsManager()
  val hr = FakeHrManager()
  val viewModel = WorkoutViewModel(
    workout = sampleWorkout(),
    ftmsManager = ftms,
    hrManager = hr
  )

  ftms.emit(IndoorBikeData(powerWatts = 250, cadenceRpm = 90.0))
  hr.emit(145)

  val state = viewModel.uiState.value
  assertEquals(250, state.currentPowerWatts)
  assertEquals(90, state.currentCadenceRpm)
  assertEquals(145, state.currentHrBpm)
}
```

**Step 2:** Run the test.

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.ui.workout.WorkoutViewModelTest"`  
Expected: `BUILD SUCCESSFUL`, tests pass.

**Step 3:** Commit.

```bash
git add android/app/src/test/java/com/trainerloop/ui/workout/WorkoutViewModelTest.kt
git commit -m "test: verify workout view model consumes telemetry"
```

---

### Task 9.3: Update device test checklist

**Files:**
- Modify: `docs/android-device-tests.md`

**Step 1:** Add a section for the new UI/UX flow:

```markdown
## Full Workout Flow Verification (Pixel 2 XL)

1. Home dashboard shows user header and connected-device cards.
2. Tap a device card → opens Devices screen.
3. Scan, connect trainer and HR → status shows Connected with battery/live HR.
4. Return Home; tap Workout Library.
5. Library shows filters, search, and mini-chart cards.
6. Select AE-2 Endurance → Workout Detail shows full chart and intervals.
7. Tap Start Workout → Workout Player opens.
8. Big metrics (Power, HR, Cadence, Time to Interval) update every second.
9. Live chart shows target band and actual power line.
10. Intensity buttons adjust ERG target; Pause/Skip work.
11. Stop or complete → Workout Complete screen with TSS/IF/NP and chart tabs.
12. Tap Share FIT → chooser opens; file opens in Garmin Connect / Strava.
13. Tap Save → session persisted to Room; return Home shows it in Recent Workouts.
```

**Step 2:** Commit.

```bash
git add docs/android-device-tests.md
git commit -m "docs: update Pixel 2 XL checklist for new UI flow"
```

---

## Phase 10 — Final Verification

### Task 10.1: Full build and test run

**Files:**
- All touched files

**Step 1:** Run the full unit-test suite.

Run: `./gradlew :app:testDebugUnitTest`  
Expected: `BUILD SUCCESSFUL`

**Step 2:** Run lint.

Run: `./gradlew :app:lintDebug`  
Expected: `BUILD SUCCESSFUL`

**Step 3:** Build debug APK.

Run: `./gradlew :app:assembleDebug`  
Expected: `BUILD SUCCESSFUL`

**Step 4:** Commit.

```bash
git commit -m "chore: verify build, tests, and lint for telemetry/chart/export integration"
```

---

## Appendix — Files Touches Summary

| File | Action | Why |
|---|---|---|
| `android/app/src/main/java/com/trainerloop/app/TrainerLoopApplication.kt` | Create | Application-scoped BLE manager holder + cross-screen data |
| `android/app/src/main/AndroidManifest.xml` | Modify | Register custom Application |
| `android/app/src/main/java/com/trainerloop/ui/navigation/Screen.kt` | Modify | Bottom-tab routes |
| `android/app/src/main/java/com/trainerloop/ui/home/HomeScreen.kt` | Create | Dashboard |
| `android/app/src/main/java/com/trainerloop/ui/home/HomeViewModel.kt` | Create | Dashboard state |
| `android/app/src/main/java/com/trainerloop/ui/TrainerLoopApp.kt` | Modify | Bottom nav + flow wiring |
| `android/app/src/main/java/com/trainerloop/ui/devices/DevicesScreen.kt` | Rename + modify | Dedicated BLE pairing screen |
| `android/app/src/main/java/com/trainerloop/ui/devices/DevicesViewModel.kt` | Rename + modify | Real GATT connect/disconnect |
| `android/app/src/main/java/com/trainerloop/ui/library/WorkoutLibraryScreen.kt` | Modify | Filters, search, mini charts |
| `android/app/src/main/java/com/trainerloop/ui/components/WorkoutMiniChart.kt` | Create | Library card charts |
| `android/app/src/main/java/com/trainerloop/ui/workout/detail/WorkoutDetailScreen.kt` | Create | Workout preview |
| `android/app/src/main/java/com/trainerloop/ui/workout/detail/WorkoutDetailViewModel.kt` | Create | Preview state |
| `android/app/src/main/java/com/trainerloop/ui/workout/WorkoutViewModel.kt` | Modify | Consume telemetry, drive ERG, finish event |
| `android/app/src/main/java/com/trainerloop/ui/workout/WorkoutViewModelFactory.kt` | Modify | Accept BLE managers |
| `android/app/src/main/java/com/trainerloop/ui/workout/WorkoutScreen.kt` | Modify | Player UI matching design |
| `android/app/src/main/java/com/trainerloop/ui/workout/WorkoutStatsPager.kt` | Create | Power / Trainer tabs |
| `android/app/src/main/java/com/trainerloop/ui/components/WorkoutChart.kt` | Create | Live power/target chart |
| `android/app/src/main/java/com/trainerloop/ui/components/MetricCard.kt` | Modify | Big metric style |
| `android/app/src/main/java/com/trainerloop/ui/complete/WorkoutCompleteScreen.kt` | Create | Completion summary |
| `android/app/src/main/java/com/trainerloop/ui/complete/WorkoutCompleteViewModel.kt` | Create | TSS/IF/NP, save, share |
| `android/app/src/main/java/com/trainerloop/ui/summary/SessionSummaryViewModel.kt` | Modify/merge | Reuse persistence logic |
| `android/app/src/main/java/com/trainerloop/ui/settings/SettingsScreen.kt` | Modify | Profile-style settings |
| `android/app/src/main/java/com/trainerloop/ui/components/FitShareHelper.kt` | Modify | FIT share MIME type |
| `android/app/src/test/java/com/trainerloop/ui/workout/WorkoutViewModelTest.kt` | Create | Telemetry wiring tests |
| `docs/android-device-tests.md` | Modify | Checklist update |

---

## Out of Scope / Follow-Up

- **Workout Builder** — the design shows a builder screen. This is a feature worth adding later but is not required to fix the current telemetry/chart/export gaps.
- **History screen** — a dedicated list of saved sessions is implied by the bottom nav; the data layer exists in Room, but the UI is future work.
- **Strava upload UI** — existing web-only `StravaAuthButton`/`StravaUploadModal`; native OAuth/upload is tracked in Phase 13 of the broad migration plan.
- **Advanced chart features** — smoothed power, HR trace, gaps, tooltip, pinch-to-zoom. Keep the first chart minimal and iterate.
- **Coach audio/text-to-speech** — not part of this gap.
- **Background sensor support (cadence/power as standalone devices)** — the current `FtmsManager` covers smart-trainer power/cadence; standalone cadence/power meters can be added later.

---

**Plan updated and saved to `docs/plans/2026-06-17-android-telemetry-chart-export.md`.**

Two execution options:

1. **Subagent-Driven (this session)** — I dispatch a fresh subagent per task, review between tasks, and iterate quickly.
2. **Parallel Session (separate)** — Open a new session using the `executing-plans` skill and run the tasks batch-by-batch with checkpoints.

Which approach would you like?
