package com.trainerloop.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CoachModelTest {
  @Test
  fun `coach profile can be constructed and action applied`() {
    val profile = defaultCoachProfile()
    assertEquals("Default Coach", profile.name)

    val suggestion = CoachSuggestion(
      id = "s1",
      action = CoachAction.AdjustIntensityUp(5),
      message = "Increase intensity",
      rationale = "Power is below target",
      segmentIndex = 0
    )
    assertEquals(SuggestionStatus.PENDING, suggestion.status)
    assertEquals("AdjustIntensityUp", suggestion.action::class.simpleName)
  }

  private fun defaultCoachProfile(): CoachProfile = CoachProfile(
    id = "default",
    name = "Default Coach",
    description = "Balanced coach that gives timely suggestions.",
    rules = CoachRules(
      targetAdherenceWarn = 0.85,
      targetAdherenceIntervene = 0.70,
      hrDriftWarn = 0.05,
      hrDriftIntervene = 0.10,
      cadenceVarianceWarn = 0.10,
      cadenceVarianceIntervene = 0.20,
      minElapsedSecondsForSuggestions = 120,
      cooldownSeconds = 180
    ),
    interventions = CoachInterventions(
      intensityAdjustStepPct = 0.05,
      intensityAdjustMinPct = -0.20,
      intensityAdjustMaxPct = 0.20,
      recoveryExtendStepSec = 30,
      recoveryExtendMaxSec = 300,
      allowSkipRemainingOnIntervals = true
    ),
    voice = CoachVoice(
      tone = "encouraging",
      style = "concise"
    ),
    messages = CoachMessages(
      suggestions = mapOf(
        "lowPower" to listOf("Try to lift power a bit.", "Pedal with more intent."),
        "highHr" to listOf("Ease off slightly.")
      ),
      completion = listOf("Workout complete!", "Great job."),
      encouragement = listOf("Keep it up!", "Smooth pedal stroke.")
    )
  )
}
