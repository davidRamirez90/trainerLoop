package com.trainerloop.ui.complete

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trainerloop.data.model.SessionData
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.data.repository.ProfileRepository
import com.trainerloop.data.repository.SessionRepository
import com.trainerloop.data.source.local.AppDatabase
import com.trainerloop.data.source.remote.IntervalsIcuClient
import com.trainerloop.domain.WorkoutSummaryMath
import com.trainerloop.ui.components.FitShareHelper
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant

data class WorkoutCompleteUiState(
  val sessionId: String = "",
  val workoutName: String = "",
  val durationSec: Int = 0,
  val avgPower: Int = 0,
  val maxPower: Int = 0,
  val avgCadence: Int = 0,
  val avgHr: Int = 0,
  val normalizedPower: Int = 0,
  val intensityFactor: Double = 0.0,
  val tss: Int = 0,
  val calories: Int = 0,
  val totalWorkKj: Int = 0,
  val fitFile: File? = null,
  val isSaved: Boolean = false,
  val isDiscarded: Boolean = false,
  val error: String? = null,
  val uploadStatus: String? = null
)

class WorkoutCompleteViewModel(
  application: Application,
  private val sessionId: String,
  private val workoutId: String,
  private val workoutName: String,
  val samples: List<TelemetrySample>,
  private val startTimeMs: Long = System.currentTimeMillis(),
  private val profileRepository: ProfileRepository = ProfileRepository(application),
  private val sessionRepository: SessionRepository = SessionRepository.create(AppDatabase.getInstance(application))
) : AndroidViewModel(application) {

  private val _uiState = MutableStateFlow(WorkoutCompleteUiState())
  val uiState: StateFlow<WorkoutCompleteUiState> = _uiState.asStateFlow()

  init {
    computeSummary()
    createFitFile()
    saveSession()
  }

  private fun computeSummary() {
    if (samples.isEmpty()) {
      _uiState.value = WorkoutCompleteUiState(
        sessionId = sessionId,
        workoutName = workoutName
      )
      return
    }

    val duration = samples.lastOrNull()?.timeSec ?: 0
    val avgPower = WorkoutSummaryMath.averagePower(samples)
    val maxPower = WorkoutSummaryMath.maxPower(samples)
    val avgCadence = WorkoutSummaryMath.averageCadence(samples)
    val avgHr = WorkoutSummaryMath.averageHr(samples)
    val np = WorkoutSummaryMath.normalizedPower(samples)
    val ftp = profileRepository.getProfileSync().ftp
    val ifactor = WorkoutSummaryMath.intensityFactor(np, ftp)
    val tss = WorkoutSummaryMath.tss(np, ftp, duration)
    val calories = WorkoutSummaryMath.caloriesKcal(avgPower, duration)
    val totalWork = WorkoutSummaryMath.totalWorkKj(avgPower, duration)

    _uiState.value = _uiState.value.copy(
      sessionId = sessionId,
      workoutName = workoutName,
      durationSec = duration,
      avgPower = avgPower,
      maxPower = maxPower,
      avgCadence = avgCadence,
      avgHr = avgHr,
      normalizedPower = np,
      intensityFactor = ifactor,
      tss = tss,
      calories = calories,
      totalWorkKj = totalWork
    )
  }

  private fun saveSession() {
    val state = _uiState.value
    if (samples.isEmpty()) return

    val samplesJson = Json.encodeToString(ListSerializer(TelemetrySample.serializer()), samples)

    viewModelScope.launch {
      try {
        sessionRepository.save(
          SessionData(
            id = sessionId,
            workoutId = workoutId,
            workoutName = workoutName,
            startedAt = Instant.ofEpochMilli(startTimeMs).toString(),
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
    if (samples.isEmpty()) return

    val context = getApplication<Application>()
    try {
      val file = FitShareHelper.createFitFile(
        context = context,
        startTimeMs = startTimeMs,
        elapsedSec = _uiState.value.durationSec,
        samples = samples
      )
      _uiState.value = _uiState.value.copy(fitFile = file)
      uploadToIntervalsIcu(file)
    } catch (e: Exception) {
      _uiState.value = _uiState.value.copy(error = "Failed to create FIT: ${e.message}")
    }
  }

  private fun uploadToIntervalsIcu(file: File) {
    val profile = profileRepository.getProfileSync()
    val athleteId = profile.intervalsIcuAthleteId
    val apiKey = profile.intervalsIcuApiKey
    if (athleteId.isBlank() || apiKey.isBlank()) return

    _uiState.value = _uiState.value.copy(uploadStatus = "Uploading…")
    viewModelScope.launch {
      try {
        val ok = IntervalsIcuClient(apiKey).uploadActivity(athleteId, file.readBytes(), workoutName)
        _uiState.value = _uiState.value.copy(
          uploadStatus = if (ok) "Uploaded to intervals.icu" else "Upload failed"
        )
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(uploadStatus = "Upload failed: ${e.message}")
      }
    }
  }

  fun onSave() {
    if (_uiState.value.isSaved) return
    saveSession()
  }

  fun onShare() {
    val file = _uiState.value.fitFile ?: return
    FitShareHelper.shareFitFile(getApplication(), file)
  }

  fun onDiscard() {
    viewModelScope.launch {
      try {
        sessionRepository.deleteById(sessionId)
        _uiState.value.fitFile?.delete()
        _uiState.value = _uiState.value.copy(isDiscarded = true, isSaved = false, fitFile = null)
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(error = "Failed to discard session: ${e.message}")
      }
    }
  }

  fun clearError() {
    _uiState.value = _uiState.value.copy(error = null)
  }
}
