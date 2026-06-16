# Trainer Loop → Kotlin Android Native Migration Plan

> **REQUIRED SUB-SKILL:** Use the `executing-plans` skill to implement this plan task-by-task.

**Goal:** Port the browser-based Trainer Loop application to a native Kotlin Android app that runs reliably on a Pixel 2 XL (Android 11 / API 30), preserving smart-trainer FTMS control, heart-rate monitoring, workout playback, live coaching, FIT export, and session persistence.

**Architecture:** A single Android app module under `android/` using Jetpack Compose for UI, `ViewModel` + `StateFlow` for state, coroutines/Flow for async work, Android `BluetoothGatt` directly for BLE, and Room for local persistence. Domain logic is ported from TypeScript to pure Kotlin and unit-tested; platform-specific code (BLE, file sharing, notifications) lives in thin adapters.

**Tech Stack:** Kotlin, Jetpack Compose, ViewModel, Navigation Compose, Coroutines/Flow, Room, JUnit 5 (or JUnit 4), MockK, Turbine, Android Gradle Plugin.

---

## Source → Destination Mapping

| Current TypeScript file | Responsibility | New Kotlin file(s) |
|---|---|---|
| `src/types.ts` | Shared telemetry types | `data/model/TelemetrySample.kt` |
| `src/types/coach.ts` | Coach suggestion/event types | `data/model/CoachProfile.kt`, `CoachEvent.kt`, `CoachSuggestion.kt` |
| `src/data/workout.ts` | Workout segment types | `data/model/Workout.kt`, `WorkoutSegment.kt`, `TargetRange.kt` |
| `src/utils/workout.ts` | Workout math | `domain/WorkoutMath.kt` |
| `src/utils/workoutParser.ts` | Text parsers | `domain/parser/*Parser.kt` |
| `src/utils/workoutImport.ts` | File import dispatch | `domain/WorkoutImporter.kt` |
| `src/utils/fit.ts` | FIT file builder | `domain/fit/FitEncoder.kt` |
| `src/utils/sessionStorage.ts` | Session persistence | `data/repository/SessionRepository.kt`, `data/source/local/*` |
| `src/utils/coachProfiles.ts`, `coachNotes.ts` | Coach data | `data/repository/CoachProfileRepository.kt`, `domain/CoachMessageBuilder.kt` |
| `src/hooks/useBluetoothDevices.ts` | BLE connection/scanner | `ble/BleScanner.kt`, `ble/BleConnection.kt` |
| `src/hooks/useBluetoothTelemetry.ts` | Telemetry notifications | `ble/FtmsManager.kt`, `ble/HrManager.kt` |
| `src/hooks/useFtmsControl.ts` | ERG writes | `ble/FtmsControlManager.kt` |
| `src/hooks/useWorkoutClock.ts` | Session timer | `domain/WorkoutClock.kt` |
| `src/hooks/useCoachEngine.ts` | Coaching rules | `domain/CoachEngine.kt` |
| `src/App.tsx`, `src/components/*.tsx` | UI | `ui/*Screen.kt`, `ui/*ViewModel.kt`, `ui/components/*.kt` |

---

## Phase 0 — Bootstrap the Android Project

### Task 0.1: Create the Android module

**Files:**
- Create: `android/settings.gradle.kts`
- Create: `android/build.gradle.kts`
- Create: `android/gradle.properties`
- Create: `android/app/build.gradle.kts`
- Create: `android/app/src/main/AndroidManifest.xml`

**Step 1:** Add the project-level Gradle files with the Android Gradle Plugin and Kotlin plugin.

**Step 2:** In `android/app/build.gradle.kts` set:

```kotlin
namespace = "com.trainerloop.app"
compileSdk = 35

defaultConfig {
    minSdk = 30
    targetSdk = 35
    versionCode = 1
    versionName = "0.1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
}

buildFeatures { compose = true }
composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("app.cash.turbine:turbine:1.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
```

**Step 3:** Add the manifest with BLE and foreground-service permissions:

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />
```

**Step 4:** Run a build to verify the empty project compiles.

Run: `./gradlew :app:assembleDebug`  
Expected: `BUILD SUCCESSFUL`

**Step 5:** Commit.

```bash
git add android/
git commit -m "chore: bootstrap android app module"
```

---

## Phase 1 — Domain Models (Pure Kotlin)

### Task 1.1: Port workout segment model

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/data/model/Workout.kt`
- Create: `android/app/src/main/java/com/trainerloop/data/model/WorkoutSegment.kt`
- Create: `android/app/src/main/java/com/trainerloop/data/model/TargetRange.kt`
- Test: `android/app/src/test/java/com/trainerloop/data/model/WorkoutSegmentTest.kt`

**Step 1:** Define sealed class for segments.

