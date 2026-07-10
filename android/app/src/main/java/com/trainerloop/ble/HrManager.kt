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
import kotlinx.coroutines.cancel

class HrManager(
  private val context: Context,
  val device: BluetoothDevice
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var connection: BleConnection? = null

  private val _heartRate = MutableStateFlow<Int?>(null)
  val heartRate: StateFlow<Int?> = _heartRate.asStateFlow()

  private val _isConnected = MutableStateFlow(false)
  val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

  suspend fun connect(): Result<Unit> {
    BleLog.d("HrManager.connect device=${device.address}")
    val conn = BleConnection(context, device)
    connection = conn

    conn.connect().getOrElse {
      BleLog.e("HrManager.connect: connect() failed: ${it.message}")
      return Result.failure(it)
    }
    conn.discoverServices().getOrElse {
      BleLog.e("HrManager.connect: discoverServices failed: ${it.message}")
      return Result.failure(it)
    }

    subscribeToNotifications(conn)

    // Auto-resubscribe on reconnect. Service discovery is re-run once by
    // BleConnection before handlers fire.
    conn.addReconnectHandler {
      subscribeToNotifications(conn)
    }

    _isConnected.value = true
    BleLog.d("HrManager.connect success")
    return Result.success(Unit)
  }

  private fun subscribeToNotifications(conn: BleConnection) {
    val dataChar = conn.getCharacteristic(BleConstants.HEART_RATE_SERVICE, BleConstants.HEART_RATE_MEASUREMENT)
    if (dataChar == null) {
      BleLog.e(
        "HR Measurement characteristic NOT FOUND " +
          "service=${BleConstants.HEART_RATE_SERVICE} char=${BleConstants.HEART_RATE_MEASUREMENT}"
      )
      return
    }
    BleLog.d("HR characteristic found, enabling notifications")
    scope.launch {
      try {
        val notificationFlow = conn.enableNotifications(dataChar)
        BleLog.d("HR notifications enabled, starting collect")
        notificationFlow.collect { bytes ->
          val hr = HeartRateMeasurementParser.parse(bytes)
          if (hr != null) {
            _heartRate.value = hr
            BleLog.d("HR update: $hr bpm")
          } else {
            BleLog.w("HR parse returned null, dropping ${bytes.size} bytes")
          }
        }
      } catch (t: Throwable) {
        BleLog.e("HR notification collector crashed", t)
      }
    }
  }

  suspend fun disconnect() {
    BleLog.d("HrManager.disconnect device=${device.address}")
    connection?.disconnect()
    connection = null
    _isConnected.value = false
    _heartRate.value = null
    scope.cancel()
  }
}
