# Audit Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the 18 prioritized audit findings for the TrainerLoop Android app, in the audit's recommended order: ride-loss risks first, then data trustworthiness, test guardrails, lifecycle/BLE, storage/perf, and finally build modernization.

**Architecture:** The pure-Kotlin core (workout math, parsers, coaching, simulation) stays untouched wherever possible; changes concentrate in Android glue — navigation (`TrainerLoopApp.kt`), session completion (`WorkoutCompleteViewModel`), telemetry (`TelemetryRecorder`), BLE managers, and the foreground service. Every behavioral change lands with a JVM unit test where the seam allows it; UI-only changes get explicit manual verification steps.

**Tech Stack:** Kotlin 1.9.25, Jetpack Compose (BOM 2024.12.01), Navigation-Compose, Room 2.6.1, kotlinx-coroutines/serialization, JUnit4 + MockK + Turbine.

## Global Constraints

- All work happens under `android/` unless a path says otherwise (repo root: `/Users/david.ramirez/Projects/trainer-loop`).
- Verification command after every task: `./gradlew testDebugUnitTest lint` (run from `android/`). The suite currently passes with 232 tests, 0 lint errors — it must stay green.
- Test framework is JUnit4 with `kotlinx-coroutines-test` (`runTest`, `StandardTestDispatcher`) and Turbine; follow the style of existing tests in `app/src/test/java/com/trainerloop/`.
- Kotlin style: 2-space indent, no wildcard imports (match existing files).
- One commit per task, message prefix per existing history (`fix:`, `feat:`, `refactor:`, `test:`, `build:`, `docs:`).
- Do not change FIT encoding, workout math, or parser output semantics except where a task explicitly says so.

**Recommended execution order (from the audit):** Phase 1 (Tasks 1–4) → Phase 2 (Tasks 5–8) → Phase 3 (Task 9) → Phase 4 (Tasks 10–12) → Phase 5 (Tasks 13–17) → Phase 6 (Task 18). Task 18 must be its own PR/branch, not mixed with product work.

---

## Phase 1 — Ride-loss and wrong-ride risks (findings #1, #8, #2, #3)

### Task 1: Protect Free Ride from system Back (finding #1)

**Files:**
- Modify: `app/src/main/java/com/trainerloop/ui/freeride/FreeRideScreen.kt`
- Test: manual (Compose UI; no instrumentation suite exists — see verification step)

**Interfaces:**
- Consumes: `FreeRideViewModel.stop()`, `uiState.elapsedSec`, `uiState.samples` (existing).
- Produces: no new API. Back gesture now routes through the existing stop-confirm dialog, exactly like `WorkoutScreen.kt:99`.

- [ ] **Step 1: Add the BackHandler and empty-ride exit**

In `FreeRideScreen.kt`, add the import and handler (mirror `WorkoutScreen.kt`):

```kotlin
import androidx.activity.compose.BackHandler
```

After `var showStopConfirm by remember { mutableStateOf(false) }` (line 59), add:

```kotlin
BackHandler(enabled = uiState.elapsedSec > 0) { showStopConfirm = true }
```

Then fix the confirm button (line ~109) so a ride with no samples still exits the screen instead of leaving the user stranded (stop() only emits a finish event when samples exist, `FreeRideViewModel.kt:188`):

```kotlin
confirmButton = {
  TextButton(onClick = {
    showStopConfirm = false
    val hadSamples = uiState.samples.isNotEmpty()
    viewModel.stop()
    if (!hadSamples) onExit()
  }) { Text("End ride") }
},
```

- [ ] **Step 2: Build and run the unit suite**

Run: `./gradlew testDebugUnitTest lint`
Expected: PASS (no behavior under test changed; compile check).

- [ ] **Step 3: Manual verification (emulator or device)**

Start a free ride, let it run ~10 s, press the hardware/gesture Back: the "End ride?" dialog must appear; "Keep riding" continues the ride; "End ride" lands on the completion screen. Back with `elapsedSec == 0` exits directly.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/trainerloop/ui/freeride/FreeRideScreen.kt
git commit -m "fix(freeride): route system Back through stop confirmation"
```

---

### Task 2: Capture the real wall-clock start time (finding #8)

Both ride modes compute `startTimeMs = now - elapsedSec * 1000`; elapsed stops during pauses, so a paused ride shifts its recorded start time later (`WorkoutViewModel.kt:477`, `FreeRideViewModel.kt:192`).

**Files:**
- Modify: `app/src/main/java/com/trainerloop/ui/workout/WorkoutViewModel.kt`
- Modify: `app/src/main/java/com/trainerloop/ui/freeride/FreeRideViewModel.kt`
- Test: `app/src/test/java/com/trainerloop/ui/workout/WorkoutViewModelStartTimeTest.kt` (create)

**Interfaces:**
- Produces: both ViewModels gain a constructor parameter `now: () -> Long = System::currentTimeMillis` (last parameter, defaulted — existing call sites unchanged). `WorkoutFinishData.startTimeMs` becomes the wall-clock time at first `start()` of the session.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.trainerloop.ui.workout

import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.Workout
import com.trainerloop.data.model.WorkoutSource
import com.trainerloop.data.model.WorkoutSegment
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutViewModelStartTimeTest {

  private val workout = Workout(
    id = "w", name = "W", description = null, source = WorkoutSource.MANUAL,
    segments = listOf(
      WorkoutSegment.FreeRide(id = "s", durationSec = 600, label = null, phase = SegmentPhase.WORK)
    )
  )

  @Test
  fun `finish start time is wall clock at first start, unaffected by pause`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    var wallClock = 1_000_000L
    val vm = WorkoutViewModel(workout, dispatcher = dispatcher, now = { wallClock })

    vm.start()
    advanceTimeBy(10_000)   // ride 10 s
    wallClock += 10_000
    vm.pause()
    advanceTimeBy(600_000)  // paused 10 min
    wallClock += 600_000
    vm.resume()
    advanceTimeBy(5_000)
    wallClock += 5_000
    vm.stop()
    advanceTimeBy(1_000)

    // Before the fix this would be wallClock - elapsed*1000 (start shifted 10 min late).
    assertEquals(1_000_000L, vm.finishEvent.value?.startTimeMs ?: -1L)
  }
}
```

Note: `finishEvent` is only emitted when samples exist; if the bare ViewModel produces no samples without an FTMS manager, assert via a new internal accessor instead — expose `internal fun sessionStartMsForTest(): Long?` — or attach the fake FTMS flow pattern already used in `WorkoutViewModelTest` (copy its fake-manager setup). Prefer reusing the existing fake setup.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.trainerloop.ui.workout.WorkoutViewModelStartTimeTest"`
Expected: FAIL (no `now` parameter yet / wrong start time).

- [ ] **Step 3: Implement in both ViewModels**

`WorkoutViewModel.kt` — add constructor param and session-start bookkeeping:

```kotlin
class WorkoutViewModel(
  ...,
  coachProfile: CoachProfile? = null,
  private val now: () -> Long = System::currentTimeMillis
) : ViewModel() {

  private var sessionStartMs: Long? = null
```

In `start()`: `if (sessionStartMs == null) sessionStartMs = now()`.
In `stop()`: after `maybeEmitFinish()`, reset `sessionStartMs = null`.
In `maybeEmitFinish()` replace the computed start:

```kotlin
startTimeMs = sessionStartMs ?: (now() - state.elapsedSec * 1000L),
```

`FreeRideViewModel.kt` — same pattern: param `private val now: () -> Long = System::currentTimeMillis`, set `sessionStartMs` in `start()`, use it in `stop()`'s `WorkoutFinishData(startTimeMs = sessionStartMs ?: (now() - _uiState.value.elapsedSec * 1000L), ...)`.

- [ ] **Step 4: Run the full suite**

Run: `./gradlew testDebugUnitTest`
Expected: PASS, including the new test.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/trainerloop/ui/workout/WorkoutViewModel.kt \
        app/src/main/java/com/trainerloop/ui/freeride/FreeRideViewModel.kt \
        app/src/test/java/com/trainerloop/ui/workout/WorkoutViewModelStartTimeTest.kt
