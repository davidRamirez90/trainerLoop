package com.trainerloop.ui.screenshots

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.Workout
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.data.model.WorkoutSource
import com.trainerloop.ui.home.HomeScreenContent
import com.trainerloop.ui.home.HomeUiState
import com.trainerloop.ui.theme.TrainerLoopTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-xxhdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenshotTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun homePlannedLight() = captureHome(darkTheme = false, name = "home-planned-light")

  @Test
  fun homePlannedDark() = captureHome(darkTheme = true, name = "home-planned-dark")

  private fun captureHome(darkTheme: Boolean, name: String) {
    val workout = Workout(
      id = "screenshot-threshold",
      name = "Threshold 3 × 10",
      description = "Representative planned workout",
      source = WorkoutSource.MANUAL,
      segments = listOf(
        WorkoutSegment.Ramp("warmup", 600, "Warm up", SegmentPhase.WARMUP, false, 100, 200),
        WorkoutSegment.Step("work-1", 600, "Threshold", SegmentPhase.WORK, true, TargetRange(235, 250)),
        WorkoutSegment.Step("recovery-1", 180, "Recover", SegmentPhase.RECOVERY, false, TargetRange(100, 125)),
        WorkoutSegment.Step("work-2", 600, "Threshold", SegmentPhase.WORK, true, TargetRange(235, 250)),
        WorkoutSegment.Step("recovery-2", 180, "Recover", SegmentPhase.RECOVERY, false, TargetRange(100, 125)),
        WorkoutSegment.Step("work-3", 600, "Threshold", SegmentPhase.WORK, true, TargetRange(235, 250)),
        WorkoutSegment.Ramp("cooldown", 300, "Cool down", SegmentPhase.COOLDOWN, false, 150, 80)
      )
    )
    compose.setContent {
      TrainerLoopTheme(darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          HomeScreenContent(
            uiState = HomeUiState(
              riderName = "Alex Morgan", ftp = 250, weightKg = 72.5,
              trainerModel = "KICKR CORE", isTrainerConnected = true,
              trainerBattery = 85, isHrConnected = true, latestHrBpm = 124,
              plannedName = workout.name, plannedWorkout = workout
            ),
            today = java.time.LocalDate.of(2026, 10, 11),
            onNavigateToDevices = {}, onNavigateToWorkouts = {},
            onChooseWorkout = {},
            onNavigateToProfile = {}, onStartPlanned = {}
          )
        }
      }
    }
    compose.waitForIdle()
    compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
  }
}
