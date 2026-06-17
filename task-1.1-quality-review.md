# Code Quality Review — Task 1.1: Define bottom-tab screens

**Git range:** `459825f2..79e46f4`
**Files changed:**
- `android/app/src/main/java/com/trainerloop/ui/navigation/Screen.kt`
- `android/app/src/main/java/com/trainerloop/ui/TrainerLoopApp.kt`

## Strengths

- **`Screen.kt` matches the Task 1.1 spec exactly.** The sealed class cleanly separates bottom-tab destinations (`Home`, `Workouts`, `Ride`, `History`, `Profile`) from nested flow destinations (`Devices`, `WorkoutDetail`, `WorkoutPlayer`, `WorkoutComplete`), and exposes `bottomTabs` as a companion list.
- **No stale `Screen` references remain.** A repo-wide search confirms the old names (`Library`, `WorkoutPreview`, `Workout`, `SessionSummary`, `Connect`, `Settings`) are gone and only the new names are used.
- **Minimal, focused change set.** Only two files were touched: the navigation contract and the single call-site file needed to keep the project compiling.
- **Build verification reported green.** `./gradlew :app:compileDebugKotlin` was reported as `BUILD SUCCESSFUL` in the implementer’s environment. (Local review build could not be run because `ANDROID_HOME` is not configured in this environment.)

## Issues

### Important (Should Fix)

#### 1. Hard-coded route string bypasses the sealed class
- **File:** `android/app/src/main/java/com/trainerloop/ui/TrainerLoopApp.kt:41`
- **What’s wrong:** `onStartWorkout` navigates using the literal string `"workout_player/1"` instead of the `Screen` object. The previous code used `Screen.Workout.createRoute(sessionId = 1)`, so this is a small regression in type safety.
- **Why it matters:** It undermines the benefit of the sealed class and creates a maintenance liability if the route pattern changes. It also forces future callers to construct route strings by hand.
- **How to fix:** Add `createRoute(...)` helpers to the parameterized flow screens and use them:
  ```kotlin
  object WorkoutDetail : Screen("workout_detail/{workoutId}") {
    fun createRoute(workoutId: String) = "workout_detail/$workoutId"
  }
  object WorkoutPlayer : Screen("workout_player/{sessionId}") {
    fun createRoute(sessionId: Int) = "workout_player/$sessionId"
  }
  object WorkoutComplete : Screen("workout_complete/{sessionId}/{workoutName}/{startTimeMs}") {
    fun createRoute(sessionId: String, workoutName: String, startTimeMs: Long): String {
      val encodedName = java.net.URLEncoder.encode(workoutName, "UTF-8")
      return "workout_complete/$sessionId/$encodedName/$startTimeMs"
    }
  }
  ```
  Then replace `"workout_player/1"` with `Screen.WorkoutPlayer.createRoute(sessionId = 1)`.

### Minor (Nice to Have)

#### 2. `WorkoutComplete` route declares three arguments but only one is registered
- **File:** `android/app/src/main/java/com/trainerloop/ui/TrainerLoopApp.kt:64`
- **What’s wrong:** The route `workout_complete/{sessionId}/{workoutName}/{startTimeMs}` has three path placeholders, but the `composable` block only declares `navArgument("sessionId")`.
- **Why it matters:** It is inconsistent and may confuse argument parsing or type coercion when the screen is eventually wired up. Navigation Compose will still parse undeclared placeholders as strings, but the mismatch is a latent bug.
- **How to fix:** Register all three arguments explicitly:
  ```kotlin
  arguments = listOf(
    navArgument("sessionId") { type = NavType.StringType },
    navArgument("workoutName") { type = NavType.StringType },
    navArgument("startTimeMs") { type = NavType.LongType }
  )
  ```

#### 3. Stale callback name on `ConnectScreen`
- **File:** `android/app/src/main/java/com/trainerloop/ui/TrainerLoopApp.kt:81`
- **What’s wrong:** The parameter is still named `onNavigateToLibrary` even though it now navigates to `Screen.Workouts.route`.
- **Why it matters:** Naming drift makes the code harder to read and search. It also conflicts with the new design vocabulary.
- **How to fix:** Rename the callback to `onNavigateToWorkouts` in both `TrainerLoopApp.kt` and `ConnectScreen.kt`.

## Recommendations

1. **Add `createRoute` helpers to all parameterized `Screen` objects now** rather than waiting for Task 4.2/7.3. It keeps navigation construction centralized and type-safe.
2. **Keep route patterns and `navArgument` declarations in sync** when introducing new placeholders to avoid runtime navigation surprises.
3. **Rename `ConnectScreen` → `DevicesScreen` and its callbacks soon** (per Task 2.1) so the temporary `onNavigateToLibrary` name does not linger.
4. **Add a lint or build step check** to catch hard-coded route strings in navigation calls (e.g., a custom Detekt/ktlint rule), since they are easy to introduce when refactoring routes.

## Assessment

**Ready to merge?** Yes

**Reasoning:** Task 1.1’s requirements are fully met: the flat route enum is replaced by a sealed class that distinguishes bottom tabs from nested flows, and the build remains green. The one important issue (hard-coded route string) is confined to temporary scaffolding that will be rewritten in upcoming tasks; it should still be fixed before the next task to avoid compounding navigation-string debt, but it is not a blocker for merging this incremental change.