git commit -m "fix(session): record wall-clock start time; pauses no longer shift it"
```

---

### Task 3: Explicit save/discard state machine on the completion screen (finding #2)

Today `WorkoutCompleteViewModel.init` auto-saves **and** auto-uploads before the user chooses (line 80); Discard fires an async delete and the screen navigates away immediately (`WorkoutCompleteScreen.kt:170-174`); every stopped ride is stored with `completed = true` (line 183).

**Files:**
- Modify: `app/src/main/java/com/trainerloop/ui/complete/WorkoutCompleteViewModel.kt`
- Modify: `app/src/main/java/com/trainerloop/ui/complete/WorkoutCompleteScreen.kt`
- Modify: `app/src/main/java/com/trainerloop/ui/complete/WorkoutCompleteViewModelFactory.kt` (add `completed` param passthrough)
- Modify: `app/src/main/java/com/trainerloop/ui/TrainerLoopApp.kt` (thread `completed` through the finish payload)
- Modify: `app/src/main/java/com/trainerloop/ui/workout/WorkoutViewModel.kt` + `FreeRideViewModel.kt` (`WorkoutFinishData.completedNaturally`)
- Test: `app/src/test/java/com/trainerloop/ui/complete/WorkoutCompleteViewModelTest.kt` (create or extend if it exists)

**Interfaces:**
- Produces: `WorkoutFinishData` gains `val completedNaturally: Boolean = false`. `WorkoutCompleteViewModel` gains constructor param `completed: Boolean = false` and new state fields `isSaving: Boolean`, keeps `isSaved`/`isDiscarded`. Behavior contract: **nothing is written or uploaded until `onSave()`**; `onDiscard()` completes its cleanup before `isDiscarded` becomes true; screen navigates on state, not on click.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.trainerloop.ui.complete

// Use a fake SessionRepository:
class FakeSessionRepository : SessionRepository(dao = /* not used */ mockk(relaxed = true)) {
  val saved = mutableListOf<SessionData>()
  val deleted = mutableListOf<String>()
  override suspend fun save(session: SessionData) { saved += session }
  override suspend fun deleteById(id: String) { deleted += id }
}
```

(If `SessionRepository.save`/`deleteById` are not open, make them `open` — the class is already `open`.)

Tests (Robolectric is not in the project; `WorkoutCompleteViewModel` is an `AndroidViewModel`, so inject `Application` via MockK relaxed mock as existing tests do, or if none exist, refactor first — see Step 3 note):

1. `init does not save or upload` — construct VM with non-empty samples; assert `fake.saved.isEmpty()` and `uiState.uploadStatus == null`.
2. `onSave saves once and marks completed flag from constructor` — call `onSave()` twice rapidly; assert `fake.saved.size == 1` and `fake.saved[0].completed == false` when constructed with `completed = false`.
3. `onDiscard before save deletes nothing from the repository` — assert `fake.deleted.isEmpty()` and `uiState.isDiscarded == true` after the coroutine completes.
4. `onDiscard after save deletes the session` — `onSave()` then `onDiscard()`; assert `fake.deleted == listOf(sessionId)`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.trainerloop.ui.complete.WorkoutCompleteViewModelTest"`
Expected: FAIL (init auto-saves today).

- [ ] **Step 3: Implement the state machine**

`WorkoutCompleteViewModel.kt`:

- Add constructor params: `private val completed: Boolean = false` (after `routeId`).
- **Remove** `saveSession()` from `init` (keep `computeSummary()`, coach data, `createFitFile()`, ramp-test block). The FIT file is a local cache — creating it eagerly is fine; it is deleted on discard.
- Add to `WorkoutCompleteUiState`: `val isSaving: Boolean = false`.
- Rewrite the actions:

```kotlin
fun onSave() {
  val state = _uiState.value
  if (state.isSaved || state.isSaving || state.isDiscarded || samples.isEmpty()) return
  _uiState.value = state.copy(isSaving = true)

  val samplesJson = Json.encodeToString(ListSerializer(TelemetrySample.serializer()), samples)
  val sessionData = SessionData(
    id = sessionId,
    workoutId = workoutId,
    workoutName = workoutName,
    startedAt = Instant.ofEpochMilli(startTimeMs).toString(),
    endedAt = Instant.now().toString(),
    durationSec = state.durationSec,
    samplesJson = samplesJson,
    coachEventsJson = coachJson,
    completed = completed,
    avgPower = state.avgPower,
    maxPower = state.maxPower,
    avgCadence = state.avgCadence,
    avgHr = state.avgHr,
    sessionType = sessionType,
    routeId = routeId
  )

  viewModelScope.launch {
    try {
      sessionRepository.save(sessionData)
      _uiState.value = _uiState.value.copy(isSaved = true, isSaving = false)
    } catch (e: Exception) {
      _uiState.value = _uiState.value.copy(
        isSaving = false, error = "Failed to save session: ${e.message}"
      )
      return@launch
    }
    uploadToIntervalsIcu(sessionData)
  }
}

fun onDiscard() {
  viewModelScope.launch {
    try {
      if (_uiState.value.isSaved) sessionRepository.deleteById(sessionId)
      _uiState.value.fitFile?.delete()
      _uiState.value = _uiState.value.copy(isDiscarded = true, isSaved = false, fitFile = null)
    } catch (e: Exception) {
      _uiState.value = _uiState.value.copy(error = "Failed to discard session: ${e.message}")
    }
  }
}
```

Delete the old private `saveSession()`.

`WorkoutCompleteScreen.kt` — navigate on state, not on click (replace the Discard button lambda at line ~170):

```kotlin
LaunchedEffect(uiState.isDiscarded) { if (uiState.isDiscarded) onDiscard() }
...
OutlinedButton(
  onClick = { viewModel.onDiscard() },
  ...
```

Also disable both buttons while `uiState.isSaving`.

`WorkoutFinishData` (in `WorkoutViewModel.kt`): add `val completedNaturally: Boolean = false`. Set it:
- `WorkoutViewModel.maybeEmitFinish()`: `completedNaturally = state.isComplete`.
- `FreeRideViewModel.stop()`: `completedNaturally = _uiState.value.routeComplete`.

`TrainerLoopApp.kt`: add a `FINISH_COMPLETED_KEY = "finish_completed"` savedStateHandle entry in `storeFinishPayload(...)` (new `completed: Boolean` parameter), read it in the `WorkoutComplete` composable, pass to the factory. Update both `storeFinishPayload` call sites to pass `data.completedNaturally`. Update `WorkoutCompleteViewModelFactory` to accept and forward `completed`.

- [ ] **Step 4: Run tests and full suite**

Run: `./gradlew testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Manual verification**

Finish a short workout: completion screen shows Save/Discard, no "Uploading…" until Save is pressed. Discard leaves History empty. Save then Discard removes the entry. An aborted ride (stopped early) saved from now on has `completed = false` in the DB.

- [ ] **Step 6: Commit**

```bash
git add -A app/src/main/java/com/trainerloop/ui/complete app/src/main/java/com/trainerloop/ui/TrainerLoopApp.kt \
        app/src/main/java/com/trainerloop/ui/workout/WorkoutViewModel.kt \
        app/src/main/java/com/trainerloop/ui/freeride/FreeRideViewModel.kt \
        app/src/test/java/com/trainerloop/ui/complete
