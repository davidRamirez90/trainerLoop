package com.trainerloop.ble

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.trainerloop.ble.model.IndoorBikeData
import com.trainerloop.ble.model.IndoorBikeDataParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FtmsManager(
  private val context: Context,
  private val device: BluetoothDevice
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var connection: BleConnection? = null

  private val _data = MutableStateFlow<IndoorBikeData?>(null)
  val data: StateFlow<IndoorBikeData?> = _data.asStateFlow()

  private val _batteryLevel = MutableStateFlow<Int?>(null)
  val batteryLevel: StateFlow<Int?> = _batteryLevel.asStateFlow()

  private val _manufacturer = MutableStateFlow<String?>(null)
  val manufacturer: StateFlow<String?> = _manufacturer.asStateFlow()

  private val _model = MutableStateFlow<String?>(null)
  val model: StateFlow<String?> = _model.asStateFlow()

  private val _isConnected = MutableStateFlow(false)
  val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

  suspend fun connect(): Result<Unit> {
    val conn = BleConnection(context, device)
    connection = conn

    conn.connect().getOrElse { return Result.failure(it) }
    conn.discoverServices().getOrElse { return Result.failure(it) }

    readDeviceInfo(conn)
    subscribeToNotifications(conn)

    // Auto-resubscribe on reconnect
    conn.onReconnected = {
      conn.discoverServices()
      subscribeToNotifications(conn)
    }

    _isConnected.value = true
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
        it[0].toInt() and 0xFF
      }
    }
  }

  private fun subscribeToNotifications(conn: BleConnection) {
    val dataChar = conn.getCharacteristic(BleConstants.FTMS_SERVICE, BleConstants.INDOOR_BIKE_DATA)
    if (dataChar != null) {
      scope.launch {
        val notificationFlow = conn.enableNotifications(dataChar)
        notificationFlow.collect { bytes ->
          val parsed = IndoorBikeDataParser.parse(bytes)
          if (parsed != null) {
            _data.value = parsed
          }
        }
      }
    }
  }

  suspend fun disconnect() {
    connection?.disconnect()
    connection = null
    _isConnected.value = false
    _data.value = null
  }
}
