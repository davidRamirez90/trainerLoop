package com.trainerloop.ble

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.trainerloop.ble.model.HeartRateMeasurementParser
import com.trainerloop.ble.model.IndoorBikeData
import com.trainerloop.ble.model.Stamped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

class HrManager internal constructor(
  private val context: Context,
  val device: BluetoothDevice,
  private val sharedConnection: HeartRateBleConnection?,
  private val ftmsData: StateFlow<Stamped<IndoorBikeData>?>? = null,
  dispatcher: CoroutineDispatcher = Dispatchers.Main
) {
  constructor(
    context: Context,
    device: BluetoothDevice
  ) : this(context, device, null, dispatcher = Dispatchers.Main)

  private val scope = CoroutineScope(SupervisorJob() + dispatcher)
  private var connection: HeartRateBleConnection? = null
  private val ownsConnection = sharedConnection == null
  internal val usesSharedTrainerConnection: Boolean = !ownsConnection
  private var active = false

  private val _heartRate = MutableStateFlow<Stamped<Int>?>(null)
  val heartRate: StateFlow<Stamped<Int>?> = _heartRate.asStateFlow()

  private val _isConnected = MutableStateFlow(false)
  val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

  suspend fun connect(): Result<Unit> {
    BleLog.d(
      "HrManager.connect device=${device.address} sharedTrainerLink=${!ownsConnection}"
    )
    val conn = sharedConnection ?: BleConnection(context, device)
    connection = conn

    if (ftmsData != null) {
      scope.launch {
        ftmsData.collect { stamped ->
          _heartRate.value = stamped?.value?.heartRateBpm?.let { heartRate ->
            Stamped(heartRate, stamped.atMs)
          }
        }
      }
      active = true
      _isConnected.value = true
      BleLog.d("HrManager.connect success via FTMS data")
      return Result.success(Unit)
    }

    if (ownsConnection) {
      conn.connect().getOrElse {
        BleLog.e("HrManager.connect: connect() failed: ${it.message}")
        return Result.failure(it)
      }
      conn.discoverServices().getOrElse {
        BleLog.e("HrManager.connect: discoverServices failed: ${it.message}")
        return Result.failure(it)
      }
    }

    val subscribed = subscribeToNotifications(conn)
    if (!subscribed) {
      return Result.failure(Exception("Heart Rate Measurement subscription failed"))
    }

    active = true
    // Auto-resubscribe on reconnect. Service discovery is re-run once by
    // BleConnection before handlers fire.
    conn.addReconnectHandler {
      if (active) subscribeToNotifications(conn)
    }

    _isConnected.value = true
    BleLog.d("HrManager.connect success")
    return Result.success(Unit)
  }

  private suspend fun subscribeToNotifications(conn: HeartRateBleConnection): Boolean {
    val dataChar = conn.getCharacteristic(
      BleConstants.HEART_RATE_SERVICE,
      BleConstants.HEART_RATE_MEASUREMENT
    ) ?: run {
      BleLog.e(
        "HR Measurement characteristic NOT FOUND " +
          "service=${BleConstants.HEART_RATE_SERVICE} char=${BleConstants.HEART_RATE_MEASUREMENT}"
      )
      return false
    }
    BleLog.d("HR characteristic found, enabling notifications")
    val notificationFlow = try {
      conn.enableNotifications(dataChar)
    } catch (t: Throwable) {
      BleLog.e("HR enableNotifications failed", t)
      return false
    }
    scope.launch {
      try {
        BleLog.d("HR notifications enabled, starting collect")
        notificationFlow.collect { bytes ->
          val hr = HeartRateMeasurementParser.parse(bytes)
          if (hr != null) {
            _heartRate.value = Stamped(hr, android.os.SystemClock.elapsedRealtime())
            BleLog.d("HR update: $hr bpm")
          } else {
            BleLog.w("HR parse returned null, dropping ${bytes.size} bytes")
          }
        }
      } catch (t: Throwable) {
        BleLog.e("HR notification collector crashed", t)
      }
    }
    return true
  }

  suspend fun disconnect() {
    BleLog.d("HrManager.disconnect device=${device.address}")
    active = false
    if (ownsConnection) {
      connection?.disconnect()
    }
    connection = null
    _isConnected.value = false
    _heartRate.value = null
    scope.cancel()
  }
}