git commit -m "fix(complete): explicit save/discard state machine; no auto-upload; honest completed flag"
```

---

### Task 4: Replace the application-memory workout handoff with ID-based routes (finding #3)

`TrainerLoopApp.kt:100` navigates to the player without selecting a workout ("Start Free Ride" plays the previous workout or the dev Sweet Spot fallback at line 252); process death loses `selectedWorkout` entirely.

**Files:**
- Create: `app/src/main/java/com/trainerloop/ui/library/BuiltInWorkouts.kt`
- Create: `app/src/main/java/com/trainerloop/domain/WorkoutResolver.kt`
- Modify: `app/src/main/java/com/trainerloop/ui/navigation/Screen.kt`
- Modify: `app/src/main/java/com/trainerloop/ui/TrainerLoopApp.kt`
- Modify: `app/src/main/java/com/trainerloop/ui/library/WorkoutLibraryViewModel.kt` (use `BuiltInWorkouts`)
- Modify: `app/src/main/java/com/trainerloop/app/TrainerLoopApplication.kt` (delete `selectedWorkout`)
- Test: `app/src/test/java/com/trainerloop/domain/WorkoutResolverTest.kt`

**Interfaces:**
- Produces:
  - `object BuiltInWorkouts { fun all(): List<Workout> }` — the three workouts moved verbatim from `WorkoutLibraryViewModel.builtInWorkouts()`.
  - `object WorkoutResolver { const val FREE_RIDE_ID = "free-ride"; fun resolve(workoutId: String, ftp: Int, imported: List<Workout>): Workout? }` — pure JVM, no Context. Resolution order: `FREE_RIDE_ID` → generated open-ended free ride; `RampTest.isRampTest(workoutId)` → `RampTest.generate(ftp)`; `BuiltInWorkouts.all()` by id; `imported` by id; else null.
  - `Screen.WorkoutPlayer` route becomes `"workout_player/{sessionId}/{workoutId}"`, `createRoute(sessionId: Long, workoutId: String)`.

- [ ] **Step 1: Extract `BuiltInWorkouts`**

Move the `builtInWorkouts()` list literal from `WorkoutLibraryViewModel.kt:216-255` into a new `object BuiltInWorkouts { fun all(): List<Workout> = listOf(/* same three workouts */) }` in the same package; `WorkoutLibraryViewModel.builtInWorkouts()` becomes `BuiltInWorkouts.all()`. Run the suite — pure move, must stay green.

- [ ] **Step 2: Write the failing resolver test**

```kotlin
package com.trainerloop.domain

import com.trainerloop.ui.library.BuiltInWorkouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutResolverTest {

  @Test
  fun `resolves built-in by id`() {
    val w = WorkoutResolver.resolve("sweet_spot", ftp = 200, imported = emptyList())
    assertEquals("sweet_spot", w?.id)
  }

  @Test
  fun `resolves imported by id`() {
    val custom = BuiltInWorkouts.all().first().copy(id = "custom_1")
    assertEquals("custom_1", WorkoutResolver.resolve("custom_1", 200, listOf(custom))?.id)
  }

  @Test
  fun `free ride id generates an open-ended free ride workout`() {
    val w = WorkoutResolver.resolve(WorkoutResolver.FREE_RIDE_ID, 200, emptyList())
    assertTrue(w!!.segments.all { it is com.trainerloop.data.model.WorkoutSegment.FreeRide })
  }

  @Test
  fun `ramp test id regenerates from ftp`() {
    val ramp = RampTest.generate(220)
    assertEquals(ramp.segments.size, WorkoutResolver.resolve(ramp.id, 220, emptyList())?.segments?.size)
  }

  @Test
  fun `unknown id returns null`() {
    assertNull(WorkoutResolver.resolve("nope", 200, emptyList()))
  }
}
```

- [ ] **Step 3: Run to verify failure, then implement `WorkoutResolver`**

```kotlin
package com.trainerloop.domain

import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.Workout
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.data.model.WorkoutSource
import com.trainerloop.ui.library.BuiltInWorkouts

object WorkoutResolver {
  const val FREE_RIDE_ID = "free-ride"
  private const val FREE_RIDE_MAX_SEC = 12 * 3600

  fun resolve(workoutId: String, ftp: Int, imported: List<Workout>): Workout? = when {
    workoutId == FREE_RIDE_ID -> Workout(
      id = FREE_RIDE_ID,
      name = "Free Ride",
      description = "Open-ended ride — stop whenever you like",
      source = WorkoutSource.MANUAL,
      segments = listOf(
        WorkoutSegment.FreeRide(
          id = "free", durationSec = FREE_RIDE_MAX_SEC, label = "Free Ride",
          phase = SegmentPhase.WORK
        )
      )
    )
    RampTest.isRampTest(workoutId) -> RampTest.generate(ftp)
    else -> BuiltInWorkouts.all().find { it.id == workoutId }
      ?: imported.find { it.id == workoutId }
  }
}
```

Run: `./gradlew testDebugUnitTest --tests "com.trainerloop.domain.WorkoutResolverTest"` — PASS.

- [ ] **Step 4: Route the ID through navigation**

`Screen.kt`:

```kotlin
object WorkoutPlayer : Screen("workout_player/{sessionId}/{workoutId}") {
  fun createRoute(sessionId: Long, workoutId: String): String =
    "workout_player/$sessionId/${Uri.encode(workoutId)}"
}
```

`TrainerLoopApp.kt`:
- Home: `onStartFreeRide = { navController.navigate(Screen.WorkoutPlayer.createRoute(System.currentTimeMillis(), WorkoutResolver.FREE_RIDE_ID)) }`; `onStartPlanned = { workout -> navController.navigate(Screen.WorkoutPlayer.createRoute(System.currentTimeMillis(), workout.id)) }` — delete the `selectedWorkout` write. **Caveat:** `onStartPlanned` workouts come from intervals.icu sync and land in `ImportedWorkoutStore`, so they resolve by id; if HomeScreen ever passes a workout not in the store, persist it to the store before navigating.
- Workouts tab: `onWorkoutSelected` navigates to `WorkoutDetail.createRoute(workout.id)` without `selectedWorkout`; `onStartRampTest` navigates with `workoutId = RampTest.generate(ftp).id`.
- `WorkoutDetail` composable: resolve by the `workoutId` argument:

```kotlin
val workoutId = backStackEntry.arguments?.getString("workoutId") ?: return@composable
val ftp = remember { com.trainerloop.data.repository.ProfileRepository(context).getProfileSync().ftp }
val workout = remember(workoutId) {
  com.trainerloop.domain.WorkoutResolver.resolve(
    workoutId, ftp, com.trainerloop.ui.library.ImportedWorkoutStore.load(context)
  )
}
if (workout == null) { LaunchedEffect(Unit) { navController.popBackStack() }; return@composable }
```

- `WorkoutPlayer` composable: add `navArgument("workoutId") { type = NavType.StringType }`, resolve identically (replacing `app.selectedWorkout ?: sampleWorkout`), pop back with a log if null. Delete the `sampleWorkout` block at the bottom of the file.
- `TrainerLoopApplication.kt`: delete `var selectedWorkout: Workout? = null`.

- [ ] **Step 5: Run the full suite + manual verification**

`./gradlew testDebugUnitTest lint` — PASS. Manually: Home → "Start Free Ride" opens an open-ended free ride (not the last workout); pick a library workout → detail → start plays that workout; kill the app process on the player screen and relaunch — no crash, no wrong workout.

- [ ] **Step 6: Commit**

```bash
git add -A app/src/main/java/com/trainerloop app/src/test/java/com/trainerloop/domain/WorkoutResolverTest.kt
git commit -m "fix(nav): resolve workouts by id in the route; drop application-memory handoff"
```

---

## Phase 2 — Trustworthy sensor data and coaching (findings #4, #5, #6, #7)

### Task 5: Model sensor freshness end-to-end (finding #4)

FTMS/HR StateFlows retain their last value after a silent drop; `TelemetryRecorder` treats any non-null value as current (`TelemetryRecorder.kt:74`), and `WorkoutViewModel.tickLiveCoach` (line 543) rebuilds samples without the dropout flag.

**Files:**
- Create: `app/src/main/java/com/trainerloop/ble/model/Stamped.kt`
- Modify: `app/src/main/java/com/trainerloop/ble/FtmsManager.kt`
- Modify: `app/src/main/java/com/trainerloop/ble/HrManager.kt`
- Modify: `app/src/main/java/com/trainerloop/domain/TelemetryRecorder.kt`
- Modify: `app/src/main/java/com/trainerloop/ui/workout/WorkoutViewModel.kt`
- Test: extend `app/src/test/java/com/trainerloop/domain/TelemetryRecorderTest.kt` (exists — follow its fixtures)

**Interfaces:**
- Produces:
  - `data class Stamped<T>(val value: T, val atMs: Long)` in `com.trainerloop.ble.model`.
  - `FtmsManager.data: StateFlow<Stamped<IndoorBikeData>?>`, `HrManager.heartRate: StateFlow<Stamped<Int>?>` — stamped with `android.os.SystemClock.elapsedRealtime()` at packet arrival (managers) and injectable in the recorder.
  - `TelemetryRecorder` constructor gains `now: () -> Long = android.os.SystemClock::elapsedRealtime`; `DataProvider` fields become the stamped flows. A sample is a dropout when FTMS data is null **or** older than `STALE_AFTER_MS = 3_000`; HR older than 5 s records as `hrBpm = 0`.

- [ ] **Step 1: Write the failing recorder test**

Add to `TelemetryRecorderTest` (adapting its existing fake `DataProvider` fixtures to `Stamped`):

```kotlin
@Test
fun `sample is flagged dropout when ftms data is stale`() = runTest {
  var nowMs = 0L
  val data = MutableStateFlow<Stamped<IndoorBikeData>?>(null)
  val hr = MutableStateFlow<Stamped<Int>?>(null)
  val recorder = TelemetryRecorder(
    clock, TelemetryRecorder.DataProvider(data, hr),
    dispatcher = StandardTestDispatcher(testScheduler),
    now = { nowMs }
  )
  recorder.startCollecting()
  clock.start()

  data.value = Stamped(IndoorBikeData(powerWatts = 150, cadenceRpm = 90.0), atMs = 0L)
  advanceTimeBy(2_000); nowMs = 2_000
  assertFalse(recorder.latest.value.dropout)   // fresh

  advanceTimeBy(4_000); nowMs = 6_000          // no new packet for 6 s
  assertTrue(recorder.latest.value.dropout)    // stale → dropout
}
```

(Match `IndoorBikeData`'s real constructor from the existing test file.)

- [ ] **Step 2: Run to verify it fails** (won't compile — signatures don't exist yet; that counts).

- [ ] **Step 3: Implement**

`Stamped.kt`:

```kotlin
package com.trainerloop.ble.model

