package com.trainerloop.ui.screenshots

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.trainerloop.ui.designproposals.HomeProposal
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-xxhdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeProposalScreenshotTest {
  @get:Rule val compose = createComposeRule()
  @Test fun focusHome() = capture(1, "proposal-1-focus")
  @Test fun consoleHome() = capture(2, "proposal-2-console")
  @Test fun journalHome() = capture(3, "proposal-3-journal")

  private fun capture(concept: Int, name: String) {
    compose.setContent { HomeProposal(concept) }
    compose.waitForIdle()
    compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
  }
}