```kotlin
package com.trainerloop.data.model

data class TargetRange(val low: Int, val high: Int)

sealed class WorkoutSegment(
    open val id: String,
    open val durationSec: Int,
    open val label: String?,
    open val phase: SegmentPhase,
    open val isWork: Boolean
) {
    data class Step(
        override val id: String,
        override val durationSec: Int,
        override val label: String?,
        override val phase: SegmentPhase,
        override val isWork: Boolean,
        val targetRange: TargetRange,
        val targetCadence: IntRange? = null
    ) : WorkoutSegment(id, durationSec, label, phase, isWork)

    data class Ramp(
        override val id: String,
        override val durationSec: Int,
        override val label: String?,
        override val phase: SegmentPhase,
        override val isWork: Boolean,
        val startPower: Int,
        val endPower: Int,
        val targetCadence: IntRange? = null
    ) : WorkoutSegment(id, durationSec, label, phase, isWork)

    data class FreeRide(
        override val id: String,
        override val durationSec: Int,
        override val label: String?,
        override val phase: SegmentPhase,
        override val isWork: Boolean = false
    ) : WorkoutSegment(id, durationSec, label, phase, isWork)
}

enum class SegmentPhase { WARMUP, WORK, RECOVERY, COOLDOWN }

data class Workout(
    val id: String,
    val name: String,
    val description: String?,
    val source: WorkoutSource,
    val segments: List<WorkoutSegment>
)

enum class WorkoutSource { MANUAL, IMPORTED }
```

**Step 2:** Write a test that constructs a workout and asserts segment durations sum correctly.

```kotlin
@Test
fun `workout total duration is sum of segments`() {
    val workout = sampleWorkout()
    val total = workout.segments.sumOf { it.durationSec }
    assertEquals(600, total)
}
```

**Step 3:** Run the test.

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.data.model.WorkoutSegmentTest"`  
Expected: `BUILD SUCCESSFUL`, tests pass.

**Step 4:** Commit.

```bash
git commit -m "feat: add workout segment domain model"
```

---

### Task 1.2: Port telemetry sample model

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/data/model/TelemetrySample.kt`
- Create: `android/app/src/main/java/com/trainerloop/data/model/TelemetryGap.kt`
- Test: `android/app/src/test/java/com/trainerloop/data/model/TelemetrySampleTest.kt`

**Step 1:** Define the model matching `src/types.ts`.

```kotlin
package com.trainerloop.data.model

data class TelemetrySample(
    val timeSec: Int,
    val powerWatts: Int,
    val cadenceRpm: Int,
    val hrBpm: Int,
    val dropout: Boolean = false,
    val lagCompensated: Boolean = false
)

data class TelemetryGap(
    val startSec: Int,
    val endSec: Int,
    val kind: GapKind = GapKind.DROPOUT
)

enum class GapKind { DROPOUT }
```

**Step 2:** Test that a sample serializes/deserializes through JSON for future storage compatibility.

```kotlin
@Test
fun `sample round trips through json`() {
    val sample = TelemetrySample(10, 200, 90, 150)
    val json = Json.encodeToString(sample)
    val restored = Json.decodeFromString<TelemetrySample>(json)
    assertEquals(sample, restored)
}
```

**Step 3:** Run tests and commit.

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.data.model.TelemetrySampleTest"`  
Expected: pass.

```bash
git commit -m "feat: add telemetry sample model"
```

---

### Task 1.3: Port coach data model

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/data/model/CoachProfile.kt`
- Create: `android/app/src/main/java/com/trainerloop/data/model/CoachEvent.kt`
- Create: `android/app/src/main/java/com/trainerloop/data/model/CoachSuggestion.kt`
- Test: `android/app/src/test/java/com/trainerloop/data/model/CoachModelTest.kt`

**Step 1:** Port the types from `src/types/coach.ts` and `docs/data-model.md`.

```kotlin
package com.trainerloop.data.model

data class CoachProfile(
    val id: String,
    val name: String,
    val description: String,
    val rules: CoachRules,
    val interventions: CoachInterventions,
    val voice: CoachVoice,
    val messages: CoachMessages
)

data class CoachRules(
    val targetAdherenceWarn: Double,
    val targetAdherenceIntervene: Double,
    val hrDriftWarn: Double,
    val hrDriftIntervene: Double,
    val cadenceVarianceWarn: Double,
    val cadenceVarianceIntervene: Double,
    val minElapsedSecondsForSuggestions: Int,
    val cooldownSeconds: Int
)

data class CoachInterventions(
    val intensityAdjustStepPct: Double,
    val intensityAdjustMinPct: Double,
    val intensityAdjustMaxPct: Double,
    val recoveryExtendStepSec: Int,
    val recoveryExtendMaxSec: Int,
    val allowSkipRemainingOnIntervals: Boolean
)

data class CoachVoice(val tone: String, val style: String)

data class CoachMessages(
    val suggestions: Map<String, List<String>>,
    val completion: List<String>,
    val encouragement: List<String>
)