data class Stamped<T>(val value: T, val atMs: Long)
```

`FtmsManager.kt`: `_data` becomes `MutableStateFlow<Stamped<IndoorBikeData>?>(null)`; in the notification collector: `_data.value = Stamped(parsed, android.os.SystemClock.elapsedRealtime())`. `disconnect()` still nulls it. `HrManager.kt`: same treatment on its heart-rate flow.

`TelemetryRecorder.kt`:

```kotlin
class TelemetryRecorder(
  private val clock: WorkoutClock,
  private val dataProvider: DataProvider,
  private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
  private val stamper: com.trainerloop.domain.sim.SampleStamper? = null,
  private val now: () -> Long = android.os.SystemClock::elapsedRealtime
) {
  data class DataProvider(
    val data: StateFlow<Stamped<IndoorBikeData>?>,
    val heartRate: StateFlow<Stamped<Int>?>
  )
```

In the tick lambda:

```kotlin
val nowMs = now()
val ftmsFresh = ftmsData != null && nowMs - ftmsData.atMs <= STALE_AFTER_MS
val hrFresh = hrBpm != null && nowMs - hrBpm.atMs <= HR_STALE_AFTER_MS
val dropout = !ftmsFresh

if (ftmsFresh) {
  lastDataReceivedAtSec = elapsedSec
  ftmsData!!.value.powerWatts?.let { lastPowerWatts = it }
  ftmsData.value.cadenceRpm?.let { lastCadenceRpm = it.toInt() }
}
if (hrFresh) lastHrBpm = hrBpm!!.value

val sample = TelemetrySample(
  ...,
  hrBpm = if (hrFresh) lastHrBpm else 0,
  dropout = dropout,
  ...
)
```

with `STALE_AFTER_MS = 3_000L`, `HR_STALE_AFTER_MS = 5_000L` as companion constants. Update the secondary `(clock, ftms, hr, ...)` constructor for the new flow types.

`WorkoutViewModel.kt`:
- The fast-path HR collector (line ~210) now maps `Stamped<Int>` → `it.value`.
- Add `val sensorDropout: Boolean = true` to `WorkoutUiState`; set it in the `recorder.latest` collector from `sample.dropout`.
- `tickLiveCoach` reconstructed sample: `TelemetrySample(..., dropout = state.sensorDropout)`.

Fix the other `DataProvider` users the compiler flags (fakes in tests: wrap values in `Stamped(value, atMs = <current fake time>)`).

- [ ] **Step 4: Run the full suite** — PASS (update broken fixtures as part of this step).

- [ ] **Step 5: Commit**

```bash
git add -A app/src/main/java/com/trainerloop/ble app/src/main/java/com/trainerloop/domain/TelemetryRecorder.kt \
        app/src/main/java/com/trainerloop/ui/workout/WorkoutViewModel.kt app/src/test
git commit -m "fix(telemetry): timestamp sensor data; flag stale power/HR as dropout end-to-end"
```

---

### Task 6: Preserve session samples when managers change (finding #5)

Attaching HR mid-ride or reattaching a trainer swaps in a **new, empty** `TelemetryRecorder` (`WorkoutViewModel.kt:163-180`, `FreeRideViewModel.kt:103-115`), erasing everything recorded so far.

**Files:**
- Modify: `app/src/main/java/com/trainerloop/domain/TelemetryRecorder.kt`
- Modify: `app/src/main/java/com/trainerloop/ui/workout/WorkoutViewModel.kt`
- Modify: `app/src/main/java/com/trainerloop/ui/freeride/FreeRideViewModel.kt`
- Test: extend `TelemetryRecorderTest.kt`

**Interfaces:**
- Produces: `TelemetryRecorder` constructor gains `initialSamples: List<TelemetrySample> = emptyList()` (both constructors); `_samples` starts from it and the timeSec-dedup guard naturally continues after the last inherited sample.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `recorder seeded with prior samples appends instead of restarting`() = runTest {
  val prior = listOf(TelemetrySample(timeSec = 1, powerWatts = 100, cadenceRpm = 80, hrBpm = 0))
  val recorder = TelemetryRecorder(clock, provider, dispatcher, stamper = null,
    now = { 0L }, initialSamples = prior)
  recorder.startCollecting()
  // drive the clock to t=2 with fresh data (reuse existing fixture helpers)
  ...
  assertEquals(listOf(1, 2), recorder.samples.value.map { it.timeSec })
}
```

- [ ] **Step 2: Run to verify failure, then implement**

`TelemetryRecorder.kt`: add the param, `private val _samples = MutableStateFlow(initialSamples)`. The existing append guard (`existing.last().timeSec < elapsedSec`) already prevents duplicates.

Both ViewModels' manager-swap collectors — read the old samples **before** creating the replacement:

```kotlin
.collect { (ftms, hr) ->
  val previous = recorder.value
  val carried = previous?.samples?.value ?: emptyList()
  recorder.value = if (ftms != null) {
    TelemetryRecorder(clock, ftms, hr, dispatcher, virtualRide, initialSamples = carried)
      .also { it.startCollecting() }
  } else null
  previous?.stop()
}
```

(`FreeRideViewModel` passes `tracker` as its stamper; keep that.) Note `stop()`/`reset()` in `WorkoutViewModel.stop()` already clears state for the next session; `reset()` must also clear inherited samples — it sets `_samples.value = emptyList()`, which it already does.

- [ ] **Step 3: Run the full suite** — PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/trainerloop/domain/TelemetryRecorder.kt \
        app/src/main/java/com/trainerloop/ui/workout/WorkoutViewModel.kt \
        app/src/main/java/com/trainerloop/ui/freeride/FreeRideViewModel.kt \
        app/src/test/java/com/trainerloop/domain/TelemetryRecorderTest.kt
git commit -m "fix(telemetry): carry recorded samples across recorder swaps mid-ride"
```

---

### Task 7: Apply every accepted coach action (finding #6)

`acceptSuggestion` (`WorkoutViewModel.kt:381-388`) only executes `ExtendRecovery`; the other three `CoachAction`s are accepted in the UI but never applied.

**Files:**
- Modify: `app/src/main/java/com/trainerloop/ui/workout/WorkoutViewModel.kt`
- Test: extend `app/src/test/java/com/trainerloop/ui/workout/WorkoutViewModelTest.kt` (or create `WorkoutViewModelCoachActionTest.kt`)

**Interfaces:**
- Produces: `internal fun applyCoachAction(action: CoachAction)` on `WorkoutViewModel` (called from `acceptSuggestion`, directly testable). New private `skipToCooldown()`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `adjust intensity up action raises the offset`() = runTest {
  vm.applyCoachAction(CoachAction.AdjustIntensityUp(percent = 5))
  assertEquals(5, vm.uiState.value.intensityOffsetPct)
}

@Test
fun `adjust intensity down clamps at -20`() = runTest {
  repeat(6) { vm.applyCoachAction(CoachAction.AdjustIntensityDown(percent = 5)) }
  assertEquals(-20, vm.uiState.value.intensityOffsetPct)
}

@Test
fun `skip remaining seeks to the first cooldown segment`() = runTest {
  // workout fixture: WORK(300) + WORK(300) + COOLDOWN(120)
  vm.start(); advanceTimeBy(10_000)
  vm.applyCoachAction(CoachAction.SkipRemainingOnIntervals)
  advanceTimeBy(1_000)
  assertEquals(2, vm.uiState.value.segmentIndex)
}
```

- [ ] **Step 2: Run to verify failure, then implement**

```kotlin
fun acceptSuggestion(suggestionId: String) {
  viewModelScope.launch {
    coachEngine.accept(suggestionId)?.action?.let { applyCoachAction(it) }
  }
}

internal fun applyCoachAction(action: CoachAction) {
  when (action) {
    is CoachAction.AdjustIntensityUp -> setIntensityOffset(
      _uiState.value.intensityOffsetPct + action.percent
    )
    is CoachAction.AdjustIntensityDown -> setIntensityOffset(
      _uiState.value.intensityOffsetPct - action.percent
    )
    is CoachAction.ExtendRecovery -> extendCurrentRecovery(action.seconds)
    CoachAction.SkipRemainingOnIntervals -> skipToCooldown()
  }
}

private fun setIntensityOffset(pct: Int) {
  _uiState.value = _uiState.value.copy(intensityOffsetPct = pct.coerceIn(-20, 20))
  updateFromClock()
}

/** Jump to the first COOLDOWN at/after the current segment, or end the workout. */
private fun skipToCooldown() {
  val currentIdx = _uiState.value.segmentIndex
  val cooldownIdx = segments.withIndex()
    .firstOrNull { (i, seg) -> i >= currentIdx && seg.phase == SegmentPhase.COOLDOWN }?.index
  if (cooldownIdx != null) {
    seek(segments.take(cooldownIdx).sumOf { it.durationSec })
  } else {
    seek(WorkoutMath.totalDurationSec(segments))
  }
}
```

Also refactor the four existing `adjustIntensity*/fineIntensity*` functions to call `setIntensityOffset` (removes the duplicated clamp — and fixes the pre-existing gap that they never called `updateFromClock()`).

- [ ] **Step 3: Run the full suite** — PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/trainerloop/ui/workout/WorkoutViewModel.kt app/src/test/java/com/trainerloop/ui/workout
git commit -m "fix(coach): apply all accepted coach actions, not only ExtendRecovery"
```

---

### Task 8: Remove hidden 250 W FTP assumptions and honor ERG bias (finding #7)

**Files:**
- Modify: `app/src/main/java/com/trainerloop/ui/library/WorkoutImportContract.kt`
- Modify: `app/src/main/java/com/trainerloop/ui/library/WorkoutLibraryViewModel.kt`
- Modify: `app/src/main/java/com/trainerloop/ui/workout/WorkoutViewModel.kt`
- Test: extend `WorkoutViewModelTest.kt`; library behavior covered by compile-enforced parameter threading

- [ ] **Step 1: Failing test for ERG bias**

```kotlin
@Test
fun `initial intensity offset honors saved erg bias`() = runTest {
  val vm = WorkoutViewModel(workout, dispatcher = dispatcher,
    userProfile = UserProfile(ergBiasPct = 5))
  assertEquals(5, vm.uiState.value.intensityOffsetPct)
}
```

- [ ] **Step 2: Implement**

1. `WorkoutViewModel` initial state: `_uiState = MutableStateFlow(WorkoutUiState(segments = workout.segments, elevationProfile = route?.expectedAltitudeM, intensityOffsetPct = userProfile.ergBiasPct.coerceIn(-20, 20)))`. Also change `stop()`'s reset from `intensityOffsetPct = 0` to `intensityOffsetPct = userProfile.ergBiasPct.coerceIn(-20, 20)`.
2. `WorkoutImportContract.kt:23`: remove the `ftp: Int = 250` default → `ftp: Int` (required).
3. `WorkoutLibraryViewModel.importWorkout` (line ~133): pass `profileRepository.getProfileSync().ftp` to `WorkoutImportHelper.importWorkout(context, uri, ftp)`.
4. `WorkoutLibraryViewModel.categorize` / `toListItem` (lines 184–207): `WorkoutSummaryMath.workoutStats(workout)` uses an FTP default of 250 — check its actual signature in `domain/WorkoutSummaryMath.kt`; if it takes an `ftp` parameter, thread `profileRepository.getProfileSync().ftp` through `toListItem()` and remove any default; if the 250 lives inside `workoutStats`, make the parameter required and fix all callers with the profile FTP.

- [ ] **Step 3: Run the full suite** — PASS.

- [ ] **Step 4: Commit**

```bash
git add -A app/src/main/java/com/trainerloop/ui/library app/src/main/java/com/trainerloop/ui/workout \
        app/src/main/java/com/trainerloop/domain app/src/test
git commit -m "fix(library,workout): use profile FTP everywhere; apply saved ERG bias to initial state"
```

---

## Phase 3 — Guardrails before deeper surgery (finding #15)

### Task 9: Lifecycle/migration guardrails, docs, CI

**Files:**
- Modify: `/Users/david.ramirez/Projects/trainer-loop/AGENTS.md` (currently describes a React/npm app)
- Track: `android/README.md` (already written, untracked)
- Modify: `app/build.gradle.kts` (Room schema export, room-testing dep)
- Modify: `app/src/main/java/com/trainerloop/data/source/local/AppDatabase.kt` (`exportSchema = true` if currently false)
- Create: `app/src/androidTest/java/com/trainerloop/data/source/local/MigrationTest.kt`
- Create: `/Users/david.ramirez/Projects/trainer-loop/.github/workflows/android-ci.yml`

- [ ] **Step 1: Rewrite AGENTS.md** — replace the React/npm content with the Android reality: module layout (`android/app`), build/test commands (`./gradlew testDebugUnitTest lint` from `android/`), code style (2-space Kotlin), and a pointer to `android/README.md`. Then `git add android/README.md`.

- [ ] **Step 2: Enable Room schema export**

`app/build.gradle.kts` inside `android {}`:

```kotlin
defaultConfig {
  ...
  javaCompileOptions {
    annotationProcessorOptions {
      arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
    }
  }
}
```

(For kapt use `kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }`.) In `AppDatabase.kt` ensure `@Database(..., exportSchema = true)`. Build once (`./gradlew :app:kaptDebugKotlin` or `assembleDebug`) and commit the generated `app/schemas/*.json`.

- [ ] **Step 3: Add a migration test scaffold**

```kotlin
dependencies {
  androidTestImplementation("androidx.room:room-testing:2.6.1")
}
```

`MigrationTest.kt` using `MigrationTestHelper` — at the current schema version it just opens the DB at version N and asserts creation succeeds; future migrations extend it:

```kotlin
@RunWith(AndroidJUnit4::class)
class MigrationTest {
  @get:Rule
  val helper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    AppDatabase::class.java
  )

  @Test
  fun createFromCurrentSchema() {
    helper.createDatabase("migration-test", /* version = */ CURRENT_VERSION).close()
  }
}
```

(Read the `version =` value from `AppDatabase.kt` and use it literally.)

- [ ] **Step 4: Add CI**

`.github/workflows/android-ci.yml`:

```yaml
name: android-ci
on:
  push: { branches: [main] }
  pull_request:
