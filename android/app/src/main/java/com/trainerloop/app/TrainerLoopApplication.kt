package com.trainerloop.app

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Context
import com.trainerloop.ble.BleConnection
import com.trainerloop.ble.FtmsControlManager
import com.trainerloop.ble.FtmsManager
import com.trainerloop.ble.HrManager
import com.trainerloop.ble.BleLog
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

  /**
   * The single BLE GATT connection for the trainer, shared between
   * [FtmsManager] and [FtmsControlManager]. BLE allows only one GATT
   * client per peripheral — opening a second `connectGatt` either
   * gets refused or steals the link from the first.
   */
  private var trainerConnection: BleConnection? = null

  var selectedWorkout: Workout? = null
  var pendingSessionSamples: List<TelemetrySample>? = null
  var pendingCoachJson: String? = null

  /** Set by FreeRideScreen while active: volume keys shift gears (true = up). */
  var volumeShiftHandler: ((Boolean) -> Unit)? = null
  var pendingSessionType: String? = null
  var pendingRouteId: String? = null

  fun attachTrainer(device: BluetoothDevice) {
    val previousConnection = trainerConnection
    val previousFtms = _ftmsManager.value
    val previousControl = _ftmsControlManager.value

    val conn = BleConnection(this, device)
    trainerConnection = conn
    _ftmsManager.value = FtmsManager(device, conn)
    _ftmsControlManager.value = FtmsControlManager(device, conn)

    appScope.launch {
      previousFtms?.disconnect()
      previousControl?.disconnect()
      previousConnection?.disconnect()
    }
  }

  /**
   * Opens the single shared GATT connection to the trainer and then
   * asks both the data and control managers to subscribe to their
   * respective characteristics. The GATT connect happens exactly once.
   */
  suspend fun connectTrainer(): Result<Unit> {
    val conn = trainerConnection
      ?: return Result.failure(Exception("No trainer attached"))
    val ftms = _ftmsManager.value
      ?: return Result.failure(Exception("FTMS manager not initialised"))
    val ctrl = _ftmsControlManager.value
      ?: return Result.failure(Exception("FTMS control manager not initialised"))

    conn.connect().getOrElse {
      BleLog.e("connectTrainer: gatt connect failed: ${it.message}")
      return Result.failure(it)
    }
    // Discover services exactly once on the shared GATT link, before either
    // manager subscribes. Previously each manager called discoverServices()
    // itself, so the second call aborted in-flight descriptor writes from the
    // first (Android allows only one outstanding GATT op at a time).
    conn.discoverServices().getOrElse {
      BleLog.e("connectTrainer: discoverServices failed: ${it.message}")
      return Result.failure(it)
    }
    ftms.connect().getOrElse {
      BleLog.e("connectTrainer: FtmsManager subscribe failed: ${it.message}")
      return Result.failure(it)
    }
    ctrl.connect().getOrElse {
      BleLog.e("connectTrainer: FtmsControlManager subscribe failed: ${it.message}")
      return Result.failure(it)
    }
    return Result.success(Unit)
  }

  suspend fun disconnectTrainer() {
    val previousFtms = _ftmsManager.value
    val previousControl = _ftmsControlManager.value
    val previousConn = trainerConnection
    _ftmsManager.value = null
    _ftmsControlManager.value = null
    trainerConnection = null
    previousFtms?.disconnect()
    previousControl?.disconnect()
    previousConn?.disconnect()
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
    val previousConn = trainerConnection
    _ftmsManager.value = null
    _ftmsControlManager.value = null
    trainerConnection = null
    appScope.launch {
      previousFtms?.disconnect()
      previousControl?.disconnect()
      previousConn?.disconnect()
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
    val previousConn = trainerConnection
    _ftmsManager.value = null
    _ftmsControlManager.value = null
    _hrManager.value = null
    trainerConnection = null
    appScope.launch {
      previousFtms?.disconnect()
      previousControl?.disconnect()
      previousHr?.disconnect()
      previousConn?.disconnect()
    }
  }

  override fun onTerminate() {
    appScope.cancel()
    super.onTerminate()
  }
}

val Context.trainerLoopApp: TrainerLoopApplication
  get() = applicationContext as TrainerLoopApplication
