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
import kotlinx.coroutines.launch

class TrainerLoopApplication : Application() {

  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  var ftmsManager: FtmsManager? = null
    private set
  var hrManager: HrManager? = null
    private set
  var ftmsControlManager: FtmsControlManager? = null
    private set

  var selectedWorkout: Workout? = null
  var pendingSessionSamples: List<TelemetrySample>? = null

  fun attachTrainer(device: BluetoothDevice) {
    val previousFtms = ftmsManager
    val previousControl = ftmsControlManager
    ftmsManager = FtmsManager(this, device)
    ftmsControlManager = FtmsControlManager(this, device)
    appScope.launch {
      previousFtms?.disconnect()
      previousControl?.disconnect()
    }
  }

  fun attachHr(device: BluetoothDevice) {
    val previousHr = hrManager
    hrManager = HrManager(this, device)
    appScope.launch {
      previousHr?.disconnect()
    }
  }

  fun clearDevices() {
    val previousFtms = ftmsManager
    val previousControl = ftmsControlManager
    val previousHr = hrManager
    ftmsManager = null
    ftmsControlManager = null
    hrManager = null
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
