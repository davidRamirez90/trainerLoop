package com.trainerloop.ui.summary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trainerloop.data.model.SessionData
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.data.repository.SessionRepository
import com.trainerloop.data.source.local.AppDatabase
import com.trainerloop.ui.components.FitShareHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant

data class SessionSummaryUiState(
  val sessionId: String = "",
  val workoutName: String = "",
  val durationSec: Int = 0,
  val avgPower: Int = 0,
  val maxPower: Int = 0,
  val avgCadence: Int = 0,
  val avgHr: Int = 0,
  val isSaved: Boolean = false,
  val fitFile: File? = null,
  val error: String? = null
)

class SessionSummaryViewModel(
  application: Application,
  private val sessionId: String,
  private val workoutName: String,
  private val samples: List<TelemetrySample>,
  private val startTimeMs: Long = System.currentTimeMillis()
) : AndroidViewModel(application) {

  private val dao = AppDatabase.getInstance(application).sessionDao()
  private val repository = SessionRepository(dao)

  private val _uiState = MutableStateFlow(SessionSummaryUiState())
  val uiState: StateFlow<SessionSummaryUiState> = _uiState.asStateFlow()

  init {
    computeSummary()
    saveSession()
    createFitFile()
  }

  private fun computeSummary() {
    if (samples.isEmpty()) return

    val duration = samples.lastOrNull()?.timeSec ?: 0
    val avgPwr = samples.map { it.powerWatts }.average().toInt()
    val maxPwr = samples.maxOf { it.powerWatts }
    val avgCad = samples.map { it.cadenceRpm }.average().toInt()
    val avgHeartRate = samples.map { it.hrBpm }.average().toInt()

    _uiState.value = _uiState.value.copy(
      sessionId = sessionId,
      workoutName = workoutName,
      durationSec = duration,
      avgPower = avgPwr,
      maxPower = maxPwr,
      avgCadence = avgCad,
      avgHr = avgHeartRate
    )
  }

  private fun saveSession() {
    val state = _uiState.value
    // Serialize samples to simple JSON for Room storage
    val samplesJson = samples.joinToString("|") { s ->
      "${s.timeSec},${s.powerWatts},${s.cadenceRpm},${s.hrBpm},${s.dropout}"
    }

    viewModelScope.launch {
      try {
        repository.save(
          SessionData(
            id = sessionId,
            workoutId = sessionId,
            workoutName = workoutName,
            startedAt = Instant.now().toString(),
            endedAt = Instant.now().toString(),
            durationSec = state.durationSec,
            samplesJson = samplesJson,
            coachEventsJson = "",
            completed = true,
            avgPower = state.avgPower,
            maxPower = state.maxPower,
            avgCadence = state.avgCadence,
            avgHr = state.avgHr
          )
        )
        _uiState.value = _uiState.value.copy(isSaved = true)
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(error = "Failed to save session: ${e.message}")
      }
    }
  }

  private fun createFitFile() {
    val context = getApplication<Application>()
    try {
      val file = FitShareHelper.createFitFile(
        context = context,
        startTimeMs = startTimeMs,
        elapsedSec = _uiState.value.durationSec,
        samples = samples
      )
      _uiState.value = _uiState.value.copy(fitFile = file)
    } catch (e: Exception) {
      _uiState.value = _uiState.value.copy(error = "Failed to create FIT: ${e.message}")
    }
  }

  fun shareFit() {
    val file = _uiState.value.fitFile ?: return
    FitShareHelper.shareFitFile(getApplication(), file)
  }

  fun clearError() {
    _uiState.value = _uiState.value.copy(error = null)
  }
}
