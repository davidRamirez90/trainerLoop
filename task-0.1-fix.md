# Task 0.1 Quality Fix Report

## Change

Updated `android/app/src/main/java/com/trainerloop/app/TrainerLoopApplication.kt` to prevent BLE resource leaks when replacing or clearing managers.

Specifically:
- Added an Application-scoped `CoroutineScope` (`SupervisorJob()` + `Dispatchers.Main`) to perform asynchronous cleanup without exposing suspend functions in the public API.
- Added private `disconnectTrainer()` and `disconnectHr()` helpers that call each manager's `suspend fun disconnect()` and then null the property.
- `attachTrainer(device)` now launches `disconnectTrainer()` in `appScope` before creating the new `FtmsManager` and `FtmsControlManager`.
- `attachHr(device)` now launches `disconnectHr()` in `appScope` before creating the new `HrManager`.
- `clearDevices()` now launches both disconnect helpers in `appScope` before clearing references.
- Added `onTerminate()` override to cancel the `appScope` when the Application is terminated.
- Kept public API signatures (`attachTrainer`, `attachHr`, `clearDevices`) non-suspend and non-blocking.
- Preserved the existing properties and the `Context.trainerLoopApp` extension.

## Build Verification

Ran:

```bash
cd android
ANDROID_HOME=/private/tmp/android-sdk ./gradlew :app:compileDebugKotlin
```

Result: `BUILD SUCCESSFUL`

## Commit

Amended the previous Task 0.1 commit so the final history is a single commit:

```
9b37cc5 feat: add application-scoped BLE manager holder
```

## Issues / Concerns

None. The fix compiles cleanly and addresses the resource-leak concern identified by the code quality reviewer.
