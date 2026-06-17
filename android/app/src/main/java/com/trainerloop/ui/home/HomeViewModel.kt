package com.trainerloop.ui.home

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trainerloop.app.ManagerProvider
import com.trainerloop.app.trainerLoopApp
import com.trainerloop.ble.FtmsManager
import com.trainerloop.ble.HrManager
import com.trainerloop.data.model.SessionSummary
import com.trainerloop.data.repository.ProfileRepository
import com.trainerloop.data.repository.SessionRepository
import com.trainerloop.data.source.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
  val riderName: String = "Rider",
  val ftp: Int = 250,
  val weightKg: Double = 75.0,
  val connectedTrainer: BluetoothDevice? = null,
  val isTrainerConnected: Boolean = false,
  val trainerBattery: Int? = null,
  val trainerModel: String? = null,
  val connectedHr: BluetoothDevice? = null,
  val isHrConnected: Boolean = false,
  val latestHrBpm: Int? = null,
  val recentSession: SessionSummary? = null
)

class HomeViewModel(
  application: Application,
  private val profileRepository: ProfileRepository,
  private val sessionRepository: SessionRepository,
  private val managerProvider: ManagerProvider,
  private val coroutineScope: CoroutineScope? = null
) : AndroidViewModel(application) {

  constructor(application: Application) : this(
    application,
    ProfileRepository(application),
    SessionRepository.create(AppDatabase.getInstance(application)),
    application.trainerLoopApp,
    null
  )

  private val scope: CoroutineScope
    get() = coroutineScope ?: viewModelScope

  private val _uiState = MutableStateFlow(HomeUiState())
  val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

  private var ftmsJob: Job? = null
  private var hrJob: Job? = null

  init {
    scope.launch {
      profileRepository.profile.collect { profile ->
        _uiState.value = _uiState.value.copy(
          riderName = profile.name,
          ftp = profile.ftp,
          weightKg = profile.weightKg
        )
      }
    }

    scope.launch {
      sessionRepository.summaries().collect { sessions ->
        _uiState.value = _uiState.value.copy(
          recentSession = sessions.maxByOrNull { it.startedAt }
        )
      }
    }

    scope.launch {
      managerProvider.ftmsManager.collect { manager ->
        bindTrainer(manager)
      }
    }

    scope.launch {
      managerProvider.hrManager.collect { manager ->
        bindHr(manager)
      }
    }
  }

  private fun bindTrainer(manager: FtmsManager?) {
    ftmsJob?.cancel()
    ftmsJob = null

    _uiState.value = _uiState.value.copy(
      connectedTrainer = manager?.device,
      isTrainerConnected = false,
      trainerBattery = null,
      trainerModel = null
    )

    manager ?: return

    ftmsJob = scope.launch {
      launch {
        manager.isConnected.collect { connected ->
          _uiState.value = _uiState.value.copy(isTrainerConnected = connected)
        }
      }
      launch {
        manager.batteryLevel.collect { battery ->
          _uiState.value = _uiState.value.copy(trainerBattery = battery)
        }
      }
      launch {
        manager.model.collect { model ->
          _uiState.value = _uiState.value.copy(trainerModel = model)
        }
      }
    }
  }

  private fun bindHr(manager: HrManager?) {
    hrJob?.cancel()
    hrJob = null

    _uiState.value = _uiState.value.copy(
      connectedHr = manager?.device,
      isHrConnected = false,
      latestHrBpm = null
    )

    manager ?: return

    hrJob = scope.launch {
      launch {
        manager.isConnected.collect { connected ->
          _uiState.value = _uiState.value.copy(isHrConnected = connected)
        }
      }
      launch {
        manager.heartRate.collect { bpm ->
          _uiState.value = _uiState.value.copy(latestHrBpm = bpm)
        }
      }
    }
  }
}