jobs:
  test:
    runs-on: ubuntu-latest
    defaults: { run: { working-directory: android } }
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: "17" }
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew testDebugUnitTest lint
```

- [ ] **Step 5: Verify locally and commit**

Run `./gradlew testDebugUnitTest lint` — PASS.

```bash
git add ../AGENTS.md README.md app/build.gradle.kts app/schemas app/src/androidTest \
        ../.github/workflows/android-ci.yml app/src/main/java/com/trainerloop/data/source/local/AppDatabase.kt
git commit -m "test(dx): CI, Room schema export + migration test scaffold, honest AGENTS.md, track README"
```

---

## Phase 4 — Lifecycle, BLE, and shared state (findings #10, #11, #12)

### Task 10: Make the foreground service authoritative and pause-safe (finding #10)

Notification "Stop" only kills the service, not the ride (`WorkoutForegroundService.kt:29`); pausing removes the service entirely (screens stop it whenever `isRunning` is false, `WorkoutScreen.kt:163`, `FreeRideScreen.kt:80`).

**Files:**
- Modify: `app/src/main/java/com/trainerloop/app/TrainerLoopApplication.kt`
- Modify: `app/src/main/java/com/trainerloop/app/WorkoutForegroundService.kt`
- Modify: `app/src/main/java/com/trainerloop/ui/workout/WorkoutScreen.kt`
- Modify: `app/src/main/java/com/trainerloop/ui/freeride/FreeRideScreen.kt`

**Interfaces:**
- Produces: `TrainerLoopApplication.stopRequests: MutableSharedFlow<Unit>` (`extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST`). Notification Stop emits into it; the active ride screen collects it and calls the ViewModel's `stop()` (same path as the on-screen Stop after confirmation — notification stop skips the dialog, saving the ride so far). Service lifetime = whole session (running **or paused**), not just running.

- [ ] **Step 1: Application-level stop channel**

```kotlin
// TrainerLoopApplication
val stopRequests = MutableSharedFlow<Unit>(
  extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
)
```

- [ ] **Step 2: Service emits instead of self-destructing**

`WorkoutForegroundService.onStartCommand` `ACTION_STOP` branch:

```kotlin
ACTION_STOP -> {
  (application as? TrainerLoopApplication)?.stopRequests?.tryEmit(Unit)
  // The ride screen tears the session down and calls stop(context),
  // which stops the service through the normal path.
  return START_NOT_STICKY
}
```

- [ ] **Step 3: Screens keep the service for the whole session and react to stop requests**

In both `WorkoutScreen.kt` and `FreeRideScreen.kt`, replace the `LaunchedEffect(uiState.isRunning)` start/stop block with session-scoped logic:

```kotlin
// Service lives while a session exists (running or paused); paused rides keep
// process protection but the service holds no wake lock (isRunning = false).
val sessionActive = uiState.elapsedSec > 0 || uiState.isRunning
LaunchedEffect(sessionActive, uiState.isRunning) {
  if (sessionActive) {
    WorkoutForegroundService.start(context, uiState.currentPowerWatts, formatDuration(uiState.elapsedSec))
    if (!uiState.isRunning) {
      WorkoutForegroundService.update(context, uiState.currentPowerWatts, formatDuration(uiState.elapsedSec), false)
    }
  } else {
    WorkoutForegroundService.stop(context)
  }
}
DisposableEffect(Unit) {
  onDispose { WorkoutForegroundService.stop(context) }
}
LaunchedEffect(Unit) {
  context.trainerLoopApp.stopRequests.collect { viewModel.stop() }
}
```

(`FreeRideScreen` uses `formatTime` instead of `formatDuration`.) The notification already renders a non-ongoing "paused" card when `isRunning = false`, and `updateWakeLock(false)` releases the wake lock — that behavior is reused, not rewritten.

- [ ] **Step 4: Manual verification**

Start a workout → background the app → notification Stop: the ride stops **and** the completion screen shows the ride so far when you return. Pause a ride → notification stays (not ongoing), `adb shell dumpsys power | grep TrainerLoop` shows no held wake lock. Finish/exit → notification gone.

- [ ] **Step 5: Run suite, commit**

```bash
git add app/src/main/java/com/trainerloop/app app/src/main/java/com/trainerloop/ui/workout/WorkoutScreen.kt \
        app/src/main/java/com/trainerloop/ui/freeride/FreeRideScreen.kt
