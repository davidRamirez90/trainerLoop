# Code Review: Task 0.1 — Create TrainerLoopApplication

## Scope

- `android/app/src/main/java/com/trainerloop/app/TrainerLoopApplication.kt` (new)
- `android/app/src/main/AndroidManifest.xml` (modified)
- Diff range: `bf2a7f9..55c951c`

## Strengths

- **Plan compliance:** The implementation matches the Task 0.1 specification verbatim, including all required fields (`FtmsManager`, `HrManager`, `FtmsControlManager`, `selectedWorkout`, `pendingSessionSamples`), helper methods (`attachTrainer`, `attachHr`, `clearDevices`), and the `Context.trainerLoopApp` extension.
- **Focused change:** Only the two files required by the plan were modified/created; no scope creep.
- **Visibility control:** BLE manager properties use `private set`, which correctly exposes read access while limiting mutations to the Application class's own methods.
- **Manifest registration:** `android:name=".TrainerLoopApplication"` is added to the existing `<application>` tag without disturbing permissions, activities, services, or providers.
- **Build artifacts present:** The `android/app/build/tmp/kapt3/stubs/debug/` and `kotlin-classes/debug/` outputs for `TrainerLoopApplication` exist in the working tree, indicating the code was compiled successfully on the author's machine.

## Issues

### Critical (Must Fix)

None.

### Important (Should Fix)

#### 1. BLE resource leak when replacing or clearing managers

- **File:** `android/app/src/main/java/com/trainerloop/app/TrainerLoopApplication.kt`
- **Lines:** 25-26 (`attachTrainer`), 30-31 (`attachHr`), 35-38 (`clearDevices`)
- **What's wrong:** Overwriting `ftmsManager`, `ftmsControlManager`, or `hrManager` with a new instance, or clearing them to `null`, does not disconnect the underlying `BleConnection`. Each manager owns an active GATT connection and a `CoroutineScope` (`SupervisorJob` + `Dispatchers.Main`) that remains alive after the reference is dropped.
- **Why it matters:** This leaks BluetoothGatt callbacks and subscription collectors. If a user attaches a second trainer/HR monitor, or if `clearDevices()` is called, the old BLE connection stays open, consuming radio resources and continuing to emit into the stale manager's `StateFlow`s.
- **How to fix:** Disconnect the previous manager(s) before reassigning or clearing them. `FtmsManager.disconnect()`, `HrManager.disconnect()`, and `FtmsControlManager.disconnect()` are all `suspend`, so the helper methods will need to become `suspend` or launch cleanup in a dedicated `SupervisorJob` scope owned by the Application. Example:

```kotlin
class TrainerLoopApplication : Application() {
  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  fun attachTrainer(device: BluetoothDevice) {
    appScope.launch { disconnectTrainer() }
    ftmsManager = FtmsManager(this, device)
    ftmsControlManager = FtmsControlManager(this, device)
  }

  fun attachHr(device: BluetoothDevice) {
    appScope.launch { disconnectHr() }
    hrManager = HrManager(this, device)
  }

  fun clearDevices() {
    appScope.launch {
      disconnectTrainer()
      disconnectHr()
    }
  }

  private suspend fun disconnectTrainer() {
    ftmsManager?.disconnect()
    ftmsControlManager?.disconnect()
    ftmsManager = null
    ftmsControlManager = null
  }

  private suspend fun disconnectHr() {
    hrManager?.disconnect()
    hrManager = null
  }
}
```

(Alternatively, make `clearDevices()` suspend and call it from lifecycle-aware callers if that fits the next tasks.)

### Minor (Nice to Have)

#### 2. Unsafe cast in `Context.trainerLoopApp`

- **File:** `android/app/src/main/java/com/trainerloop/app/TrainerLoopApplication.kt`
- **Lines:** 40-41
- **What's wrong:** `applicationContext as TrainerLoopApplication` will throw `ClassCastException` if the manifest is misconfigured or if the extension is invoked from a non-default process that uses a different `Application` class.
- **Why it matters:** A configuration mistake becomes a runtime crash rather than a recoverable diagnostic.
- **How to fix:** Use a checked cast with a clear error message:

```kotlin
val Context.trainerLoopApp: TrainerLoopApplication
  get() = applicationContext as? TrainerLoopApplication
    ?: throw IllegalStateException("applicationContext is not a TrainerLoopApplication")
```

#### 3. Cross-screen state is mutable and unsynchronized

- **File:** `android/app/src/main/java/com/trainerloop/app/TrainerLoopApplication.kt`
- **Lines:** 21-23
- **What's wrong:** `selectedWorkout` and `pendingSessionSamples` are mutable `var`s with no synchronization mechanism.
- **Why it matters:** These fields are intended to be shared across screens (ViewModels / Compose). Reads and writes from background threads can race, leading to visibility issues or inconsistent state.
- **How to fix:** Either document that these fields must be accessed only on the main thread, or guard them with `volatile`/atomic references, or expose them through a small repository/ holder with a mutex if concurrent access is expected.

#### 4. No unit tests for the new class

- **File:** `android/app/src/test/java/...`
- **What's wrong:** There is no test verifying the Application holder behavior.
- **Why it matters:** Future refactorings of attach/clear logic risk regressing the lifecycle or state-holding contract.
- **How to fix:** Add a Robolectric-based test that constructs `TrainerLoopApplication`, verifies initial null state, attaches/clears mocked devices, and asserts manager non-null/null transitions. This is optional for Task 0.1 but becomes more valuable once cleanup logic is added.

## Recommendations

1. **Fix the cleanup leak before merging.** This is the only issue that affects runtime correctness and resource usage.
2. **Introduce an Application-scoped coroutine scope** (`SupervisorJob`) for cleanup and any future background work, and cancel it in `onTerminate()` or `onLowMemory()` if appropriate.
3. **Document thread-safety expectations** for `selectedWorkout` and `pendingSessionSamples` once consumers start reading/writing them from ViewModels.
4. **Add a regression test** when the cleanup behavior is implemented.

## Build Verification

- Author reported `./gradlew :app:compileDebugKotlin` as `BUILD SUCCESSFUL`.
- Local verification in this environment failed because `ANDROID_HOME` is not configured and `android/local.properties` is missing; this is an environment limitation, not a code issue. Build artifacts present in the working tree corroborate that compilation succeeded elsewhere.

## Assessment

**Ready to merge?** With fixes.

**Reasoning:** The implementation exactly matches the Task 0.1 plan and compiles cleanly, but the `attachTrainer`, `attachHr`, and `clearDevices` methods drop active BLE manager references without disconnecting them first. This leaks GATT connections and coroutine scopes, which is a genuine runtime defect in a class whose sole purpose is to host those managers. Once cleanup is added (e.g., disconnect old instances before reassigning/clearing), the change is ready to merge.
