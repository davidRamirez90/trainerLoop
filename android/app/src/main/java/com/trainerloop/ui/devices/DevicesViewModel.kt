package com.trainerloop.ui.devices

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trainerloop.app.trainerLoopApp
import com.trainerloop.ble.BleConstants
import com.trainerloop.ble.BlePermissions
import com.trainerloop.ble.BleScanner
import com.trainerloop.ble.model.BleDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DevicesUiState(
  val isScanning: Boolean = false,
  val trainerDevices: List<BleDevice> = emptyList(),
  val hrDevices: List<BleDevice> = emptyList(),
  val connectedTrainer: BleDevice? = null,
  val connectedHr: BleDevice? = null,
  val pendingTrainerAddress: String? = null,
  val pendingHrAddress: String? = null,
  val isConnectingTrainer: Boolean = false,
  val isConnectingHr: Boolean = false,
  val trainerBattery: Int? = null,
  val latestHrBpm: Int? = null,
  val hasPermissions: Boolean = false,
  val isBluetoothOn: Boolean = false,
  val isLocationOn: Boolean = false,
  val error: String? = null
)

class DevicesViewModel(application: Application) : AndroidViewModel(application) {

  private val appContext = application.applicationContext
  private val scanner = BleScanner(appContext)

  private val _uiState = MutableStateFlow(DevicesUiState())
  val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

  private var scanJob: Job? = null
  private var scanTimeoutJob: Job? = null
  private var trainerConnectionJob: Job? = null
  private var hrConnectionJob: Job? = null
  private var trainerCollectorJob: Job? = null
  private var hrCollectorJob: Job? = null

  init {
    refreshStatus()
    restoreConnectedDevices()
    if (_uiState.value.trainerDevices.isEmpty() && _uiState.value.hrDevices.isEmpty() && !_uiState.value.isScanning) {
      startScan()
    }
  }

  fun refreshStatus() {
    _uiState.value = _uiState.value.copy(
      hasPermissions = BlePermissions.hasPermissions(appContext),
      isLocationOn = BlePermissions.isLocationEnabled(appContext),
      isBluetoothOn = scanner.isBluetoothEnabled()
    )
  }