git commit -m "fix(service): notification Stop ends the ride; paused rides keep the service without a wake lock"
```

---

### Task 11: Bound BLE connection setup and report real subscription success (finding #11)

`BleConnection.connect()`/reconnect await the GATT callback with **no timeout** (`BleConnection.kt:83,138`); `FtmsManager.connect()` returns success even when the Indoor Bike Data characteristic is missing or the descriptor write fails (`FtmsManager.kt:56-72`).

**Files:**
- Modify: `app/src/main/java/com/trainerloop/ble/BleConnection.kt`
- Modify: `app/src/main/java/com/trainerloop/ble/FtmsManager.kt`
- Modify: `app/src/main/java/com/trainerloop/ble/FtmsControlManager.kt`, `HrManager.kt`, `ZwiftClickManager.kt` (same pattern where they enable notifications)
- Test: extend existing BLE tests where fakes exist; otherwise compile + manual

- [ ] **Step 1: Connection timeout**

In `connect()` and `reconnectWithBackoff()`, wrap the await:

```kotlin
val success: Boolean = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
  try { callback.connectionResult.await() } catch (e: Exception) { false }
} ?: false
```

with `private const val CONNECT_TIMEOUT_MS = 20_000L` in the companion. On timeout the existing failure branch closes the GATT and reports `DISCONNECTED` (connect) or advances the backoff attempt (reconnect).

- [ ] **Step 2: Truthful notification setup**

`BleConnection.enableNotifications` — on descriptor-write failure, fail instead of logging:

```kotlin
if (!ok) {
  BleLog.w("Descriptor write for $uuid returned GATT failure (indicate=$useIndicate)")
  throw IllegalStateException("CCCD write failed for $uuid")
}
...
} else {
  deferred.cancel()
  throw IllegalStateException("writeDescriptor could not start for $uuid")
}
```

(Missing CCCD keeps its warning-only path — some peripherals notify without one.)

`FtmsManager.kt`: make `subscribeToNotifications` return `Boolean` and await the enable step before declaring success:

```kotlin
suspend fun connect(): Result<Unit> {
  readDeviceInfo(connection)
  val subscribed = subscribeToNotifications(connection)
  if (!subscribed) {
    return Result.failure(Exception("FTMS Indoor Bike Data subscription failed"))
  }
  connection.addReconnectHandler { readDeviceInfo(connection); subscribeToNotifications(connection) }
  _isConnected.value = true
  return Result.success(Unit)
}

private suspend fun subscribeToNotifications(conn: BleConnection): Boolean {
  val dataChar = conn.getCharacteristic(BleConstants.FTMS_SERVICE, BleConstants.INDOOR_BIKE_DATA)
    ?: run { BleLog.e("FTMS IndoorBikeData characteristic NOT FOUND"); return false }
  val notificationFlow = try {
    conn.enableNotifications(dataChar)
  } catch (t: Throwable) {
    BleLog.e("FTMS enableNotifications failed", t); return false
  }
  scope.launch {
    try {
      notificationFlow.collect { bytes ->
        IndoorBikeDataParser.parse(bytes)?.let {
          _data.value = Stamped(it, android.os.SystemClock.elapsedRealtime())
        } ?: BleLog.w("FTMS parse returned null, dropping ${bytes.size} bytes")
      }
    } catch (t: Throwable) { BleLog.e("FTMS notification collector crashed", t) }
  }
  return true
}
```

Apply the same subscribe-then-report pattern to `FtmsControlManager`, `HrManager`, and `ZwiftClickManager` (read each file first; they follow the same shape). `TrainerLoopApplication.connectTrainer()` already propagates manager failures — no change needed there.

- [ ] **Step 3: Run the full suite** — PASS (fix any test fakes the compiler flags).

- [ ] **Step 4: Manual verification** — power the trainer off mid-scan and attempt connect: the UI must reach a failure state within ~20 s instead of spinning forever.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/trainerloop/ble app/src/test
git commit -m "fix(ble): bound connect with timeout; report real subscription success"
```

---

### Task 12: Application-scoped ProfileRepository (finding #12)

Every screen constructs its own `ProfileRepository`, each with an independently-loaded `StateFlow` (`ProfileRepository.kt:17`) — Settings writes never reach retained Home/Library ViewModels.

**Files:**
- Modify: `app/src/main/java/com/trainerloop/app/TrainerLoopApplication.kt`
- Modify: every `ProfileRepository(` construction site — find them all with `grep -rn "ProfileRepository(" app/src/main/java` (known: `TrainerLoopApp.kt` ×3, `WorkoutScreen.kt:92`, `WorkoutCompleteViewModel.kt:67`, `WorkoutLibraryViewModel.kt:54`, `HomeViewModel.kt`, `SettingsViewModel.kt`)
- Test: `app/src/test/java/com/trainerloop/data/repository/ProfileRepositoryTest.kt` unchanged (repo class itself is untouched)

