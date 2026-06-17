# Task 0.1 Race Condition Fix

## What Changed

Rewrote `TrainerLoopApplication.attachTrainer`, `attachHr`, and `clearDevices` to avoid a race where the asynchronous disconnect coroutine would run after new manager instances were already assigned, causing it to disconnect the newly created managers.

The fix captures the previous manager references into local variables **before** reassigning the properties, then launches the disconnect work on those captured references:

- `attachTrainer` captures previous `ftmsManager` and `ftmsControlManager`, assigns new instances, then disconnects the previous ones in `appScope`.
- `attachHr` captures previous `hrManager`, assigns the new instance, then disconnects the previous one in `appScope`.
- `clearDevices` captures all three current managers, synchronously clears the properties, then disconnects the captured previous instances in `appScope`.

Removed the private `disconnectTrainer()` and `disconnectHr()` helpers because the disconnect logic is now handled inline with captured references.

## Build Verification

```bash
cd android/
ANDROID_HOME=/private/tmp/android-sdk ./gradlew :app:compileDebugKotlin
```

Result: `BUILD SUCCESSFUL`

## Commit

Amended the Task 0.1 commit so the branch history remains a single commit for this task:

```
459825f feat: add application-scoped BLE manager holder
```

## Issues or Concerns

None. The public API remains unchanged and non-suspend, and the Application-scoped `CoroutineScope` and `onTerminate()` override are preserved.
