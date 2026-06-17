package com.trainerloop.ui.connect

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trainerloop.ble.BleScanner
import com.trainerloop.ble.BleConstants
import com.trainerloop.ble.model.BleDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ConnectUiState(
  val isScanning: Boolean = false,
  val trainerDevices: List<BleDevice> = emptyList(),
  val hrDevices: List<BleDevice> = emptyList(),
  val connectedTrainer: BleDevice? = null,
  val connectedHr: BleDevice? = null,
  val error: String? = null
)

class ConnectViewModel(application: Application) : AndroidViewModel(application) {

  private val scanner = BleScanner(application)

  private val _uiState = MutableStateFlow(ConnectUiState())
  val uiState: StateFlow<ConnectUiState> = _uiState.asStateFlow()

  private var scanJob: Job? = null

  fun startScan() {
    scanJob?.cancel()
    _uiState.value = _uiState.value.copy(isScanning = true, error = null)

    scanJob = viewModelScope.launch {
      scanner.scan(
        services = listOf(BleConstants.FTMS_SERVICE, BleConstants.HEART_RATE_SERVICE),
        durationMs = 10_000L
      ).collect { devices ->
        val trainers = devices.filter { device ->
          device.services.contains(BleConstants.FTMS_SERVICE)
        }
        val hrSensors = devices.filter { device ->
          device.services.contains(BleConstants.HEART_RATE_SERVICE)
        }
        _uiState.value = _uiState.value.copy(
          trainerDevices = trainers,
          hrDevices = hrSensors,
          isScanning = true
        )
      }
    }

    // Stop scanning after timeout and update state
    viewModelScope.launch {
      kotlinx.coroutines.delay(11_000L)
      _uiState.value = _uiState.value.copy(isScanning = false)
    }
  }

  fun stopScan() {
    scanJob?.cancel()
    scanner.stopScan()
    _uiState.value = _uiState.value.copy(isScanning = false)
  }

  fun connectTrainer(device: BleDevice) {
    // Attaching to a real device happens in the workout player screen.
    // Here we just track which device the user selected.
    _uiState.value = _uiState.value.copy(
      connectedTrainer = device,
      error = null
    )
  }

  fun connectHr(device: BleDevice) {
    _uiState.value = _uiState.value.copy(
      connectedHr = device,
      error = null
    )
  }

  fun disconnectTrainer() {
    _uiState.value = _uiState.value.copy(connectedTrainer = null)
  }

  fun disconnectHr() {
    _uiState.value = _uiState.value.copy(connectedHr = null)
  }

  fun clearError() {
    _uiState.value = _uiState.value.copy(error = null)
  }

  override fun onCleared() {
    scanJob?.cancel()
    scanner.stopScan()
    super.onCleared()
  }
}