- [ ] **Step 1: Add the singleton**

```kotlin
// TrainerLoopApplication
val profileRepository: ProfileRepository by lazy { ProfileRepository(this) }
```

- [ ] **Step 2: Replace all construction sites**

Composables: `context.trainerLoopApp.profileRepository`. `AndroidViewModel`s: `(application as TrainerLoopApplication).profileRepository` — keep it as the ViewModel's constructor **default** so tests can still inject a fake:

```kotlin
private val profileRepository: ProfileRepository =
  (application as? TrainerLoopApplication)?.profileRepository ?: ProfileRepository(application)
```

- [ ] **Step 3: Make retained ViewModels observe instead of snapshot**

`WorkoutLibraryViewModel.init` — replace the one-shot `canSync` read:

```kotlin
viewModelScope.launch {
  profileRepository.profile.collect { p ->
    _uiState.value = _uiState.value.copy(
      canSync = p.intervalsIcuAthleteId.isNotBlank() && p.intervalsIcuApiKey.isNotBlank()
    )
  }
}
```

`HomeViewModel`: same pattern for whatever profile fields it snapshots (read the file; it constructs its own repository at line ~54).

- [ ] **Step 4: Run suite + manual verification** — change FTP in Settings, switch to Workouts tab without restarting: categories/targets reflect the new FTP.

- [ ] **Step 5: Commit**

```bash
git add -A app/src/main/java/com/trainerloop
git commit -m "refactor(profile): single app-scoped ProfileRepository; screens observe live profile"
```

---

## Phase 5 — Storage and performance (findings #13, #14, #16, #17, #9)

### Task 13: Room projection for session summaries (finding #13)

`SELECT *` (`SessionDao.kt:15`) drags every telemetry/coach JSON blob into memory on every History refresh.

**Files:**
- Modify: `app/src/main/java/com/trainerloop/data/source/local/SessionDao.kt`
- Modify: `app/src/main/java/com/trainerloop/data/repository/SessionRepository.kt`

- [ ] **Step 1: Add the projection**

```kotlin
data class SessionSummaryRow(
  val id: String,
  val workoutId: String,
  val workoutName: String,
  val startedAt: String,
  val endedAt: String?,
  val durationSec: Int,
  val completed: Boolean,
  val avgPower: Int,
  val maxPower: Int,
  val avgCadence: Int,
  val avgHr: Int,
  val icuSyncedAt: String?
)

@Query(
  "SELECT id, workoutId, workoutName, startedAt, endedAt, durationSec, completed, " +
  "avgPower, maxPower, avgCadence, avgHr, icuSyncedAt FROM sessions ORDER BY startedAt DESC"
)
fun getSummaries(): Flow<List<SessionSummaryRow>>
```

`SessionRepository.summaries()` maps `SessionSummaryRow → SessionSummary` field-for-field (drop `getAll()` if no other caller remains — check with grep).

- [ ] **Step 2: Run the full suite** — PASS (Room validates the query at compile time via kapt).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/trainerloop/data
git commit -m "perf(history): summary projection query — stop loading telemetry blobs for the list"
```

---

### Task 14: Atomic, idempotent workout persistence (finding #14)

`ImportedWorkoutStore.add` appends without dedup (repeated icu syncs duplicate workouts), writes non-atomically, and one malformed entry makes `load()` return an empty library (`ImportedWorkoutStore.kt:33,48,54`).

**Files:**
- Modify: `app/src/main/java/com/trainerloop/ui/library/ImportedWorkoutStore.kt`
- Modify: `app/src/main/java/com/trainerloop/ui/library/WorkoutLibraryViewModel.kt` (stable icu ids)
- Test: `app/src/test/java/com/trainerloop/ui/library/ImportedWorkoutStoreTest.kt` (create)

**Interfaces:**
- Produces: file-taking internal overloads for JVM tests: `internal fun add(file: File, workout: Workout)`, `internal fun remove(file: File, id: String)`, `internal fun load(file: File): List<Workout>`; the Context overloads delegate. `add` upserts by `workout.id`; writes go to a temp file then `renameTo`; `load` skips malformed entries individually.

- [ ] **Step 1: Write the failing tests**

```kotlin
class ImportedWorkoutStoreTest {
  private val file = File.createTempFile("workouts", ".json").apply { delete() }

  @Test
  fun `add with an existing id replaces instead of duplicating`() {
    val w = workout(id = "a", name = "One")
    ImportedWorkoutStore.add(file, w)
    ImportedWorkoutStore.add(file, w.copy(name = "Two"))
    val loaded = ImportedWorkoutStore.load(file)
    assertEquals(1, loaded.size)
    assertEquals("Two", loaded[0].name)
  }

  @Test
  fun `one malformed entry does not wipe the library`() {
    ImportedWorkoutStore.add(file, workout(id = "good", name = "Good"))
    val arr = JSONArray(file.readText())
    arr.put(JSONObject().put("id", "broken"))  // missing every required field
    file.writeText(arr.toString())
    assertEquals(listOf("good"), ImportedWorkoutStore.load(file).map { it.id })
  }
}
```

(`workout(...)` helper builds a minimal one-segment `Workout`.)

- [ ] **Step 2: Run to verify failure, then implement**

```kotlin
fun add(context: Context, workout: Workout) = add(file(context), workout)

internal fun add(file: File, workout: Workout) {
  val existing = load(file).filter { it.id != workout.id } + workout   // upsert
  writeAtomically(file, existing)
}

internal fun remove(file: File, id: String) =
  writeAtomically(file, load(file).filter { it.id != id })

internal fun load(file: File): List<Workout> {
  if (!file.exists()) return emptyList()
  return try {
    val json = JSONArray(file.readText())
    (0 until json.length()).mapNotNull { i ->
      try { jsonToWorkout(json.getJSONObject(i)) } catch (e: Exception) { null }  // skip bad entry
    }
  } catch (e: Exception) {
    emptyList()  // whole file unreadable
  }
}

private fun writeAtomically(file: File, workouts: List<Workout>) {
  val json = JSONArray()
  workouts.forEach { json.put(workoutToJson(it)) }
  val tmp = File(file.parentFile, "${file.name}.tmp")
  tmp.writeText(json.toString())
  if (!tmp.renameTo(file)) {
    file.writeText(json.toString())  // fallback on filesystems without atomic rename
    tmp.delete()
  }
}
```

Keep the Context-based `remove`/`load` delegating to the file overloads.

`WorkoutLibraryViewModel.sync()` — stable ids so re-sync upserts instead of duplicating:

```kotlin
val workout = WorkoutImporter.import("$name.zwo", zwo, ftp).copy(id = "icu_${event.id}")
```

- [ ] **Step 3: Run the full suite** — PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/trainerloop/ui/library app/src/test/java/com/trainerloop/ui/library
git commit -m "fix(library): upsert-by-id, atomic writes, per-entry parse resilience; stable icu ids"
```

---

### Task 15: Bound long-session telemetry work (finding #16)

Every 1 Hz tick copies the whole sample list (`TelemetryRecorder.kt:105`); `CoachEngine` (line ~354) rescans full history for short windows.

**Files:**
- Modify: `app/src/main/java/com/trainerloop/domain/TelemetryRecorder.kt`
- Modify: `app/src/main/java/com/trainerloop/domain/CoachEngine.kt`
- Modify: `app/src/main/java/com/trainerloop/ui/workout/WorkoutViewModel.kt`, `FreeRideViewModel.kt` (use `flush()` at stop)
- Test: extend `TelemetryRecorderTest.kt`

**Interfaces:**
- Produces: recorder accumulates into a private `ArrayList`; the public `samples: StateFlow<List<TelemetrySample>>` snapshot is refreshed only every `SNAPSHOT_EVERY_SEC = 5` ticks; new `fun flush()` publishes the final snapshot immediately (call before reading samples at stop/finish). `CoachEngine` windowed scans switch to `samples.takeLast(windowSec)` — samples are 1 Hz so index-window equals time-window.

- [ ] **Step 1: Failing test**

```kotlin
@Test
fun `samples snapshot refreshes every 5 ticks and flush publishes immediately`() = runTest {
  // drive 3 ticks → samples.value still reflects the last snapshot boundary
  ...
  assertEquals(0, recorder.samples.value.size)
  recorder.flush()
  assertEquals(3, recorder.samples.value.size)
}
```