  fun startScan() {
    scanTimeoutJob?.cancel()
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
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(
          isScanning = false,
          error = "Scan failed: ${e.message}"
        )
      }
    }

    scanTimeoutJob = viewModelScope.launch {
      delay(11_000L)
      if (_uiState.value.isScanning) {
        _uiState.value = _uiState.value.copy(isScanning = false)
      }
    }
  }

  fun stopScan() {
    scanJob?.cancel()
    scanTimeoutJob?.cancel()
    scanner.stopScan()
    _uiState.value = _uiState.value.copy(isScanning = false)
  }

  fun connectTrainer(device: BleDevice) {
    val app = appContext.trainerLoopApp
    _uiState.value = _uiState.value.copy(
      isConnectingTrainer = true,
      pendingTrainerAddress = device.address,
      error = null
    )

    val btDevice = resolveBluetoothDevice(appContext, device.address)
    if (btDevice == null) {
      _uiState.value = _uiState.value.copy(
        isConnectingTrainer = false,
        pendingTrainerAddress = null,
        error = "Could not resolve Bluetooth device ${device.address}."
      )
      return
    }

    trainerCollectorJob?.cancel()
    trainerConnectionJob?.cancel()
    trainerConnectionJob = viewModelScope.launch {
      var success = false
      var capturedFtms: com.trainerloop.ble.FtmsManager? = null
      try {
        app.attachTrainer(btDevice)
        val ftmsManager = app.ftmsManager.value ?: run {
          _uiState.value = _uiState.value.copy(
            connectedTrainer = null,
            isConnectingTrainer = false,
            pendingTrainerAddress = null,
            error = "Trainer connection failed: manager not created"
          )
          return@launch
        }
        capturedFtms = ftmsManager

        val ftmsResult = try {
          ftmsManager.connect()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Result.failure(e)
        }
        if (!ftmsResult.isSuccess) {
          _uiState.value = _uiState.value.copy(
            connectedTrainer = null,
            isConnectingTrainer = false,
            pendingTrainerAddress = null,
            error = "Trainer connection failed: ${ftmsResult.exceptionOrNull()?.message ?: "unknown"}"
          )
          return@launch
        }

        val controlResult = try {
          app.ftmsControlManager.value?.connect()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Result.failure(e)
        }
        if (controlResult?.isSuccess != true) {
          _uiState.value = _uiState.value.copy(
            connectedTrainer = null,
            isConnectingTrainer = false,
            pendingTrainerAddress = null,
            error = "Trainer control point failed: ${controlResult?.exceptionOrNull()?.message ?: "unknown"}"
          )
          return@launch
        }

        _uiState.value = _uiState.value.copy(
          connectedTrainer = device,
          isConnectingTrainer = false,
          pendingTrainerAddress = null
        )
        collectTrainerState()
        success = true
      } finally {
        if (!success && app.ftmsManager.value == capturedFtms) {
          app.clearTrainer()
        }
      }
    }
  }

  fun connectHr(device: BleDevice) {
    val app = appContext.trainerLoopApp
    _uiState.value = _uiState.value.copy(
      isConnectingHr = true,
      pendingHrAddress = device.address,
      error = null
    )

    val btDevice = resolveBluetoothDevice(appContext, device.address)
    if (btDevice == null) {
      _uiState.value = _uiState.value.copy(
        isConnectingHr = false,
        pendingHrAddress = null,
        error = "Could not resolve Bluetooth device ${device.address}."
      )
      return
    }

    hrCollectorJob?.cancel()
    hrConnectionJob?.cancel()
    hrConnectionJob = viewModelScope.launch {
      var success = false
      var capturedHr: com.trainerloop.ble.HrManager? = null
      try {
        app.attachHr(btDevice)
        val hrManager = app.hrManager.value ?: run {
          _uiState.value = _uiState.value.copy(
            connectedHr = null,
            isConnectingHr = false,
            pendingHrAddress = null,
            error = "HR connection failed: manager not created"
          )
          return@launch
        }
        capturedHr = hrManager

        val result = try {
          hrManager.connect()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Result.failure(e)
        }
        if (result.isSuccess) {
          _uiState.value = _uiState.value.copy(
            connectedHr = device,
            isConnectingHr = false,
            pendingHrAddress = null
          )
          collectHrState()
          success = true
        } else {
          _uiState.value = _uiState.value.copy(
            connectedHr = null,
            isConnectingHr = false,
            pendingHrAddress = null,
            error = "HR connection failed: ${result.exceptionOrNull()?.message ?: "unknown"}"
          )
        }
      } finally {
        if (!success && app.hrManager.value == capturedHr) {
          app.clearHr()
        }
      }
    }
  }

  fun disconnectTrainer() {
    trainerConnectionJob?.cancel()
    trainerCollectorJob?.cancel()
    val app = appContext.trainerLoopApp
    viewModelScope.launch {
      app.clearTrainer()
    }
    _uiState.value = _uiState.value.copy(
      connectedTrainer = null,
      trainerBattery = null,
      pendingTrainerAddress = null,
      error = null
    )
  }

  fun disconnectHr() {
    hrConnectionJob?.cancel()
    hrCollectorJob?.cancel()
    val app = appContext.trainerLoopApp
    viewModelScope.launch {
      app.clearHr()
    }
    _uiState.value = _uiState.value.copy(
      connectedHr = null,
      latestHrBpm = null,
      pendingHrAddress = null,
      error = null
    )
  }

  fun clearError() {
    _uiState.value = _uiState.value.copy(error = null)
  }

  private fun restoreConnectedDevices() {
    val app = appContext.trainerLoopApp
    val trainer = app.ftmsManager.value?.takeIf { it.isConnected.value }?.device?.toBleDevice()
    val hr = app.hrManager.value?.takeIf { it.isConnected.value }?.device?.toBleDevice()
    _uiState.value = _uiState.value.copy(
      connectedTrainer = trainer,
      connectedHr = hr
    )
    if (trainer != null) collectTrainerState()
    if (hr != null) collectHrState()
  }

  private fun collectTrainerState() {
    trainerCollectorJob?.cancel()
    val app = appContext.trainerLoopApp
    val manager = app.ftmsManager.value ?: return
    trainerCollectorJob = viewModelScope.launch {
      launch {
        manager.batteryLevel.collect { battery ->
          _uiState.value = _uiState.value.copy(trainerBattery = battery)
        }
      }
      launch {
        manager.isConnected.collect { connected ->
          if (connected) {
            _uiState.value = _uiState.value.copy(connectedTrainer = manager.device.toBleDevice())
          } else if (_uiState.value.connectedTrainer != null) {
            _uiState.value = _uiState.value.copy(connectedTrainer = null)
          }
        }
      }
    }
  }

  private fun collectHrState() {
    hrCollectorJob?.cancel()
    val app = appContext.trainerLoopApp
    val manager = app.hrManager.value ?: return
    hrCollectorJob = viewModelScope.launch {
      launch {
        manager.heartRate.collect { bpm ->
          _uiState.value = _uiState.value.copy(latestHrBpm = bpm)
        }
      }
      launch {
        manager.isConnected.collect { connected ->
          if (connected) {
            _uiState.value = _uiState.value.copy(connectedHr = manager.device.toBleDevice())
          } else if (_uiState.value.connectedHr != null) {
            _uiState.value = _uiState.value.copy(connectedHr = null)
          }
        }
      }
    }
  }

  override fun onCleared() {
    scanJob?.cancel()
    scanTimeoutJob?.cancel()
    trainerConnectionJob?.cancel()
    hrConnectionJob?.cancel()
    trainerCollectorJob?.cancel()
    hrCollectorJob?.cancel()
    scanner.stopScan()
    super.onCleared()
  }
}

private fun resolveBluetoothDevice(context: Context, address: String): BluetoothDevice? {
  val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
  return try {
    bluetoothManager?.adapter?.getRemoteDevice(address)
  } catch (_: IllegalArgumentException) {
    null
  }
}

private fun BluetoothDevice.toBleDevice(): BleDevice {
  return BleDevice(
    address = address,
    name = name,
    services = emptyList(),
    rssi = 0
  )
}