sealed class CoachAction {
    data class AdjustIntensityUp(val percent: Int) : CoachAction()
    data class AdjustIntensityDown(val percent: Int) : CoachAction()
    data class ExtendRecovery(val seconds: Int) : CoachAction()
    object SkipRemainingOnIntervals : CoachAction()
}

data class CoachSuggestion(
    val id: String,
    val action: CoachAction,
    val message: String,
    val rationale: String,
    val segmentIndex: Int?,
    val status: SuggestionStatus = SuggestionStatus.PENDING
)

enum class SuggestionStatus { PENDING, ACCEPTED, REJECTED }

data class CoachEvent(
    val id: String,
    val sessionId: String,
    val timestamp: String,
    val type: CoachEventType,
    val message: String,
    val rationale: String? = null,
    val suggestion: CoachSuggestion? = null,
    val userResponse: CoachResponse? = null
)

enum class CoachEventType { ENCOURAGEMENT, SUGGESTION, COMPLETION }

data class CoachResponse(val response: ResponseType, val respondedAt: String)
enum class ResponseType { ACCEPTED, REJECTED }
```

**Step 2:** Write a test that constructs the default coach profile and verifies an action can be applied.

**Step 3:** Run tests and commit.

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.data.model.CoachModelTest"`  
Expected: pass.

```bash
git commit -m "feat: add coach domain model"
```

---

## Phase 2 — Workout Math & Import

### Task 2.1: Port workout math utilities

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/domain/WorkoutMath.kt`
- Test: `android/app/src/test/java/com/trainerloop/domain/WorkoutMathTest.kt`

**Step 1:** Implement total duration, target range at elapsed time, and ramp interpolation based on `src/utils/workout.ts`.

```kotlin
package com.trainerloop.domain

import com.trainerloop.data.model.*

object WorkoutMath {
    fun totalDurationSec(segments: List<WorkoutSegment>): Int =
        segments.sumOf { it.durationSec }

    fun segmentIndexAt(segments: List<WorkoutSegment>, elapsedSec: Int): Int {
        var remaining = elapsedSec
        segments.forEachIndexed { index, segment ->
            if (remaining < segment.durationSec) return index
            remaining -= segment.durationSec
        }
        return segments.lastIndex.coerceAtLeast(0)
    }

    fun targetRangeAt(segments: List<WorkoutSegment>, elapsedSec: Int): TargetRange {
        val index = segmentIndexAt(segments, elapsedSec)
        val segment = segments.getOrNull(index) ?: return TargetRange(0, 0)
        val segmentStart = segments.take(index).sumOf { it.durationSec }
        val elapsedInSegment = (elapsedSec - segmentStart).coerceIn(0, segment.durationSec)
        return when (segment) {
            is WorkoutSegment.Step -> segment.targetRange
            is WorkoutSegment.Ramp -> {
                val ratio = if (segment.durationSec == 0) 0.0
                            else elapsedInSegment / segment.durationSec.toDouble()
                val power = (segment.startPower + (segment.endPower - segment.startPower) * ratio).toInt()
                TargetRange(power, power)
            }
            is WorkoutSegment.FreeRide -> TargetRange(0, 0)
        }
    }
}
```

**Step 2:** Write tests for ramp interpolation and segment boundaries.

```kotlin
@Test
fun `ramp interpolates halfway`() {
    val segments = listOf(WorkoutSegment.Ramp("r", 60, null, SegmentPhase.WORK, true, 100, 200))
    assertEquals(TargetRange(150, 150), WorkoutMath.targetRangeAt(segments, 30))
}
```

**Step 3:** Run and commit.

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.domain.WorkoutMathTest"`  
Expected: pass.

```bash
git commit -m "feat: port workout math utilities"
```

---

