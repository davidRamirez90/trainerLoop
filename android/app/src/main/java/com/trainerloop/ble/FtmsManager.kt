package com.trainerloop.ble

import android.bluetooth.BluetoothDevice
import com.trainerloop.ble.model.IndoorBikeData
import com.trainerloop.ble.model.IndoorBikeDataParser
import com.trainerloop.ble.model.Stamped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

/**
 * Subscribes to FTMS Indoor Bike Data notifications on a **shared**
 * [BleConnection]. Do not pass a different connection to a separate
 * [FtmsControlManager] — the BLE peripheral (trainer) only allows one
 * active GATT client, and a second `connectGatt` will either be refused
 * or steal the link from the first one. The [TrainerLoopApplication]
 * owns a single [BleConnection] per trainer and hands it to both
 * this manager and the control manager.
 */
class FtmsManager(
  val device: BluetoothDevice,
  private val connection: BleConnection
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  private val _data = MutableStateFlow<Stamped<IndoorBikeData>?>(null)
  val data: StateFlow<Stamped<IndoorBikeData>?> = _data.asStateFlow()

  private val _batteryLevel = MutableStateFlow<Int?>(null)
  val batteryLevel: StateFlow<Int?> = _batteryLevel.asStateFlow()

  private val _manufacturer = MutableStateFlow<String?>(null)
  val manufacturer: StateFlow<String?> = _manufacturer.asStateFlow()

  private val _model = MutableStateFlow<String?>(null)
  val model: StateFlow<String?> = _model.asStateFlow()

  private val _isConnected = MutableStateFlow(false)
  val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

  /**
   * Subscribes to the FTMS Indoor Bike Data notifications on the shared
   * [connection]. The caller is responsible for [BleConnection.connect]
   * and [BleConnection.discoverServices] having been called and returned
   * successfully before invoking this.
   */
  // NOTE: service discovery is performed exactly once by
  // TrainerLoopApplication.connectTrainer() before either manager is
  // asked to subscribe. Calling discoverServices() here again raced with
  // the control manager's subscription and aborted in-flight descriptor
  // writes on the shared GATT link.
  suspend fun connect(): Result<Unit> {
    BleLog.d("FtmsManager.connect device=${device.address}")
    readDeviceInfo(connection)
    val subscribed = subscribeToNotifications(connection)
    if (!subscribed) {
      return Result.failure(Exception("FTMS Indoor Bike Data subscription failed"))
    }

    // Auto-resubscribe on reconnect (the GATT link is shared, but
    // characteristic subscriptions need to be re-armed after a drop).
    // Service discovery is re-run once by BleConnection before handlers fire.
    connection.addReconnectHandler {
      readDeviceInfo(connection)
      subscribeToNotifications(connection)
    }

    _isConnected.value = true
    BleLog.d("FtmsManager.connect success")
    return Result.success(Unit)
  }

  private fun readDeviceInfo(conn: BleConnection) {
    scope.launch {
      _manufacturer.value = conn.read(BleConstants.DEVICE_INFO_SERVICE, BleConstants.MANUFACTURER_NAME) {
        it.decodeToString().trimEnd('\u0000')
      }
    }
    scope.launch {
      _model.value = conn.read(BleConstants.DEVICE_INFO_SERVICE, BleConstants.MODEL_NUMBER) {
        it.decodeToString().trimEnd('\u0000')
      }
    }
    scope.launch {
      _batteryLevel.value = conn.read(BleConstants.BATTERY_SERVICE, BleConstants.BATTERY_LEVEL) {
        if (it.isEmpty()) null else it[0].toInt() and 0xFF
      }
    }
  }

  private suspend fun subscribeToNotifications(conn: BleConnection): Boolean {
    val dataChar = conn.getCharacteristic(BleConstants.FTMS_SERVICE, BleConstants.INDOOR_BIKE_DATA)
      ?: run {
        BleLog.e(
          "FTMS IndoorBikeData characteristic NOT FOUND " +
            "service=${BleConstants.FTMS_SERVICE} char=${BleConstants.INDOOR_BIKE_DATA}"
        )
        return false
      }
    BleLog.d("FTMS IndoorBikeData characteristic found, enabling notifications")
    val notificationFlow = try {
      conn.enableNotifications(dataChar)
    } catch (t: Throwable) {
      BleLog.e("FTMS enableNotifications failed", t)
      return false
    }
    scope.launch {
      try {
        BleLog.d("FTMS notifications enabled, starting collect")
        notificationFlow.collect { bytes ->
          val parsed = IndoorBikeDataParser.parse(bytes)
          if (parsed != null) {
            _data.value = Stamped(parsed, android.os.SystemClock.elapsedRealtime())
          } else {
            BleLog.w("FTMS parse returned null, dropping ${bytes.size} bytes")
          }
        }
      } catch (t: Throwable) {
        BleLog.e("FTMS notification collector crashed", t)
      }
    }
    return true
  }

  suspend fun disconnect() {
    BleLog.d("FtmsManager.disconnect device=${device.address}")
    // Do NOT close the shared BleConnection here — the application
    // owns it and will close it when both managers have been released.
    _isConnected.value = false
    _data.value = null
    scope.cancel()
  }
}
