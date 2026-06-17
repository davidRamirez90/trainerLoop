package com.trainerloop.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainerloop.ble.FtmsControlManager
import com.trainerloop.ble.FtmsManager
import com.trainerloop.ble.HrManager
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
import com.trainerloop.domain.TelemetryRecorder
import com.trainerloop.domain.WorkoutClock
import com.trainerloop.domain.WorkoutMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

data class WorkoutFinishData(
  val workoutName: String,
  val startTimeMs: Long,
  val samples: List<TelemetrySample>
)

private data class WorkoutControlTick(
  val elapsedSec: Int,
  val running: Boolean,
  val ergEnabled: Boolean,
  val targetRange: TargetRange
)

class WorkoutViewModel(
  private val workout: Workout,
  private val ftmsManager: FtmsManager? = null,
  private val hrManager: HrManager? = null,
  private val ftmsControlManager: FtmsControlManager? = null,
  private val sessionRepository: SessionRepository? = null
) : ViewModel() {

  private val clock = WorkoutClock(workout.segments, Dispatchers.Default)
  private val coachEngine = CoachEngine(defaultProfile(), workout.segments)

  private val _uiState = MutableStateFlow(WorkoutUiState())
  val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

  private val _finishEvent = MutableStateFlow<WorkoutFinishData?>(null)
  val finishEvent: StateFlow<WorkoutFinishData?> = _finishEvent.asStateFlow()

  private val telemetryRecorder: TelemetryRecorder? =
    if (ftmsManager != null) {
      TelemetryRecorder(clock, ftmsManager, hrManager)
    } else null

  private var wasErgEnabled: Boolean = true

  private var ergWriteJob: Job? = null

  init {
    telemetryRecorder?.startCollecting()

    viewModelScope.launch {
      telemetryRecorder?.latest?.collect { sample ->
        _uiState.value = _uiState.value.copy(
          currentPowerWatts = sample.powerWatts,
          currentCadenceRpm = sample.cadenceRpm,
          currentHrBpm = sample.hrBpm
        )
      }
    }

    viewModelScope.launch {
      telemetryRecorder?.samples?.collect { samples ->
        _uiState.value = _uiState.value.copy(samples = samples)
      }
    }

    viewModelScope.launch {
      clock.elapsedSec.collect {
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
        if (complete) {
          tickCoach()
          maybeEmitFinish()
        }
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

    viewModelScope.launch {
      combine(
        clock.elapsedSec,
        _uiState.map { it.isRunning }.distinctUntilChanged(),
        _uiState.map { it.isErgEnabled }.distinctUntilChanged(),
        _uiState.map { it.targetRange }.distinctUntilChanged()
      ) { elapsedSec, running, ergEnabled, targetRange ->
        WorkoutControlTick(elapsedSec, running, ergEnabled, targetRange)
      }.collect { tick ->
        handleControlTick(tick)
      }
    }
  }

  fun start() {
    clock.start()
    updateFromClock()
    viewModelScope.launch {
      if (ftmsControlManager?.status?.value == com.trainerloop.ble.FtmsControlStatus.READY) {
        ftmsControlManager.startResume()
      }
    }
  }

  fun pause() {
    clock.pause()
    viewModelScope.launch {
      ftmsControlManager?.stopPause(stop = false)
    }
  }

  fun resume() {
    clock.resume()
    updateFromClock()
    viewModelScope.launch {
      if (ftmsControlManager?.status?.value == com.trainerloop.ble.FtmsControlStatus.READY) {
        ftmsControlManager.startResume()
      }
    }
  }

  fun stop() {
    clock.stop()
    viewModelScope.launch {
      ftmsControlManager?.stopPause(stop = true)
    }
    telemetryRecorder?.reset(clock.sessionId.value)
    _uiState.value = _uiState.value.copy(
      intensityOffsetPct = 0,
      samples = emptyList()
    )
    _finishEvent.value = null
  }

  fun seek(seconds: Int) {
    clock.seek(seconds)
    updateFromClock()
  }

  fun skipSegment() {
    val nextSegmentStart = _uiState.value.segmentEndSec
    if (nextSegmentStart < WorkoutMath.totalDurationSec(workout.segments)) {
      seek(nextSegmentStart)
    }
  }

  fun consumeFinishEvent() {
    _finishEvent.value = null
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

  fun fineIntensityUp() {
    val current = _uiState.value.intensityOffsetPct
    val newOffset = (current + 1).coerceAtMost(20)
    _uiState.value = _uiState.value.copy(intensityOffsetPct = newOffset)
  }

  fun fineIntensityDown() {
    val current = _uiState.value.intensityOffsetPct
    val newOffset = (current - 1).coerceAtLeast(-20)
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

  private fun handleControlTick(tick: WorkoutControlTick) {
    if (!tick.running || tick.targetRange == TargetRange(0, 0)) return

    if (!tick.ergEnabled) {
      if (wasErgEnabled) {
        ftmsControlManager?.let { control ->
          ergWriteJob?.cancel()
          ergWriteJob = viewModelScope.launch { control.stopPause(stop = false) }
        }
      }
      wasErgEnabled = false
      return
    }

    wasErgEnabled = true

    val target = (tick.targetRange.low + tick.targetRange.high) / 2
    ftmsControlManager?.let { control ->
      ergWriteJob?.cancel()
      ergWriteJob = viewModelScope.launch { control.setTargetPower(target.coerceIn(0, 2000)) }
    }
  }

  fun maybeEmitFinish() {
    val state = _uiState.value
    if (state.samples.isEmpty()) return
    _finishEvent.value = WorkoutFinishData(
      workoutName = workout.name,
      startTimeMs = System.currentTimeMillis() - state.elapsedSec * 1000L,
      samples = state.samples
    )
  }

  private fun tickCoach() {
    val state = _uiState.value
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
    clock.close()
    telemetryRecorder?.stop()
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