### Task 2.2: Port ERG/MRC/ZWO/JSON parsers

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/domain/parser/ErgParser.kt`
- Create: `android/app/src/main/java/com/trainerloop/domain/parser/MrcParser.kt`
- Create: `android/app/src/main/java/com/trainerloop/domain/parser/ZwoParser.kt`
- Create: `android/app/src/main/java/com/trainerloop/domain/parser/JsonWorkoutParser.kt`
- Create: `android/app/src/main/java/com/trainerloop/domain/WorkoutImporter.kt`
- Test: `android/app/src/test/java/com/trainerloop/domain/parser/*ParserTest.kt`

**Step 1:** Port the parsing logic from `src/utils/workoutParser.ts` and `src/utils/workoutImport.ts`. Keep each parser pure: `fun parse(name: String, content: String): Workout`.

**Step 2:** Add a dispatcher `WorkoutImporter.import(filename, content)` that selects the parser by extension.

**Step 3:** Add sample workout files under `android/app/src/test/resources/` matching the formats supported by the web app.

**Step 4:** Write unit tests for each parser and the dispatcher.

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.domain.parser.*"`  
Expected: pass.

```bash
git commit -m "feat: port workout file parsers"
```

---

## Phase 3 — FIT Export

### Task 3.1: Port FIT file encoder

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/domain/fit/FitEncoder.kt`
- Test: `android/app/src/test/java/com/trainerloop/domain/fit/FitEncoderTest.kt`

**Step 1:** Port `src/utils/fit.ts` byte-by-byte. Keep the same message definitions, CRC table, and field numbering so existing exported files remain compatible.

```kotlin
object FitEncoder {
    private const val FIT_EPOCH_MS = 631065600000L // 1989-12-31 00:00:00 UTC
    private const val PROTOCOL_VERSION = 0x10
    private const val PROFILE_VERSION = 0x0100

    fun encode(startTimeMs: Long, elapsedSec: Int, samples: List<TelemetrySample>): ByteArray {
        // implementation matching fit.ts
    }
}
```

**Step 2:** Add a golden-master test: export the same samples from the web app and from Kotlin, then assert byte arrays match.

**Step 3:** Run and commit.

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.domain.fit.FitEncoderTest"`  
Expected: pass.

```bash
git commit -m "feat: port FIT file encoder"
```

---

## Phase 4 — Local Persistence (Room)

### Task 4.1: Define session entity and Room database

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/data/source/local/AppDatabase.kt`
- Create: `android/app/src/main/java/com/trainerloop/data/source/local/SessionEntity.kt`
- Create: `android/app/src/main/java/com/trainerloop/data/source/local/SessionDao.kt`
- Create: `android/app/src/main/java/com/trainerloop/data/repository/SessionRepository.kt`
- Test: `android/app/src/test/java/com/trainerloop/data/repository/SessionRepositoryTest.kt` (use Room in-memory test)

**Step 1:** Create entity that stores session summary, JSON samples, and coach events.

```kotlin
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val workoutId: String,
    val workoutName: String,
    val startedAt: String,
    val endedAt: String?,
    val durationSec: Int,
    val samplesJson: String,
    val coachEventsJson: String,
    val completed: Boolean,
    val avgPower: Int,
    val maxPower: Int,
    val avgCadence: Int,
    val avgHr: Int
)
```

**Step 2:** Implement DAO with insert, getAll, getById, delete.

**Step 3:** Implement repository with `Flow<List<SessionSummary>>` for the UI and `suspend fun save(session: SessionData)`.

**Step 4:** Run repository tests.

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.data.repository.SessionRepositoryTest"`  
Expected: pass.

```bash
git commit -m "feat: add Room session persistence"
```

---

## Phase 5 — Workout Clock & Engine

### Task 5.1: Implement workout clock

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/domain/WorkoutClock.kt`
- Test: `android/app/src/test/java/com/trainerloop/domain/WorkoutClockTest.kt`

**Step 1:** Build a `WorkoutClock` that emits `elapsedSec` and `activeSec` via `StateFlow`, supports start/pause/resume/seek/stop, and increments session id on restart. Use `kotlinx.coroutines.delay` in a loop rather than `window.setInterval`.

```kotlin
class WorkoutClock(segments: List<WorkoutSegment>) {
    val elapsedSec: StateFlow<Int> = ...
    val activeSec: StateFlow<Int> = ...
    val isRunning: StateFlow<Boolean> = ...
    val isComplete: StateFlow<Boolean> = ...
    val sessionId: StateFlow<Int> = ...

    fun start() { ... }
    fun pause() { ... }
    fun resume() { ... }
    fun stop() { ... }
    fun seek(seconds: Int) { ... }
}
```

**Step 2:** Use `Turbine` to test flow emissions after start/pause/seek.

**Step 3:** Run and commit.

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.domain.WorkoutClockTest"`  
Expected: pass.

```bash
git commit -m "feat: add workout clock"
```

---

### Task 5.2: Implement coach engine

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/domain/CoachEngine.kt`
- Create: `android/app/src/main/java/com/trainerloop/domain/CoachMessageBuilder.kt`
- Test: `android/app/src/test/java/com/trainerloop/domain/CoachEngineTest.kt`

**Step 1:** Port the rule logic from `src/hooks/useCoachEngine.ts`: adherence, cadence variance, HR drift, cooldowns, pending suggestion state.

**Step 2:** Use `StateFlow<List<CoachEvent>>` and expose `pendingSuggestion: StateFlow<CoachSuggestion?>`.

**Step 3:** Write tests with synthetic sample windows.

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.domain.CoachEngineTest"`  
Expected: pass.

```bash
git commit -m "feat: port coach rule engine"
```

---

## Phase 6 — Bluetooth Layer

### Task 6.1: Create BLE permission helper

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/ble/BlePermissions.kt`
- Test: manual / `ActivityScenario` instrumentation test if time permits

**Step 1:** Implement runtime permission requests for:
- `BLUETOOTH` / `BLUETOOTH_ADMIN` (runtime only on Android 12+)
- `ACCESS_FINE_LOCATION` (required on Android 11 for BLE scan)
- Also check that location services are enabled.

```kotlin
object BlePermissions {
    val REQUIRED = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    fun hasPermissions(context: Context): Boolean = ...
    fun request(activity: Activity) { ... }
}
```

**Step 2:** Add the helper to `MainActivity.onCreate` before any BLE call.

**Step 3:** Manual test on Pixel 2 XL: deny location → expect an explanatory dialog; allow → proceed.

```bash
git commit -m "feat: add BLE runtime permission helper"
```

---

### Task 6.2: Implement BLE scanner

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/ble/BleScanner.kt`
- Create: `android/app/src/main/java/com/trainerloop/ble/model/BleDevice.kt`

**Step 1:** Wrap `BluetoothLeScanner` in a coroutines/Flow API:

```kotlin
class BleScanner(context: Context) {
    fun scan(services: List<UUID>, durationMs: Long = 10_000): Flow<List<BleDevice>>
    fun stopScan()
}

data class BleDevice(
    val address: String,
    val name: String?,
    val services: List<UUID>,
    val rssi: Int
)
```

**Step 2:** Filter scan results by FTMS (`0x1826`) and HR (`0x180d`) service UUIDs.

**Step 3:** Add a simple instrumentation test or manual test to list nearby devices.

```bash
git commit -m "feat: add BLE scanner"
```

---

### Task 6.3: Implement generic GATT connection

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/ble/BleConnection.kt`
- Create: `android/app/src/main/java/com/trainerloop/ble/GattCallback.kt`

**Step 1:** Build a coroutine-friendly wrapper around `BluetoothGatt` with:
- `connect()` / `disconnect()`
- `discoverServices()`
- `getCharacteristic(serviceUuid, charUuid)`
- `enableNotifications(characteristic)` returning a `Flow<ByteArray>`
- `writeCharacteristic(characteristic, bytes, withResponse)` suspend function

Use a `Callback` + `CompletableDeferred` pattern.

```kotlin
class BleConnection(private val device: BluetoothDevice) {
    val connectionState: StateFlow<Boolean> = ...
    suspend fun connect(): Result<Unit>
    suspend fun disconnect()
    suspend fun <T> read(service: UUID, characteristic: UUID, parse: (ByteArray) -> T): T?
    fun notifications(service: UUID, characteristic: UUID): Flow<ByteArray>
    suspend fun write(service: UUID, characteristic: UUID, bytes: ByteArray, withResponse: Boolean)
}
```

**Step 2:** Handle `onConnectionStateChange`, `onServicesDiscovered`, `onCharacteristicChanged`, `onCharacteristicWrite`.

**Step 3:** Manual test: connect to a trainer and read `0x2acc` (Fitness Machine Feature).

```bash
git commit -m "feat: add generic GATT connection wrapper"
```

---

### Task 6.4: Implement FTMS telemetry parser and manager

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/ble/model/IndoorBikeData.kt`
- Create: `android/app/src/main/java/com/trainerloop/ble/FtmsManager.kt`
- Test: `android/app/src/test/java/com/trainerloop/ble/IndoorBikeDataParserTest.kt`

**Step 1:** Port `parseIndoorBikeData` from `src/hooks/useBluetoothTelemetry.ts`.

```kotlin
data class IndoorBikeData(
    val powerWatts: Int?,
    val cadenceRpm: Double?,
    val speedKph: Double?,
    val resistanceLevel: Int?,
    val averagePower: Int?,
    val averageSpeed: Double?,
    val totalDistanceMeters: Int?,
    val elapsedTimeSec: Int?,
    val remainingTimeSec: Int?
)

object IndoorBikeDataParser {
    fun parse(bytes: ByteArray): IndoorBikeData { ... }
}
```

**Step 2:** Build `FtmsManager` that:
- Connects to a selected trainer.
- Reads device info, battery level, and feature bits.
- Subscribes to Indoor Bike Data notifications.
- Emits `IndoorBikeData` via `StateFlow`.

**Step 3:** Write parser tests with byte arrays copied from real trainer captures.

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.ble.IndoorBikeDataParserTest"`  
Expected: pass.

```bash
git commit -m "feat: add FTMS telemetry manager"
```

---

### Task 6.5: Implement heart-rate manager

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/ble/model/HeartRateMeasurement.kt`
- Create: `android/app/src/main/java/com/trainerloop/ble/HrManager.kt`
- Test: `android/app/src/test/java/com/trainerloop/ble/HeartRateMeasurementParserTest.kt`

**Step 1:** Port `parseHeartRateMeasurement` from `src/hooks/useBluetoothTelemetry.ts`.

```kotlin
object HeartRateMeasurementParser {
    fun parse(bytes: ByteArray): Int? { ... }
}
```

**Step 2:** Build `HrManager` that connects, reads device info/battery, and emits HR via `StateFlow<Int?>`.

**Step 3:** Run parser tests.

```bash
git commit -m "feat: add heart rate manager"
```

---

### Task 6.6: Implement FTMS control point / ERG writes

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/ble/FtmsControlManager.kt`
- Test: `android/app/src/test/java/com/trainerloop/ble/FtmsControlManagerTest.kt` (test payload builders only)

**Step 1:** Port control-point opcodes and payload builders from `src/hooks/useFtmsControl.ts`:

```kotlin
object FtmsCommands {
    fun requestControl(): ByteArray = byteArrayOf(0x00)
    fun setTargetPower(watts: Int): ByteArray = ... // opcode 0x05 + sint16le
    fun startResume(): ByteArray = byteArrayOf(0x07)
    fun stopPause(stop: Boolean): ByteArray = byteArrayOf(0x08, if (stop) 0x01 else 0x02)
}
```

**Step 2:** Build `FtmsControlManager` that:
- Requests control after connecting.
- Listens to control-point response notifications.
- Exposes `setTargetPower(watts)`, `start()`, `pause()`, `stop()`.
- Throttles writes to ~900 ms and clamps to `0..2000` watts.

**Step 3:** Test payload builders.

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.ble.FtmsControlManagerTest"`  
Expected: pass.

**Step 4:** Manual integration test on trainer: connect, request control, set 150 W, verify resistance changes.

```bash
git commit -m "feat: add FTMS ERG control manager"
```

---

### Task 6.7: Add reconnection logic

**Files:**
- Modify: `android/app/src/main/java/com/trainerloop/ble/BleConnection.kt`
- Modify: `android/app/src/main/java/com/trainerloop/ble/FtmsManager.kt`
- Modify: `android/app/src/main/java/com/trainerloop/ble/HrManager.kt`

**Step 1:** Add exponential backoff reconnection when `onConnectionStateChange` reports disconnected, unless the user explicitly disconnected.

**Step 2:** Surface connection status as `Status.IDLE | CONNECTING | CONNECTED | ERROR`.

**Step 3:** Manual test: power-cycle the trainer mid-workout and verify auto-reconnect.

```bash
git commit -m "feat: add BLE auto-reconnect"
```

---

## Phase 7 — Telemetry Pipeline & Session Recorder

### Task 7.1: Build telemetry recorder

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/domain/TelemetryRecorder.kt`
- Test: `android/app/src/test/java/com/trainerloop/domain/TelemetryRecorderTest.kt`

**Step 1:** Combine `FtmsManager.data`, `HrManager.hr`, and `WorkoutClock.elapsedSec` into `TelemetrySample` at 1 Hz (or on each notification with time bucket). Drop duplicates, handle dropouts.

```kotlin
class TelemetryRecorder(
    private val clock: WorkoutClock,
    private val ftms: FtmsManager,
    private val hr: HrManager
) {
    val latest: StateFlow<TelemetrySample> = ...
    val samples: StateFlow<List<TelemetrySample>> = ...
    fun reset(sessionId: Int) { ... }
}
```

**Step 2:** Write tests verifying HR and power merge correctly.

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.domain.TelemetryRecorderTest"`  
Expected: pass.

```bash
git commit -m "feat: add telemetry recorder"
```

---

## Phase 8 — UI (Jetpack Compose)

### Task 8.1: Set up Compose navigation and theme

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/ui/TrainerLoopApp.kt`
- Create: `android/app/src/main/java/com/trainerloop/ui/navigation/Screen.kt`
- Create: `android/app/src/main/java/com/trainerloop/ui/theme/Color.kt`, `Theme.kt`, `Type.kt`
- Modify: `android/app/src/main/java/com/trainerloop/app/MainActivity.kt`

**Step 1:** Define screens: `Library`, `WorkoutPreview`, `Workout`, `SessionSummary`, `Settings`.

**Step 2:** Wire `MainActivity.setContent { TrainerLoopTheme { TrainerLoopApp() } }`.

**Step 3:** Build and run on Pixel 2 XL to confirm app opens.

Run: `./gradlew :app:installDebug`  
Expected: app launches without crash.

```bash
git commit -m "feat: add compose navigation shell"
```

---

### Task 8.2: Build device connection screen

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/ui/connect/ConnectScreen.kt`
- Create: `android/app/src/main/java/com/trainerloop/ui/connect/ConnectViewModel.kt`

**Step 1:** UI shows:
- "Scan" button (disabled until permissions granted)
- Two lists: trainers and HR sensors
- Connect/disconnect buttons per device
- Status, battery, manufacturer/model labels

**Step 2:** `ConnectViewModel` owns `BleScanner`, `FtmsManager`, `HrManager` and exposes `UiState`.

**Step 3:** Manual test on Pixel 2 XL: scan, connect trainer, connect HR sensor.

```bash
git commit -m "feat: add device connection screen"
```

---

### Task 8.3: Build workout player screen

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/ui/workout/WorkoutScreen.kt`
- Create: `android/app/src/main/java/com/trainerloop/ui/workout/WorkoutViewModel.kt`
- Create: `android/app/src/main/java/com/trainerloop/ui/components/MetricCard.kt`
- Create: `android/app/src/main/java/com/trainerloop/ui/components/IntervalTimeline.kt`

**Step 1:** Display current target, actual power/cadence/HR, segment timer, total timer, and upcoming intervals. Use a simple timeline strip; chart can be added later.

**Step 2:** Controls: start, pause, stop, ERG toggle, intensity override buttons.

**Step 3:** `WorkoutViewModel` wires together `WorkoutClock`, `WorkoutMath`, `FtmsControlManager`, `TelemetryRecorder`, `CoachEngine`, and `SessionRepository`.

**Step 4:** Manual test: load a workout, start, verify target power updates every second and trainer responds.

```bash
git commit -m "feat: add workout player screen"
```

---

### Task 8.4: Add coach panel

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/ui/coach/CoachPanel.kt`
- Create: `android/app/src/main/java/com/trainerloop/ui/coach/CoachSuggestionCard.kt`
- Modify: `android/app/src/main/java/com/trainerloop/ui/workout/WorkoutViewModel.kt`

**Step 1:** Display pending suggestion with Accept/Reject. Log accepted/rejected events to session notes.

**Step 2:** Manual test: force a suggestion (e.g. by dropping power below threshold) and verify accept applies intensity offset.

```bash
git commit -m "feat: add coach suggestion panel"
```

---

### Task 8.5: Add session summary and FIT export

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/ui/summary/SessionSummaryScreen.kt`
- Create: `android/app/src/main/java/com/trainerloop/ui/summary/SessionSummaryViewModel.kt`
- Create: `android/app/src/main/java/com/trainerloop/ui/components/FitShareHelper.kt`

**Step 1:** After stop, compute summary (avg/max power, avg cadence/HR), save to Room, and offer "Save FIT".

**Step 2:** Use a `FileProvider` to share the FIT file via `Intent.ACTION_SEND` so the user can upload to Strava/etc.

**Step 3:** Add manifest provider entry:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths" />
</provider>
```

**Step 4:** Manual test: complete a workout, save FIT, open it in a file manager / upload to Garmin/Strava.

```bash
git commit -m "feat: add session summary and FIT export"
```

---

## Phase 9 — Settings & Profile

### Task 9.1: Port user profile and coach profile storage

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/data/model/UserProfile.kt`
- Create: `android/app/src/main/java/com/trainerloop/data/repository/ProfileRepository.kt`
- Create: `android/app/src/main/java/com/trainerloop/ui/settings/SettingsScreen.kt`
- Create: `android/app/src/main/java/com/trainerloop/ui/settings/SettingsViewModel.kt`

**Step 1:** Store user profile in `DataStore` (FTP, weight, HR/power zones, ERG bias). Store selected coach profile ID in `DataStore`.

**Step 2:** Ship default coach profiles as JSON assets in `android/app/src/main/assets/coach_profiles/`.

**Step 3:** Manual test: change FTP, verify target power percentages update.

```bash
git commit -m "feat: add user profile and coach profile settings"
```

---

## Phase 10 — Workout Library

### Task 10.1: Build workout library and import

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/ui/library/WorkoutLibraryScreen.kt`
- Create: `android/app/src/main/java/com/trainerloop/ui/library/WorkoutLibraryViewModel.kt`
- Create: `android/app/src/main/java/com/trainerloop/ui/library/WorkoutImportContract.kt`

**Step 1:** List built-in workouts and imported workouts. Use `ActivityResultContracts.OpenDocument` to import ERG/MRC/ZWO/JSON files.

**Step 2:** Persist imported workouts in Room or files dir.

**Step 3:** Manual test: import a `.zwo` file and run it.

```bash
git commit -m "feat: add workout library and import"
```

---

## Phase 11 — Foreground Service & Screen-On

### Task 11.1: Keep the workout alive

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/app/WorkoutForegroundService.kt`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Modify: `android/app/src/main/java/com/trainerloop/ui/workout/WorkoutViewModel.kt`

**Step 1:** Start a foreground service when a workout starts, displaying a persistent notification with current power/time and a pause/stop action.

**Step 2:** Acquire a wake lock and use `FLAG_KEEP_SCREEN_ON` on the workout screen.

**Step 3:** Manual test: start workout, lock screen, verify BLE connection and timer continue for at least 5 minutes.

```bash
git commit -m "feat: add workout foreground service"
```

---

## Phase 12 — Testing & Hardening

### Task 12.1: Add unit test suite run to CI / local checks

**Files:**
- Modify: `android/app/build.gradle.kts` if needed

**Step 1:** Ensure `./gradlew :app:testDebugUnitTest` runs all domain/parsing/FIT tests.

Run: `./gradlew :app:testDebugUnitTest`  
Expected: `BUILD SUCCESSFUL`

**Step 2:** Run lint.

Run: `./gradlew :app:lintDebug`  
Expected: `BUILD SUCCESSFUL` (treat warnings as errors if desired).

```bash
git commit -m "chore: wire up unit tests and lint"
```

---

### Task 12.2: Device test checklist on Pixel 2 XL

Perform these manually and record results in `docs/android-device-tests.md`:

1. Grant location permission → scan lists trainer and HR.
2. Connect trainer → reads battery, manufacturer, model, feature bits.
3. Connect HR → reads battery/manufacturer and streams BPM.
4. Start workout → trainer resistance matches target power.
5. Ramp segment → target power updates smoothly.
6. Pause → trainer enters pause state; resume restores target.
7. Power-cycle trainer mid-workout → auto-reconnects and resumes.
8. Heart-rate strap out of range → app shows warning but continues.
9. Complete workout → summary saved, FIT exported, file opens in external app.
10. 30-minute continuous workout → no ANR, no unexpected disconnect.

```bash
git commit -m "docs: add Pixel 2 XL device test checklist"
```

---

## Phase 13 — Strava Integration (Optional / Later)

### Task 13.1: Port Strava upload flow

**Files:**
- Create: `android/app/src/main/java/com/trainerloop/data/remote/StravaApi.kt`
- Create: `android/app/src/main/java/com/trainerloop/ui/strava/StravaUploadScreen.kt`

**Step 1:** Use Chrome Custom Tabs or a WebView for OAuth, then upload the FIT file via the Strava API. Reuse logic from `src/utils/stravaApi.ts` and `src/hooks/useStravaAuth.ts`.

**Step 2:** Manual test on Pixel 2 XL.

```bash
git commit -m "feat: port Strava upload (optional)"
```

---

## Phase 14 — Final Build Verification

### Task 14.1: Production build and sign

**Files:**
- Create: `android/app/trainer-loop.jks` (keystore, keep out of git)
- Modify: `android/app/build.gradle.kts` signing config

**Step 1:** Configure release signing and build a signed APK.

Run: `./gradlew :app:assembleRelease`  
Expected: `BUILD SUCCESSFUL`, APK produced at `android/app/build/outputs/apk/release/app-release.apk`.

**Step 2:** Install release APK on Pixel 2 XL and run the device checklist.

Run: `adb install -r android/app/build/outputs/apk/release/app-release.apk`  
Expected: success, app launches.

```bash
git commit -m "chore: configure release signing and build"
```

---

## Appendix A — Critical Android 11 Notes

- **Location is mandatory for BLE scanning on API 30.** Even though FTMS/HR are Bluetooth, Android 11 uses location permission to deliver scan results. The app must request `ACCESS_FINE_LOCATION` and detect if location services are disabled.
- **No `BLUETOOTH_SCAN` permission.** That was introduced in Android 12. On Android 11 use the legacy `BLUETOOTH`/`BLUETOOTH_ADMIN` permissions plus location.
- **WebView is irrelevant.** By going native Kotlin you avoid Web Bluetooth entirely; BLE reliability depends only on the Android stack and the device hardware.
- **Foreground service is strongly recommended.** The Pixel 2 XL may aggressively suspend apps. A `FOREGROUND_SERVICE` notification prevents the OS from killing the app mid-workout.

---

## Appendix B — Suggested Module Boundaries

```
android/app/src/main/java/com/trainerloop/
├── data/
│   ├── model/           # pure data classes
│   ├── repository/      # Room/DataStore access
│   └── source/local/    # Room entities/DAO
├── domain/
│   ├── parser/          # ERG/MRC/ZWO/JSON
│   ├── fit/             # FIT encoder
│   ├── WorkoutMath.kt
│   ├── WorkoutClock.kt
│   ├── CoachEngine.kt
│   └── TelemetryRecorder.kt
├── ble/
│   ├── BlePermissions.kt
│   ├── BleScanner.kt
│   ├── BleConnection.kt
│   ├── GattCallback.kt
│   ├── FtmsManager.kt
│   ├── FtmsControlManager.kt
│   ├── HrManager.kt
│   └── model/           # IndoorBikeData, HeartRateMeasurement, BleDevice
└── ui/
    ├── navigation/
    ├── theme/
    ├── components/
    ├── library/
    ├── connect/
    ├── workout/
    ├── coach/
    ├── summary/
    └── settings/
```

---

**Plan complete and saved to `docs/plans/2026-06-16-kotlin-android-migration.md`.**

Two execution options:

1. **Subagent-Driven (this session)** — I dispatch a fresh subagent per task, review between tasks, and iterate quickly.
2. **Parallel Session (separate)** — Open a new session using the `executing-plans` skill and run the tasks batch-by-batch with checkpoints.

Which approach would you like?
