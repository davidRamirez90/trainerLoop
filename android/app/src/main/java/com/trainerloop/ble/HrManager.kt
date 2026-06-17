package com.trainerloop.ble

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.trainerloop.ble.model.HeartRateMeasurementParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HrManager(
  private val context: Context,
  private val device: BluetoothDevice
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var connection: BleConnection? = null

  private val _heartRate = MutableStateFlow<Int?>(null)
  val heartRate: StateFlow<Int?> = _heartRate.asStateFlow()

  private val _isConnected = MutableStateFlow(false)
  val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

  suspend fun connect(): Result<Unit> {
    val conn = BleConnection(context, device)
    connection = conn

    conn.connect().getOrElse { return Result.failure(it) }
    conn.discoverServices().getOrElse { return Result.failure(it) }

    val dataChar = conn.getCharacteristic(BleConstants.HEART_RATE_SERVICE, BleConstants.HEART_RATE_MEASUREMENT)
    if (dataChar != null) {
      val notificationFlow = conn.enableNotifications(dataChar)
      scope.launch {
        notificationFlow.collect { bytes ->
          val hr = HeartRateMeasurementParser.parse(bytes)
          if (hr != null) {
            _heartRate.value = hr
          }
        }
      }
    }

    _isConnected.value = true
    return Result.success(Unit)
  }

  suspend fun disconnect() {
    connection?.disconnect()
    connection = null
    _isConnected.value = false
    _heartRate.value = null
  }
}
