package com.trainerloop.domain

import app.cash.turbine.test
import com.trainerloop.data.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoachEngineTest {

  @Test
  fun `initial state has no events or pending suggestion`() = runTest {
    val engine = CoachEngine(defaultProfile(), emptyList())
    engine.events.test {
      assertEquals(emptyList<CoachEvent>(), awaitItem())
    }
    engine.pendingSuggestion.test {
      assertNull(awaitItem())
    }
  }

  @Test
  fun `reset clears suggestions and events on session change`() = runTest {
    val segments = listOf(workSegment(durationSec = 120))
    val engine = CoachEngine(defaultProfile(), segments)
    val samples = lowPowerHighDriftSamples(endSec = 35, count = 31)

    engine.tick(input(segmentIndex = 0, elapsedInSegmentSec = 35, activeSec = 35, samples = samples, sessionId = 1))
    assertNotNull(engine.pendingSuggestion.value)

    engine.tick(input(segmentIndex = 0, elapsedInSegmentSec = 0, activeSec = 0, samples = emptyList(), sessionId = 2))
    assertNull(engine.pendingSuggestion.value)
    assertTrue(engine.events.value.isEmpty())
  }

  @Test
  fun `no suggestion before min elapsed seconds`() = runTest {
    val segments = listOf(workSegment(durationSec = 120))
    val engine = CoachEngine(defaultProfile(), segments)
    val samples = lowPowerSamples(endSec = 35, count = 31)

    engine.tick(input(segmentIndex = 0, elapsedInSegmentSec = 35, activeSec = 5, samples = samples, sessionId = 1))
    assertNull(engine.pendingSuggestion.value)
  }

  @Test
  fun `suggests adjust intensity down when power low and hr drift high`() = runTest {
    val segments = listOf(workSegment(durationSec = 120))
    val engine = CoachEngine(defaultProfile(), segments)
    val samples = lowPowerHighDriftSamples(endSec = 35, count = 31)

    engine.tick(input(segmentIndex = 0, elapsedInSegmentSec = 35, activeSec = 35, samples = samples, sessionId = 1))
    val pending = engine.pendingSuggestion.value
    assertNotNull(pending)
    assertTrue(pending!!.action is CoachAction.AdjustIntensityDown)
  }

  @Test
  fun `suggests adjust intensity up when stable and on target`() = runTest {
    val segments = listOf(workSegment(durationSec = 120))
    val engine = CoachEngine(defaultProfile(), segments)
    val samples = stableSamples(endSec = 50, count = 50)

    engine.tick(input(segmentIndex = 0, elapsedInSegmentSec = 50, activeSec = 50, samples = samples, sessionId = 1))
    val pending = engine.pendingSuggestion.value
    assertNotNull(pending)
    assertTrue(pending!!.action is CoachAction.AdjustIntensityUp)
  }

  @Test
  fun `pending suggestion blocks new suggestions`() = runTest {
    val segments = listOf(workSegment(durationSec = 120))
    val engine = CoachEngine(defaultProfile(), segments)

    engine.tick(input(segmentIndex = 0, elapsedInSegmentSec = 35, activeSec = 35, samples = lowPowerHighDriftSamples(endSec = 35, count = 31), sessionId = 1))
    assertNotNull(engine.pendingSuggestion.value)

    engine.tick(input(segmentIndex = 0, elapsedInSegmentSec = 60, activeSec = 60, samples = lowPowerHighDriftSamples(endSec = 60, count = 31), sessionId = 1))
    assertEquals(1, engine.suggestions.value.size)
  }

  @Test
  fun `cooldown prevents immediate second suggestion`() = runTest {
    val segments = listOf(workSegment(durationSec = 120))
    val profile = defaultProfile().copy(rules = defaultProfile().rules.copy(cooldownSeconds = 60))
    val engine = CoachEngine(profile, segments)

    engine.tick(input(segmentIndex = 0, elapsedInSegmentSec = 35, activeSec = 35, samples = lowPowerHighDriftSamples(endSec = 35, count = 31), sessionId = 1))
    engine.reject(engine.pendingSuggestion.value!!.id)

    engine.tick(input(segmentIndex = 0, elapsedInSegmentSec = 50, activeSec = 50, samples = lowPowerHighDriftSamples(endSec = 50, count = 31), sessionId = 1))
    assertEquals(1, engine.suggestions.value.size)
  }

  @Test
  fun `accept updates suggestion status and emits event`() = runTest {
    val segments = listOf(workSegment(durationSec = 120))
    val engine = CoachEngine(defaultProfile(), segments)

    engine.tick(input(segmentIndex = 0, elapsedInSegmentSec = 35, activeSec = 35, samples = lowPowerHighDriftSamples(endSec = 35, count = 31), sessionId = 1))
    val suggestion = engine.pendingSuggestion.value!!
    engine.accept(suggestion.id)

    assertEquals(SuggestionStatus.ACCEPTED, engine.suggestions.value.first().status)
    val decisionEvent = engine.events.value.last()
    assertEquals(CoachEventType.SUGGESTION, decisionEvent.type)
    assertNotNull(decisionEvent.userResponse)
    assertEquals(ResponseType.ACCEPTED, decisionEvent.userResponse!!.response)
  }

  @Test
  fun `reject updates suggestion status and records down suggestion`() = runTest {
    val segments = listOf(workSegment(durationSec = 120))
    val engine = CoachEngine(defaultProfile(), segments)

    engine.tick(input(segmentIndex = 0, elapsedInSegmentSec = 35, activeSec = 35, samples = lowPowerHighDriftSamples(endSec = 35, count = 31), sessionId = 1))
    val suggestion = engine.pendingSuggestion.value!!
    engine.reject(suggestion.id)

    assertEquals(SuggestionStatus.REJECTED, engine.suggestions.value.first().status)
  }

  @Test
  fun `suggests extend recovery on transition to recovery when interval failed`() = runTest {
    val work = workSegment(durationSec = 60, id = "work")
    val recovery = recoverySegment(durationSec = 60, id = "recovery")
    val engine = CoachEngine(defaultProfile(), listOf(work, recovery))

    engine.tick(input(segmentIndex = 0, elapsedInSegmentSec = 60, activeSec = 5, segmentStartSec = 0, segmentEndSec = 60, samples = failedIntervalSamples(0, 60), sessionId = 1))
    engine.tick(input(segmentIndex = 1, elapsedInSegmentSec = 0, activeSec = 11, segmentStartSec = 60, segmentEndSec = 120, samples = failedIntervalSamples(0, 60), sessionId = 1))

    val pending = engine.pendingSuggestion.value
    assertNotNull(pending)
    assertTrue(pending!!.action is CoachAction.ExtendRecovery)
  }

  @Test
  fun `suggests skip remaining after repeated failures and rejected down suggestions`() = runTest {
    val segments = listOf(
      workSegment(durationSec = 60, id = "w1"),
      recoverySegment(durationSec = 30, id = "r1"),
      workSegment(durationSec = 60, id = "w2"),
      recoverySegment(durationSec = 30, id = "r2")
    )
    val profile = defaultProfile().copy(
      interventions = defaultProfile().interventions.copy(allowSkipRemainingOnIntervals = true),
      rules = defaultProfile().rules.copy(cooldownSeconds = 0)
    )
    val engine = CoachEngine(profile, segments)

    val w1Samples = highCadenceVarianceSamples(0, 60)
    engine.tick(input(segmentIndex = 0, elapsedInSegmentSec = 35, activeSec = 35, segmentStartSec = 0, segmentEndSec = 60, samples = w1Samples, sessionId = 1))
    engine.reject(engine.pendingSuggestion.value!!.id)

    val w2Samples = highCadenceVarianceSamples(90, 60)
    engine.tick(input(segmentIndex = 2, elapsedInSegmentSec = 35, activeSec = 95, segmentStartSec = 90, segmentEndSec = 150, samples = w1Samples + w2Samples, sessionId = 1))
    engine.reject(engine.pendingSuggestion.value!!.id)

    engine.tick(input(segmentIndex = 3, elapsedInSegmentSec = 0, activeSec = 151, segmentStartSec = 150, segmentEndSec = 180, samples = w1Samples + w2Samples, sessionId = 1))

    val pending = engine.pendingSuggestion.value
    assertNotNull(pending)
    assertTrue(pending!!.action is CoachAction.SkipRemainingOnIntervals)
  }

  @Test
  fun `emits completion event when workout complete`() = runTest {
    val segments = listOf(workSegment(durationSec = 60))
    val engine = CoachEngine(defaultProfile(), segments)

    engine.tick(input(segmentIndex = 0, elapsedInSegmentSec = 60, activeSec = 60, isRunning = false, isComplete = true, samples = emptyList(), sessionId = 1))

    val completion = engine.events.value.find { it.type == CoachEventType.COMPLETION }
    assertNotNull(completion)
  }

  private fun input(
    activeSec: Int = 0,
    isRunning: Boolean = true,
    isComplete: Boolean = false,
    hasPlan: Boolean = true,
    sessionId: Int = 1,
    segmentIndex: Int = 0,
    elapsedInSegmentSec: Int = 0,
    segmentStartSec: Int = 0,
    segmentEndSec: Int = 60,
    targetRange: TargetRange = TargetRange(200, 200),
    samples: List<TelemetrySample>,
    intensityOffsetPct: Int = 0,
    ergEnabled: Boolean = false
  ): CoachEngine.Input = CoachEngine.Input(
    activeSec = activeSec,
    isRunning = isRunning,
    isComplete = isComplete,
    hasPlan = hasPlan,
    sessionId = sessionId,
    segmentIndex = segmentIndex,
    elapsedInSegmentSec = elapsedInSegmentSec,
    segmentStartSec = segmentStartSec,
    segmentEndSec = segmentEndSec,
    targetRange = targetRange,
    samples = samples,
    intensityOffsetPct = intensityOffsetPct,
    ergEnabled = ergEnabled
  )

  private fun defaultProfile(): CoachProfile = CoachProfile(
    id = "default",
    name = "Default",
    description = "Default coach",
    rules = CoachRules(
      targetAdherenceWarn = 95.0,
      targetAdherenceIntervene = 85.0,
      hrDriftWarn = 5.0,
      hrDriftIntervene = 10.0,
      cadenceVarianceWarn = 10.0,
      cadenceVarianceIntervene = 20.0,
      minElapsedSecondsForSuggestions = 10,
      cooldownSeconds = 10
    ),
    interventions = CoachInterventions(
      intensityAdjustStepPct = 5.0,
      intensityAdjustMinPct = -20.0,
      intensityAdjustMaxPct = 20.0,
      recoveryExtendStepSec = 30,
      recoveryExtendMaxSec = 120,
      allowSkipRemainingOnIntervals = false
    ),
    messages = CoachMessages(
      suggestions = mapOf(
        "adjust_intensity_up" to listOf("Increase intensity by {{percent}}%."),
        "adjust_intensity_up_rationale" to listOf("Metrics indicate you can handle more intensity."),
        "adjust_intensity_down" to listOf("Decrease intensity by {{percent}}%."),
        "adjust_intensity_down_rationale" to listOf("Fatigue indicators suggest reducing intensity."),
        "extend_recovery" to listOf("Extend recovery by {{seconds}} seconds."),
        "extend_recovery_rationale" to listOf("Recovery metrics indicate more time needed."),
        "skip_remaining_on_intervals" to listOf("Skip remaining intervals."),
        "skip_remaining_on_intervals_rationale" to listOf("Multiple indicators suggest terminating the session.")
      ),
      completion = listOf("Session complete.")
    )
  )

  private fun workSegment(durationSec: Int, id: String = "work"): WorkoutSegment.Step =
    WorkoutSegment.Step(
      id = id,
      durationSec = durationSec,
      label = null,
      phase = SegmentPhase.WORK,
      isWork = true,
      targetRange = TargetRange(200, 200)
    )

  private fun recoverySegment(durationSec: Int, id: String = "recovery"): WorkoutSegment.Step =
    WorkoutSegment.Step(
      id = id,
      durationSec = durationSec,
      label = null,
      phase = SegmentPhase.RECOVERY,
      isWork = false,
      targetRange = TargetRange(0, 0)
    )

  private fun lowPowerSamples(endSec: Int, count: Int): List<TelemetrySample> =
    (0 until count).map { i ->
      TelemetrySample(timeSec = endSec - count + 1 + i, powerWatts = 140, cadenceRpm = 85, hrBpm = 150)
    }

  private fun lowPowerHighDriftSamples(endSec: Int, count: Int): List<TelemetrySample> =
    (0 until count).map { i ->
      TelemetrySample(timeSec = endSec - count + 1 + i, powerWatts = 140, cadenceRpm = 85, hrBpm = 150 + i * 2)
    }

  private fun stableSamples(endSec: Int, count: Int): List<TelemetrySample> =
    (0 until count).map { i ->
      TelemetrySample(timeSec = endSec - count + 1 + i, powerWatts = 200, cadenceRpm = 90, hrBpm = 150)
    }

  private fun failedIntervalSamples(startSec: Int, count: Int): List<TelemetrySample> =
    (0 until count).map { i ->
      TelemetrySample(timeSec = startSec + i, powerWatts = 140, cadenceRpm = 70, hrBpm = 150 + i)
    }

  private fun highCadenceVarianceSamples(startSec: Int, count: Int): List<TelemetrySample> =
    (0 until count).map { i ->
      TelemetrySample(timeSec = startSec + i, powerWatts = 140, cadenceRpm = if (i % 2 == 0) 60 else 100, hrBpm = 150)
    }
}
