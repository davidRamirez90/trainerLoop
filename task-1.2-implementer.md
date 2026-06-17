# Task 1.2: Add HomeScreen dashboard — Implementer Report

## What I implemented

- Created `android/app/src/main/java/com/trainerloop/ui/home/HomeScreen.kt` with:
  - Rider header showing avatar (initial), name, FTP, and weight badges.
  - "Connected Devices" card with trainer and HR rows displaying connection status, model, battery %, and live HR bpm.
  - "Quick Start" primary button to start a free ride.
  - Action rows for Workout Library and Workout Builder.
  - "Recent Workouts" list showing up to the last 5 saved sessions with date, duration, and avg power.
- Created `android/app/src/main/java/com/trainerloop/ui/home/HomeViewModel.kt` that:
  - Reads `TrainerLoopApplication` via `application.trainerLoopApp`.
  - Reads `ProfileRepository` for rider name/FTP/weight.
  - Reads `SessionRepository` for recent sessions.
  - Collects live BLE state from `FtmsManager` and `HrManager`.
- Supporting changes required by the dashboard:
  - Added `name` field to `UserProfile` and persistence in `ProfileRepository`.
  - Exposed `val device: BluetoothDevice` on `FtmsManager`, `HrManager`, and `FtmsControlManager` so the dashboard can display device names.
  - Added `androidx.compose.material:material-icons-extended` dependency for the requested icons.

## BLE manager StateFlows/Flows consumed

- `FtmsManager.isConnected` → trainer connection chip
- `FtmsManager.batteryLevel` → trainer battery %
- `FtmsManager.model` → trainer model name
- `HrManager.isConnected` → HR connection chip
- `HrManager.heartRate` → live HR bpm

## What I tested

- Ran `./gradlew :app:compileDebugKotlin` from `android/` with `ANDROID_HOME=/private/tmp/android-sdk`.
- Result: `BUILD SUCCESSFUL` with no Kotlin errors or warnings.

## Files changed

- `android/app/src/main/java/com/trainerloop/ui/home/HomeScreen.kt` (new)
- `android/app/src/main/java/com/trainerloop/ui/home/HomeViewModel.kt` (new)
- `android/app/src/main/java/com/trainerloop/data/model/UserProfile.kt`
- `android/app/src/main/java/com/trainerloop/data/repository/ProfileRepository.kt`
- `android/app/src/main/java/com/trainerloop/ble/FtmsManager.kt`
- `android/app/src/main/java/com/trainerloop/ble/HrManager.kt`
- `android/app/src/main/java/com/trainerloop/ble/FtmsControlManager.kt`
- `android/app/build.gradle.kts`

## Self-review findings

**Strengths:**
- Follows existing patterns: `AndroidViewModel`, `MutableStateFlow`/`StateFlow`, Material 3 components, repository access similar to `SettingsViewModel`.
- UI structure matches the described design sections.
- Device rows clearly show disconnected vs connected states.

**Concerns / limitations:**
1. `TrainerLoopApplication` stores managers as plain nullable vars, not observable StateFlows. `HomeViewModel` binds to the current manager instances at init; if the user swaps devices from another screen and returns to Home while the ViewModel is retained, the displayed manager references could be stale. A future improvement would be to expose managers as `StateFlow` from `TrainerLoopApplication` and rebind automatically.
2. "Workout Builder" action row is a no-op placeholder (out of scope per the plan).
3. "Start Free Ride" currently just invokes the provided callback; actual free-ride navigation will be wired in Task 1.3 / Task 4.2.
4. Recent workout date is displayed as the first 10 characters of the stored ISO string; formatting could be improved once the date format is consistent.

## Issues or concerns for the parent

- The workspace contains untracked `task-0.1-*.md` and `task-1.1-*.md` report files from earlier subagent runs. They are not committed and do not affect the build, but they clutter `git status`.
