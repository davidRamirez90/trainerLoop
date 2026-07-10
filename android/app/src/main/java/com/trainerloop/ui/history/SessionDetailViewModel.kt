package com.trainerloop.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trainerloop.app.TrainerLoopApplication
import com.trainerloop.data.model.SessionData
import com.trainerloop.data.repository.IcuActivityUploader
import com.trainerloop.data.repository.ProfileRepository
import com.trainerloop.data.repository.SessionRepository
import com.trainerloop.data.source.local.AppDatabase
import com.trainerloop.data.source.remote.IntervalsIcuClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SessionDetailUiState(
  val session: SessionData? = null,
  val icuConfigured: Boolean = false,
  val uploadStatus: String? = null,
  val isUploading: Boolean = false
)

class SessionDetailViewModel(
  application: Application,
  private val sessionId: String,
  private val sessionRepository: SessionRepository =
    SessionRepository.create(AppDatabase.getInstance(application)),
  private val profileRepository: ProfileRepository =
    (application as? TrainerLoopApplication)?.profileRepository ?: ProfileRepository(application)
) : AndroidViewModel(application) {

  private val _uiState = MutableStateFlow(SessionDetailUiState())
  val uiState: StateFlow<SessionDetailUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      val profile = profileRepository.getProfileSync()
      _uiState.value = _uiState.value.copy(
        session = sessionRepository.getById(sessionId),
        icuConfigured = profile.intervalsIcuAthleteId.isNotBlank() &&
          profile.intervalsIcuApiKey.isNotBlank()
      )
    }
  }

  fun uploadToIcu() {
    val session = _uiState.value.session ?: return
    if (_uiState.value.isUploading) return
    val profile = profileRepository.getProfileSync()
    if (profile.intervalsIcuAthleteId.isBlank() || profile.intervalsIcuApiKey.isBlank()) return

    _uiState.value = _uiState.value.copy(isUploading = true, uploadStatus = "Uploading…")
    viewModelScope.launch {
      val uploader = IcuActivityUploader(
        sessionRepository = sessionRepository,
        upload = { bytes, name ->
          IntervalsIcuClient(profile.intervalsIcuApiKey)
            .uploadActivity(profile.intervalsIcuAthleteId, bytes, name)
        }
      )
      val ok = uploader.uploadSession(session)
      _uiState.value = _uiState.value.copy(
        isUploading = false,
        uploadStatus = if (ok) "Uploaded to intervals.icu" else "Upload failed — check connection and try again",
        // Reload so icuSyncedAt reflects the new state.
        session = sessionRepository.getById(sessionId)
      )
    }
  }
}
