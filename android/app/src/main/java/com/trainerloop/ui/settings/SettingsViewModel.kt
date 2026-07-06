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
  val name: String = "Rider",
  val ftp: String = "250",
  val weightKg: String = "75.0",
  val maxHr: String = "190",
  val restingHr: String = "55",
  /** Blank = unset; the coach then estimates LTHR from max HR. */
  val lthr: String = "",
  val ergBias: String = "0",
  val selectedCoach: String = "default",
  val intervalsAthleteId: String = "",
  val intervalsApiKey: String = "",
  val isSaved: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
  private val repository = ProfileRepository(application)

  private val _uiState = MutableStateFlow(SettingsUiState())
  val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

  init {
    val profile = repository.getProfileSync()
    _uiState.value = SettingsUiState(
      name = profile.name,
      ftp = profile.ftp.toString(),
      weightKg = profile.weightKg.toString(),
      maxHr = profile.maxHr.toString(),
      restingHr = profile.restingHr.toString(),
      lthr = profile.lthr?.toString() ?: "",
      ergBias = profile.ergBiasPct.toString(),
      selectedCoach = profile.selectedCoachProfileId,
      intervalsAthleteId = profile.intervalsIcuAthleteId,
      intervalsApiKey = profile.intervalsIcuApiKey
    )
  }

  fun updateName(value: String) {
    _uiState.value = _uiState.value.copy(name = value, isSaved = false)
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

  fun updateRestingHr(value: String) {
    _uiState.value = _uiState.value.copy(restingHr = value, isSaved = false)
  }

  fun updateLthr(value: String) {
    _uiState.value = _uiState.value.copy(lthr = value, isSaved = false)
  }

  fun updateErgBias(value: String) {
    _uiState.value = _uiState.value.copy(ergBias = value, isSaved = false)
  }

  fun updateSelectedCoach(value: String) {
    _uiState.value = _uiState.value.copy(selectedCoach = value, isSaved = false)
  }

  fun updateIntervalsAthleteId(value: String) {
    _uiState.value = _uiState.value.copy(intervalsAthleteId = value, isSaved = false)
  }

  fun updateIntervalsApiKey(value: String) {
    _uiState.value = _uiState.value.copy(intervalsApiKey = value, isSaved = false)
  }

  fun save() {
    viewModelScope.launch {
      val state = _uiState.value
      repository.updateProfile {
        it.copy(
          name = state.name.trim().takeIf { it.isNotBlank() } ?: it.name,
          ftp = state.ftp.toIntOrNull() ?: it.ftp,
          weightKg = state.weightKg.toDoubleOrNull() ?: it.weightKg,
          maxHr = state.maxHr.toIntOrNull() ?: it.maxHr,
          restingHr = state.restingHr.toIntOrNull() ?: it.restingHr,
          lthr = state.lthr.toIntOrNull()?.takeIf { v -> v > 0 },
          ergBiasPct = state.ergBias.toIntOrNull() ?: it.ergBiasPct,
          selectedCoachProfileId = state.selectedCoach,
          intervalsIcuAthleteId = state.intervalsAthleteId.trim(),
          intervalsIcuApiKey = state.intervalsApiKey.trim()
        )
      }
      _uiState.value = _uiState.value.copy(isSaved = true)
    }
  }
}
