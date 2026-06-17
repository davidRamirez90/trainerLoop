package com.trainerloop.app

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Context
import com.trainerloop.ble.FtmsControlManager
import com.trainerloop.ble.FtmsManager
import com.trainerloop.ble.HrManager
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.data.model.Workout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TrainerLoopApplication : Application(), ManagerProvider {

  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  private val _ftmsManager = MutableStateFlow<FtmsManager?>(null)
  override val ftmsManager: StateFlow<FtmsManager?> = _ftmsManager.asStateFlow()

  private val _hrManager = MutableStateFlow<HrManager?>(null)
  override val hrManager: StateFlow<HrManager?> = _hrManager.asStateFlow()

  private val _ftmsControlManager = MutableStateFlow<FtmsControlManager?>(null)
  val ftmsControlManager: StateFlow<FtmsControlManager?> = _ftmsControlManager.asStateFlow()

  var selectedWorkout: Workout? = null
  var pendingSessionSamples: List<TelemetrySample>? = null

  fun attachTrainer(device: BluetoothDevice) {
    val previousFtms = _ftmsManager.value
    val previousControl = _ftmsControlManager.value
    _ftmsManager.value = FtmsManager(this, device)
    _ftmsControlManager.value = FtmsControlManager(this, device)
    appScope.launch {
      previousFtms?.disconnect()
      previousControl?.disconnect()
    }
  }

  fun attachHr(device: BluetoothDevice) {
    val previousHr = _hrManager.value
    _hrManager.value = HrManager(this, device)
    appScope.launch {
      previousHr?.disconnect()
    }
  }

  fun clearTrainer() {
    val previousFtms = _ftmsManager.value
    val previousControl = _ftmsControlManager.value
    _ftmsManager.value = null
    _ftmsControlManager.value = null
    appScope.launch {
      previousFtms?.disconnect()
      previousControl?.disconnect()
    }
  }

  fun clearHr() {
    val previousHr = _hrManager.value
    _hrManager.value = null
    appScope.launch {
      previousHr?.disconnect()
    }
  }

  fun clearDevices() {
    val previousFtms = _ftmsManager.value
    val previousControl = _ftmsControlManager.value
    val previousHr = _hrManager.value
    _ftmsManager.value = null
    _ftmsControlManager.value = null
    _hrManager.value = null
    appScope.launch {
      previousFtms?.disconnect()
      previousControl?.disconnect()
      previousHr?.disconnect()
    }
  }

  override fun onTerminate() {
    appScope.cancel()
    super.onTerminate()
  }
}

val Context.trainerLoopApp: TrainerLoopApplication
  get() = applicationContext as TrainerLoopApplication
