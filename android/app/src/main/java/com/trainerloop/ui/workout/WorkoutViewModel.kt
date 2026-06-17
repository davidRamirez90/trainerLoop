package com.trainerloop.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainerloop.data.model.CoachEvent
import com.trainerloop.data.model.CoachInterventions
import com.trainerloop.data.model.CoachMessages
import com.trainerloop.data.model.CoachProfile
import com.trainerloop.data.model.CoachRules
import com.trainerloop.data.model.CoachSuggestion
import com.trainerloop.data.model.CoachVoice
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.data.model.Workout
import com.trainerloop.data.repository.SessionRepository
import com.trainerloop.domain.CoachEngine
import com.trainerloop.domain.WorkoutClock
import com.trainerloop.domain.WorkoutMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WorkoutUiState(
  val isRunning: Boolean = false,
  val isComplete: Boolean = false,
  val elapsedSec: Int = 0,
  val activeSec: Int = 0,
  val segmentIndex: Int = 0,
  val segmentStartSec: Int = 0,
  val segmentEndSec: Int = 0,
  val elapsedInSegmentSec: Int = 0,
  val targetRange: TargetRange = TargetRange(0, 0),
  val currentPowerWatts: Int = 0,
  val currentCadenceRpm: Int = 0,
  val currentHrBpm: Int = 0,
  val samples: List<TelemetrySample> = emptyList(),
  val intensityOffsetPct: Int = 0,
  val isErgEnabled: Boolean = true,
  val error: String? = null,
  // Coach state
  val pendingSuggestion: CoachSuggestion? = null,
  val coachEvents: List<CoachEvent> = emptyList()
)

class WorkoutViewModel(
  private val workout: Workout,
  private val sessionRepository: SessionRepository? = null
) : ViewModel() {

  private val clock = WorkoutClock(workout.segments, Dispatchers.Default)
  private val coachEngine = CoachEngine(defaultProfile(), workout.segments)

  private val _uiState = MutableStateFlow(WorkoutUiState())
  val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

  private var pendingSamples = mutableListOf<TelemetrySample>()

  init {
    viewModelScope.launch {
      clock.elapsedSec.collect { elapsed ->
        updateFromClock()
        tickCoach()
      }
    }
    viewModelScope.launch {
      clock.isRunning.collect { running ->
        _uiState.value = _uiState.value.copy(isRunning = running)
      }
    }
    viewModelScope.launch {
      clock.isComplete.collect { complete ->
        _uiState.value = _uiState.value.copy(isComplete = complete)
        if (complete) tickCoach()
      }
    }
    viewModelScope.launch {
      coachEngine.events.collect { events ->
        _uiState.value = _uiState.value.copy(coachEvents = events)
      }
    }
    viewModelScope.launch {
      coachEngine.pendingSuggestion.collect { suggestion ->
        _uiState.value = _uiState.value.copy(pendingSuggestion = suggestion)
      }
    }
  }

  fun start() {
    clock.start()
    updateFromClock()
  }

  fun pause() {
    clock.pause()
  }

  fun resume() {
    clock.resume()
    updateFromClock()
  }

  fun stop() {
    clock.stop()
    _uiState.value = _uiState.value.copy(
      samples = emptyList(),
      intensityOffsetPct = 0
    )
  }

  fun seek(seconds: Int) {
    clock.seek(seconds)
    updateFromClock()
  }

  fun setTelemetry(power: Int, cadence: Int, hr: Int) {
    val state = _uiState.value
    val sample = TelemetrySample(
      timeSec = state.elapsedSec,
      powerWatts = power,
      cadenceRpm = cadence,
      hrBpm = hr
    )
    pendingSamples.add(sample)
    _uiState.value = state.copy(
      currentPowerWatts = power,
      currentCadenceRpm = cadence,
      currentHrBpm = hr,
      samples = pendingSamples.toList()
    )
  }

  fun toggleErg() {
    _uiState.value = _uiState.value.copy(
      isErgEnabled = !_uiState.value.isErgEnabled
    )
  }

  fun adjustIntensityUp() {
    val current = _uiState.value.intensityOffsetPct
    val newOffset = (current + 5).coerceAtMost(20)
    _uiState.value = _uiState.value.copy(intensityOffsetPct = newOffset)
  }

  fun adjustIntensityDown() {
    val current = _uiState.value.intensityOffsetPct
    val newOffset = (current - 5).coerceAtLeast(-20)
    _uiState.value = _uiState.value.copy(intensityOffsetPct = newOffset)
  }

  // Coach suggestion handlers
  fun acceptSuggestion(suggestionId: String) {
    viewModelScope.launch {
      coachEngine.accept(suggestionId)
    }
  }

  fun rejectSuggestion(suggestionId: String) {
    viewModelScope.launch {
      coachEngine.reject(suggestionId)
    }
  }

  private fun tickCoach() {
    val state = _uiState.value
    // Build a synthetic TelemetrySample for each tick so the coach
    // can evaluate adherence. For real usage, samples come from BLE.
    if (state.elapsedSec > 0 && pendingSamples.lastOrNull()?.timeSec != state.elapsedSec) {
      val sample = TelemetrySample(
        timeSec = state.elapsedSec,
        powerWatts = state.currentPowerWatts,
        cadenceRpm = state.currentCadenceRpm,
        hrBpm = state.currentHrBpm
      )
      pendingSamples.add(sample)
      _uiState.value = state.copy(samples = pendingSamples.toList())
    }

    viewModelScope.launch {
      coachEngine.tick(
        CoachEngine.Input(
          activeSec = state.activeSec,
          isRunning = state.isRunning,
          isComplete = state.isComplete,
          hasPlan = true,
          sessionId = clock.sessionId.value,
          segmentIndex = state.segmentIndex,
          elapsedInSegmentSec = state.elapsedInSegmentSec,
          segmentStartSec = state.segmentStartSec,
          segmentEndSec = state.segmentEndSec,
          targetRange = state.targetRange,
          samples = state.samples,
          intensityOffsetPct = state.intensityOffsetPct,
          ergEnabled = state.isErgEnabled
        )
      )
    }
  }

  private fun updateFromClock() {
    val elapsed = clock.elapsedSec.value
    val active = clock.activeSec.value
    val segIndex = WorkoutMath.segmentIndexAt(workout.segments, elapsed)
    val segStart = workout.segments.take(segIndex).sumOf { it.durationSec }
    val segEnd = segStart + (workout.segments.getOrNull(segIndex)?.durationSec ?: 0)
    val elapsedInSeg = (elapsed - segStart).coerceIn(0, workout.segments.getOrNull(segIndex)?.durationSec ?: 0)
    val offset = _uiState.value.intensityOffsetPct
    val target = WorkoutMath.targetRangeAt(workout.segments, elapsed)
    val adjustedTarget = if (offset != 0) {
      val mid = (target.low + target.high) / 2
      val factor = 1.0 + offset / 100.0
      val adjusted = (mid * factor).toInt()
      TargetRange(adjusted, adjusted)
    } else target

    _uiState.value = _uiState.value.copy(
      elapsedSec = elapsed,
      activeSec = active,
      segmentIndex = segIndex,
      segmentStartSec = segStart,
      segmentEndSec = segEnd,
      elapsedInSegmentSec = elapsedInSeg,
      targetRange = adjustedTarget
    )
  }

  override fun onCleared() {
    clock.stop()
    super.onCleared()
  }

  companion object {
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
      voice = CoachVoice(tone = "neutral", style = "concise"),
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
        completion = listOf("Session complete."),
        encouragement = emptyList()
      )
    )
  }
}
