package com.trainerloop.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trainerloop.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
  val ftp: String = "250",
  val weightKg: String = "75.0",
  val maxHr: String = "190",
  val ergBias: String = "0",
  val selectedCoach: String = "default",
  val isSaved: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
  private val repository = ProfileRepository(application)

  private val _uiState = MutableStateFlow(SettingsUiState())
  val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

  init {
    val profile = repository.getProfileSync()
    _uiState.value = SettingsUiState(
      ftp = profile.ftp.toString(),
      weightKg = profile.weightKg.toString(),
      maxHr = profile.maxHr.toString(),
      ergBias = profile.ergBiasPct.toString(),
      selectedCoach = profile.selectedCoachProfileId
    )
  }

  fun updateFtp(value: String) {
    _uiState.value = _uiState.value.copy(ftp = value, isSaved = false)
  }

  fun updateWeight(value: String) {
    _uiState.value = _uiState.value.copy(weightKg = value, isSaved = false)
  }

  fun updateMaxHr(value: String) {
    _uiState.value = _uiState.value.copy(maxHr = value, isSaved = false)
  }

  fun updateErgBias(value: String) {
    _uiState.value = _uiState.value.copy(ergBias = value, isSaved = false)
  }

  fun save() {
    viewModelScope.launch {
      val state = _uiState.value
      repository.updateFtp(state.ftp.toIntOrNull() ?: return@launch)
      repository.updateWeight(state.weightKg.toDoubleOrNull() ?: return@launch)
      repository.updateMaxHr(state.maxHr.toIntOrNull() ?: return@launch)
      repository.updateErgBias(state.ergBias.toIntOrNull() ?: 0)
      _uiState.value = _uiState.value.copy(isSaved = true)
    }
  }
}
