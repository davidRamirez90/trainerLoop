package com.trainerloop.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.data.model.Workout
import com.trainerloop.data.repository.SessionRepository
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
  val error: String? = null
)

class WorkoutViewModel(
  private val workout: Workout,
  private val sessionRepository: SessionRepository? = null
) : ViewModel() {

  private val clock = WorkoutClock(workout.segments, Dispatchers.Default)

  private val _uiState = MutableStateFlow(WorkoutUiState())
  val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

  init {
    // Observe clock
    viewModelScope.launch {
      clock.elapsedSec.collect { elapsed ->
        updateFromClock()
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
    _uiState.value = _uiState.value.copy(
      currentPowerWatts = power,
      currentCadenceRpm = cadence,
      currentHrBpm = hr
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
}
