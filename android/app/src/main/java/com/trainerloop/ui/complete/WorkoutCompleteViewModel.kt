package com.trainerloop.ui.complete

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trainerloop.data.model.SessionData
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.data.repository.IcuActivityUploader
import com.trainerloop.data.repository.ProfileRepository
import com.trainerloop.data.repository.SessionRepository
import com.trainerloop.data.source.local.AppDatabase
import com.trainerloop.data.source.remote.IntervalsIcuClient
import com.trainerloop.domain.RampTest
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
  val distanceKm: Double = 0.0,
  val ascentM: Int = 0,
  val fitFile: File? = null,
  val isSaving: Boolean = false,
  val isSaved: Boolean = false,
  val isDiscarded: Boolean = false,
  val error: String? = null,
  val uploadStatus: String? = null,
  // FTP ramp test result
  val isRampTest: Boolean = false,
  val rampTestNewFtp: Int? = null,
  val rampTestPreviousFtp: Int = 0,
  /** True once the user accepted or discarded the new FTP (one-shot). */
  val ftpDecided: Boolean = false,
  val ftpAccepted: Boolean = false,
  val showIcuFtpPrompt: Boolean = false,
  val ftpPushStatus: String? = null,
  val coachData: com.trainerloop.domain.coach.CoachSessionData? = null
)

class WorkoutCompleteViewModel(
  application: Application,
  private val sessionId: String,
  private val workoutId: String,
  private val workoutName: String,
  val samples: List<TelemetrySample>,
  private val startTimeMs: Long = System.currentTimeMillis(),
  private val coachJson: String = "",
  private val sessionType: String = "WORKOUT",
  private val routeId: String? = null,
  private val completed: Boolean = false,
  private val profileRepository: ProfileRepository = ProfileRepository(application),
  private val sessionRepository: SessionRepository = SessionRepository.create(AppDatabase.getInstance(application))
) : AndroidViewModel(application) {

  private val _uiState = MutableStateFlow(WorkoutCompleteUiState())
  val uiState: StateFlow<WorkoutCompleteUiState> = _uiState.asStateFlow()

  init {
    computeSummary()
    _uiState.value = _uiState.value.copy(
      coachData = com.trainerloop.domain.coach.CoachSessionData.fromJson(coachJson)
    )
    createFitFile()
    if (RampTest.isRampTest(workoutId)) {
      _uiState.value = _uiState.value.copy(
        isRampTest = true,
        rampTestNewFtp = RampTest.computeFtp(samples),
        rampTestPreviousFtp = profileRepository.getProfileSync().ftp
      )
    }
  }

  fun acceptFtp() {
    val newFtp = _uiState.value.rampTestNewFtp ?: return
    viewModelScope.launch {
      profileRepository.updateFtp(newFtp)
      val profile = profileRepository.getProfileSync()
      val icuConfigured = profile.intervalsIcuAthleteId.isNotBlank() && profile.intervalsIcuApiKey.isNotBlank()
      _uiState.value = _uiState.value.copy(
        ftpDecided = true,
        ftpAccepted = true,
        showIcuFtpPrompt = icuConfigured
      )
    }
  }

  fun discardFtp() {
    _uiState.value = _uiState.value.copy(ftpDecided = true, ftpAccepted = false)
  }

  fun declineIcuFtpPush() {
    _uiState.value = _uiState.value.copy(showIcuFtpPrompt = false)
  }

  fun pushFtpToIcu() {
    val newFtp = _uiState.value.rampTestNewFtp ?: return
    val profile = profileRepository.getProfileSync()
    _uiState.value = _uiState.value.copy(showIcuFtpPrompt = false, ftpPushStatus = "Updating FTP on intervals.icu…")
    viewModelScope.launch {
      val status = try {
        val ok = IntervalsIcuClient(profile.intervalsIcuApiKey)
          .updateFtp(profile.intervalsIcuAthleteId, newFtp)
        if (ok) "FTP updated on intervals.icu" else "FTP push failed — set it manually on intervals.icu"
      } catch (e: Exception) {
        "FTP push failed: ${e.message}"
      }
      _uiState.value = _uiState.value.copy(ftpPushStatus = status)
    }
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
    val distanceKm = WorkoutSummaryMath.totalDistanceKm(samples)
    val ascentM = WorkoutSummaryMath.totalAscentM(samples)

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
      totalWorkKj = totalWork,
      distanceKm = distanceKm,
      ascentM = ascentM
    )
  }

  fun onSave() {
    val state = _uiState.value
    if (state.isSaved || state.isSaving || state.isDiscarded || samples.isEmpty()) return

    _uiState.value = state.copy(isSaving = true)

    val samplesJson = Json.encodeToString(ListSerializer(TelemetrySample.serializer()), samples)
    val sessionData = SessionData(
      id = sessionId,
      workoutId = workoutId,
      workoutName = workoutName,
      startedAt = Instant.ofEpochMilli(startTimeMs).toString(),
      endedAt = Instant.now().toString(),
      durationSec = state.durationSec,
      samplesJson = samplesJson,
      coachEventsJson = coachJson,
      completed = completed,
      avgPower = state.avgPower,
      maxPower = state.maxPower,
      avgCadence = state.avgCadence,
      avgHr = state.avgHr,
      sessionType = sessionType,
      routeId = routeId
    )

    viewModelScope.launch {
      try {
        sessionRepository.save(sessionData)
        _uiState.value = _uiState.value.copy(isSaved = true, isSaving = false)
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(
          isSaving = false,
          error = "Failed to save session: ${e.message}"
        )
        return@launch
      }
      uploadToIntervalsIcu(sessionData)
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
    } catch (e: Exception) {
      _uiState.value = _uiState.value.copy(error = "Failed to create FIT: ${e.message}")
    }
  }

  private suspend fun uploadToIntervalsIcu(session: SessionData) {
    val profile = profileRepository.getProfileSync()
    val athleteId = profile.intervalsIcuAthleteId
    val apiKey = profile.intervalsIcuApiKey
    if (athleteId.isBlank() || apiKey.isBlank()) return

    _uiState.value = _uiState.value.copy(uploadStatus = "Uploading…")
    val uploader = IcuActivityUploader(
      sessionRepository = sessionRepository,
      upload = { bytes, name -> IntervalsIcuClient(apiKey).uploadActivity(athleteId, bytes, name) }
    )
    val ok = uploader.uploadSession(session)
    _uiState.value = _uiState.value.copy(
      uploadStatus = if (ok) "Uploaded to intervals.icu" else "Upload failed — retry from History"
    )
  }

  fun onShare() {
    val file = _uiState.value.fitFile ?: return
    FitShareHelper.shareFitFile(getApplication(), file)
  }

  fun onDiscard() {
    viewModelScope.launch {
      try {
        if (_uiState.value.isSaved) sessionRepository.deleteById(sessionId)
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
