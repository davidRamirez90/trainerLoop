package com.trainerloop.ui.connect

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trainerloop.ble.BleConstants
import com.trainerloop.ble.BlePermissions
import com.trainerloop.ble.BleScanner
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
  val hasPermissions: Boolean = false,
  val isBluetoothOn: Boolean = false,
  val isLocationOn: Boolean = false,
  val error: String? = null
)

class ConnectViewModel(application: Application) : AndroidViewModel(application) {

  private val app = application
  private val scanner = BleScanner(app)

  private val _uiState = MutableStateFlow(ConnectUiState())
  val uiState: StateFlow<ConnectUiState> = _uiState.asStateFlow()

  private var scanJob: Job? = null

  fun refreshStatus() {
    _uiState.value = _uiState.value.copy(
      hasPermissions = BlePermissions.hasPermissions(app),
      isLocationOn = BlePermissions.isLocationEnabled(app),
      isBluetoothOn = scanner.isBluetoothEnabled()
    )
  }

  fun startScan() {
    refreshStatus()
    val state = _uiState.value

    if (!state.hasPermissions) {
      _uiState.value = state.copy(error = "Location permission required. Please grant in Settings → Apps → Trainer Loop → Permissions.")
      return
    }
    if (!state.isBluetoothOn) {
      _uiState.value = state.copy(error = "Bluetooth is off. Turn it on in Settings.")
      return
    }
    if (!state.isLocationOn) {
      _uiState.value = state.copy(error = "Location services are off. Enable in Settings → Location.")
      return
    }

    scanJob?.cancel()
    _uiState.value = _uiState.value.copy(isScanning = true, error = null)

    val flow = scanner.startScan(
      services = listOf(BleConstants.FTMS_SERVICE, BleConstants.HEART_RATE_SERVICE),
      durationMs = 10_000L
    )

    if (flow == null) {
      _uiState.value = _uiState.value.copy(
        isScanning = false,
        error = "Could not start BLE scan. Try restarting Bluetooth."
      )
      return
    }

    scanJob = viewModelScope.launch {
      try {
        flow.collect { devices ->
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
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(
          isScanning = false,
          error = "Scan failed: ${e.message}"
        )
      }
    }

    // Timeout fallback
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
    _uiState.value = _uiState.value.copy(connectedTrainer = device, error = null)
  }

  fun connectHr(device: BleDevice) {
    _uiState.value = _uiState.value.copy(connectedHr = device, error = null)
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
