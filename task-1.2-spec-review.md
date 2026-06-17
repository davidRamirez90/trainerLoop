# Task 1.2 Spec Review: Add HomeScreen Dashboard

**Overall verdict:** Mostly spec compliant, with minor deviations and one functional gap that should be addressed before relying on the dashboard in real navigation flows.

---

## ✅ Correct (matches the spec)

- **Files created:** `android/app/src/main/java/com/trainerloop/ui/home/HomeScreen.kt` and `android/app/src/main/java/com/trainerloop/ui/home/HomeViewModel.kt` both exist and were committed in `bc6dfb9`.
- **HomeScreen signature** matches the required API exactly (`HomeScreen.kt:33-38`):
  ```kotlin
  fun HomeScreen(
    onNavigateToDevices: () -> Unit,
    onNavigateToWorkouts: () -> Unit,
    onStartFreeRide: () -> Unit,
    viewModel: HomeViewModel = viewModel()
  )
  ```
- **Dashboard sections present:**
  - Header with avatar, name, FTP, and weight (`HomeScreen.kt:69-112`).
  - "Connected Devices" card with trainer and HR rows, including name, model, battery (trainer), and live HR (`HomeScreen.kt:139-225`).
  - "Start Free Ride" primary button (`HomeScreen.kt:227-256`).
  - Action rows for Workout Library and Workout Builder (`HomeScreen.kt:258-301`).
  - "Recent Workouts" section (`HomeScreen.kt:82-97` and `HomeScreen.kt:357-401`).
- **HomeViewModel exposes the required state:** `connectedTrainer`, `connectedHr`, `latestHrBpm`, and recent sessions via `HomeUiState` (`HomeViewModel.kt:19-31`).
- **HomeViewModel reads from `TrainerLoopApplication`:** it accesses `app.ftmsManager` and `app.hrManager` and collects their `isConnected`, `batteryLevel`/`heartRate`, and `model` flows (`HomeViewModel.kt:64-95`).
- **Supporting changes are minimal and necessary:**
  - Added `name` to `UserProfile` with default `"Rider"` (`UserProfile.kt`).
  - Persisted `name` in `ProfileRepository` (`ProfileRepository.kt`).
  - Exposed `device` on `FtmsManager` and `HrManager` (required to show device names).

---

## ❌ Issues Found

### 1. Recent Workouts section shows up to 5 sessions; spec asked for the last saved session
- **Location:** `HomeViewModel.kt:54-58`
- **Code:**
  ```kotlin
  recentSessions = sessions.sortedByDescending { it.startedAt }.take(5)
  ```
- **Issue:** The task requested a "Recent Workouts" section with **the last saved session** (singular). The implementation displays the five most recent sessions. This is extra scope, not a requested feature.
- **Suggested fix:** Change `.take(5)` to `.take(1)` (or `firstOrNull()`) and render a single card, matching the spec.

### 2. `material-icons-extended` dependency is unnecessary
- **Location:** `android/app/build.gradle.kts:59`
- **Issue:** The screen only uses standard filled icons (`Bluetooth`, `BluetoothConnected`, `Build`, `FitnessCenter`, `KeyboardArrowRight`), all of which are provided by `material-icons-core` (already a transitive dependency through Material3). The new dependency adds unused weight to the build.
- **Suggested fix:** Remove `implementation("androidx.compose.material:material-icons-extended")` unless a future screen genuinely needs an extended-only icon.

### 3. Unused import in `HomeScreen.kt`
- **Location:** `HomeScreen.kt:25`
- **Code:** `import androidx.compose.material.icons.filled.Favorite`
- **Issue:** Import is never referenced. It was likely added in anticipation of an HR icon but never used.
- **Suggested fix:** Delete the unused import.

### 4. Workout Builder action is wired to a no-op
- **Location:** `HomeScreen.kt:91`
- **Code:** `onWorkoutBuilder = { /* TODO: builder not in scope */ }`
- **Issue:** The action row is present (as required) but does nothing when tapped. The spec did not define a builder navigation callback, so this is not a hard violation, but it leaves a dead UI action. At minimum the TODO should be tracked or the row should be visually disabled until the feature exists.

### 5. `HomeViewModel` does not observe changes to `TrainerLoopApplication` manager references
- **Location:** `HomeViewModel.kt:64-95`
- **Issue:** `bindToCurrentManagers()` reads `app.ftmsManager` and `app.hrManager` once during `init`. If the user connects a new trainer or HR sensor after `HomeViewModel` is created (e.g., navigates to device management, pairs, and returns), the dashboard will continue to hold the old manager reference or show stale `null` state. It only observes the *state* of the manager object that existed at initialization, not the *existence* of a new manager.
- **Impact:** This makes the "Manage" → pair → return flow potentially show the dashboard as still disconnected until the screen/ViewModel is recreated.
- **Suggested fix:** Expose `ftmsManager`/`hrManager` as `StateFlow` or `MutableState` from `TrainerLoopApplication` and collect them in `HomeViewModel`, re-binding to the new manager when it changes.

### 6. `FtmsControlManager.device` was exposed but is not used by the dashboard
- **Location:** `android/app/src/main/java/com/trainerloop/ble/FtmsControlManager.kt:40`
- **Issue:** The spec required exposing `device` on BLE managers used by the dashboard (`FtmsManager` and `HrManager`). `FtmsControlManager` is not consumed by `HomeViewModel`, so changing its visibility is unnecessary.
- **Suggested fix:** Revert `FtmsControlManager.device` to `private` unless another feature needs it. This keeps the change minimal.

---

## 📝 Notes / Observations

- **Build verification could not be completed locally** because `ANDROID_HOME` is not configured and no `local.properties` file is present. The implementer’s claim of `./gradlew :app:compileDebugKotlin` success was not independently verified in this environment.
- **Header avatar logic** uses `name.take(1).uppercase()` (`HomeScreen.kt:79-85`). If `name` is empty, this produces an empty avatar. Consider guarding against empty names.
- **Device name retrieval** relies on `BluetoothDevice.getName()`, which returns `null` if the `BLUETOOTH_CONNECT` runtime permission is not granted. The UI falls back to `"No trainer connected"` / `"No HR sensor connected"` in that case, which could be misleading when a device is paired but the name is unavailable.
- The implementation uses a single `HomeUiState` data class with sensible defaults, keeping the UI simple and consistent with project patterns.

---

## Summary

| Aspect | Verdict |
|--------|---------|
| Required files created | ✅ Yes |
| Required composable signature | ✅ Yes |
| Dashboard layout / sections | ✅ Yes |
| ViewModel exposes required state | ✅ Yes |
| ViewModel reads BLE managers and sessions | ✅ Yes (with caveat about dynamic re-binding) |
| Recent workouts scope | ❌ Shows 5 instead of 1 |
| Extra dependency | ❌ `material-icons-extended` not needed |
| Unused import | ❌ `Favorite` import unused |
| Unnecessary `FtmsControlManager.device` change | ❌ Not needed for dashboard |
| Dynamic manager updates | ⚠️ Will miss newly attached managers after init |

**Recommendation:** Accept after addressing items 1–3 and 6 (minor cleanup). Item 5 should be fixed if the dashboard is meant to stay alive across navigation to the device-management screen.