- [ ] **Step 2: Implement**

In the recorder: `private val buffer = ArrayList<TelemetrySample>()`; append to `buffer` (same dedup guard); `if (elapsedSec % SNAPSHOT_EVERY_SEC == 0) _samples.value = buffer.toList()`. `fun flush() { _samples.value = buffer.toList() }`. `reset()` clears `buffer`. Seed `buffer` from `initialSamples` (Task 6).

Call `recorder.value?.flush()` at the top of `WorkoutViewModel.stop()` / `maybeEmitFinish()` and `FreeRideViewModel.stop()` **before** reading `samples`, so the finish data includes the final seconds.

`CoachEngine.kt` at the flagged window scans (line ~354 area): replace full-history filtering like `samples.filter { it.timeSec > cutoff }` with `samples.takeLast(windowSec)` where the window is a trailing time window over 1 Hz samples. Read the surrounding code first and only convert scans that are genuinely trailing windows.

- [ ] **Step 3: Run the full suite** — PASS. Chart now updates every 5 s (acceptable; the live power number comes from `latest`, still 1 Hz).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/trainerloop/domain app/src/main/java/com/trainerloop/ui app/src/test
git commit -m "perf(telemetry): buffer samples, snapshot every 5s with explicit flush; tail-window coach scans"
```

---

### Task 16: Bound import cost and validate imported workout ranges (finding #17)

**Files:**
- Modify: `app/src/main/java/com/trainerloop/ui/library/WorkoutImportContract.kt` (file-size cap)
- Modify: `app/src/main/java/com/trainerloop/domain/parser/GpxParser.kt` (O(n) smoothing, point cap)
- Modify: `app/src/main/java/com/trainerloop/domain/parser/JsonWorkoutParser.kt`, `ZwoParser.kt` (duration/repeat caps)
- Test: extend the existing parser tests (`app/src/test/java/com/trainerloop/domain/parser/`)

- [ ] **Step 1: Failing tests**

- `GpxParserTest`: a synthetic 60 000-point GPX parses in bounded time (test with 60k points and assert point count is capped at 50 000).
- `ZwoParserTest`: `<IntervalsT Repeat="100000" ...>` yields at most `MAX_REPEATS = 200` repeats; a segment with `Duration="10000000"` clamps to `MAX_SEGMENT_SEC = 86_400`.
- `JsonWorkoutParserTest`: same caps.

- [ ] **Step 2: Implement**

- `WorkoutImportHelper.importWorkout`: before reading, cap input to 5 MB:

```kotlin
val content = inputStream.buffered().use { stream ->
  val bytes = stream.readNBytes(MAX_IMPORT_BYTES + 1)
  if (bytes.size > MAX_IMPORT_BYTES) return null
  bytes.decodeToString()
}
// companion: const val MAX_IMPORT_BYTES = 5 * 1024 * 1024
```

- `GpxParser.kt:62` elevation smoothing: replace the quadratic per-point neighborhood scan with a running-sum sliding window (subtract the element leaving, add the element entering — O(n)). After parsing, if points exceed 50 000, downsample by stride: `points.filterIndexed { i, _ -> i % stride == 0 }` with `stride = ceil(points.size / 50_000.0).toInt()`.
- `ZwoParser.kt:212` and `JsonWorkoutParser.kt:30`: clamp every parsed duration with `.coerceIn(1, 86_400)` and repeat counts with `.coerceIn(1, 200)`; after assembly, if `segments.size > 2_000` throw the parser's existing invalid-input exception type (read each parser's error convention first and reuse it).

- [ ] **Step 3: Run the full suite** — PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/trainerloop/domain/parser app/src/main/java/com/trainerloop/ui/library app/src/test
git commit -m "fix(import): size/point/duration/repeat caps; linear GPX smoothing"
```

---

### Task 17: Explicit backup/privacy policy (finding #9)

Auto Backup is on (`AndroidManifest.xml:23`) and only profile prefs are excluded — Room sessions, GPX coordinates, and FIT files are eligible for upload to Google Drive, contradicting the app's no-cloud posture for health/location data.

**Decision (this plan):** disable Auto Backup entirely. Everything valuable is either re-syncable (intervals.icu), re-importable (workouts/GPX), or health/location data that must not leave the device. This is the smallest honest policy; a selective allowlist can come later if onboarding pain warrants it.

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Delete: `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml`

- [ ] **Step 1: Implement**

```xml
<application
  android:name=".TrainerLoopApplication"
  android:allowBackup="false"
  ...>
```

Remove the `android:fullBackupContent` and `android:dataExtractionRules` attributes and delete both xml files.

- [ ] **Step 2: Verify** — `./gradlew lint assembleDebug` passes; `adb shell bmgr backupnow com.trainerloop.app` (on a test device) reports the package as not eligible.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git rm app/src/main/res/xml/backup_rules.xml app/src/main/res/xml/data_extraction_rules.xml
git commit -m "fix(privacy): disable Auto Backup — health/location data stays on device"
```

---

## Phase 6 — Build modernization (finding #18) — separate branch/PR

### Task 18: AGP 8.7.x, then kapt→KSP + Room update

AGP 8.5.2 officially supports only API 34 while the project targets 35 (`build.gradle.kts:2`, `app/build.gradle.kts:10`).

**Files:**
- Modify: `build.gradle.kts`, `app/build.gradle.kts`, `gradle/wrapper/gradle-wrapper.properties`

- [ ] **Step 1 (commit 1): AGP compatibility**

- `build.gradle.kts`: `id("com.android.application") version "8.7.3" apply false`.
- Check `gradle/wrapper/gradle-wrapper.properties`: AGP 8.7 requires Gradle ≥ 8.9; if lower, run `./gradlew wrapper --gradle-version 8.9`.
- Run: `./gradlew testDebugUnitTest lint assembleDebug` — all green.

```bash
git add build.gradle.kts gradle/wrapper
git commit -m "build: AGP 8.7.3 + Gradle 8.9 — supported pairing for compile/target SDK 35"
```

- [ ] **Step 2 (commit 2): kapt → KSP and Room bump**

- Root `build.gradle.kts`: replace the kapt plugin line with `id("com.google.devtools.ksp") version "1.9.25-1.0.20" apply false`.
- `app/build.gradle.kts`: swap `id("org.jetbrains.kotlin.kapt")` for `id("com.google.devtools.ksp")`; `kapt("androidx.room:room-compiler:2.6.1")` → `ksp("androidx.room:room-compiler:2.7.1")`; bump the other two Room artifacts to 2.7.1; move the Task 9 schema-location argument to KSP form:

```kotlin
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
```

- Run: `./gradlew clean testDebugUnitTest lint assembleDebug` — all green; verify `app/schemas/` regenerates identically (Room 2.7 must not require a migration for an unchanged schema — the schema JSON diff must be empty).

```bash
git add build.gradle.kts app/build.gradle.kts
git commit -m "build: migrate Room to KSP and 2.7.1"
```

---

## Self-review notes

- **Spec coverage:** findings #1→Task 1, #2→Task 3, #3→Task 4, #4→Task 5, #5→Task 6, #6→Task 7, #7→Task 8, #8→Task 2, #9→Task 17, #10→Task 10, #11→Task 11, #12→Task 12, #13→Task 13, #14→Task 14, #15→Task 9, #16→Task 15, #17→Task 16, #18→Task 18. The audit's "feature directions" (training plan strip, RPE/outbox, insights, editor) are deliberately out of scope — separate brainstorm/plan each.
- **Ordering dependencies:** Task 5 changes `FtmsManager.data`'s type — Task 11's snippet already uses the `Stamped` form, so do Task 5 first (they're ordered that way). Task 15 builds on Task 6's `initialSamples`. Task 18 Step 2 depends on Task 9's schema-location argument existing (it moves it).
- **Known verification points at implementation time** (signatures this plan references but intentionally re-checks in-step): `WorkoutSummaryMath.workoutStats` FTP parameter (Task 8), `HomeViewModel` profile fields (Task 12), `CoachEngine` window-scan sites (Task 15), parser error conventions (Task 16), `AppDatabase` version (Task 9). Each task says to read the file first and adapt — do not paste blindly.
