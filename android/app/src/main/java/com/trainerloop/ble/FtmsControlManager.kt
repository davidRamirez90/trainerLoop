package com.trainerloop.ble

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object FtmsCommands {
  private const val OPCODE_REQUEST_CONTROL: Byte = 0x00
  private const val OPCODE_SET_TARGET_POWER: Byte = 0x05
  private const val OPCODE_START_RESUME: Byte = 0x07
  private const val OPCODE_STOP_PAUSE: Byte = 0x08

  fun requestControl(): ByteArray = byteArrayOf(OPCODE_REQUEST_CONTROL)

  fun startResume(): ByteArray = byteArrayOf(OPCODE_START_RESUME)

  fun stopPause(stop: Boolean): ByteArray =
    byteArrayOf(OPCODE_STOP_PAUSE, if (stop) 0x01 else 0x02)

  fun setTargetPower(watts: Int): ByteArray {
    val clamped = watts.coerceIn(0, MAX_TARGET_WATTS)
    val low = (clamped and 0xFF).toByte()
    val high = ((clamped shr 8) and 0xFF).toByte()
    return byteArrayOf(OPCODE_SET_TARGET_POWER, low, high)
  }

  private const val MAX_TARGET_WATTS = 2000
}

enum class FtmsControlStatus { IDLE, REQUESTING, READY, ERROR }

class FtmsControlManager(
  val device: BluetoothDevice,
  private val connection: BleConnection
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var hasControl = false

  private val _status = MutableStateFlow(FtmsControlStatus.IDLE)
  val status: StateFlow<FtmsControlStatus> = _status.asStateFlow()

  private val _error = MutableStateFlow<String?>(null)
  val error: StateFlow<String?> = _error.asStateFlow()

  private val _isConnected = MutableStateFlow(false)
  val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

  private var lastSendTimeMs: Long = 0L
  private var lastTargetWatts: Int = -1

  /**
   * Subscribes to the FTMS Fitness Machine Control Point on the shared
   * [connection]. The caller is responsible for [BleConnection.connect]
   * and [BleConnection.discoverServices] having been called and returned
   * successfully before invoking this.
   */
  // NOTE: service discovery is performed exactly once by
  // TrainerLoopApplication.connectTrainer(). Calling discoverServices()
  // here again raced with the data manager's subscription on the shared
  // GATT link.
  suspend fun connect(): Result<Unit> {
    setupControlPoint(connection)

    // Auto-resubscribe and re-request control on reconnect. The GATT
    // link is shared with FtmsManager; we only need to re-arm the
    // characteristic subscription and re-send Request Control.
    // Service discovery is re-run once by BleConnection before handlers fire.
    connection.addReconnectHandler {
      setupControlPoint(connection)
    }

    _isConnected.value = true
    return Result.success(Unit)
  }

  private suspend fun setupControlPoint(conn: BleConnection) {
    val cpChar = conn.getCharacteristic(
      BleConstants.FTMS_SERVICE,
      BleConstants.FITNESS_MACHINE_CONTROL_POINT
    ) ?: run {
      com.trainerloop.ble.BleLog.e("FTMS control point characteristic NOT FOUND")
      return
    }
    com.trainerloop.ble.BleLog.d("FTMS control point found, enabling notifications")

    val responseFlow = conn.enableNotifications(cpChar)
    scope.launch {
      responseFlow.collect { bytes -> handleResponse(bytes) }
    }

    _status.value = FtmsControlStatus.REQUESTING
    com.trainerloop.ble.BleLog.d("Sending Request Control (0x00) to trainer")
    val requestResult = conn.writeCharacteristic(cpChar, FtmsCommands.requestControl())
    if (requestResult.isFailure) {
      com.trainerloop.ble.BleLog.e(
        "Request Control write FAILED: ${requestResult.exceptionOrNull()?.message ?: "unknown"}"
      )
    }

    // Belt-and-braces: many FTMS clones either don't reply to Request
    // Control at all, or reply with a delay. If we never see the
    // 0x80 0x00 0x01 ack within REQUEST_CONTROL_TIMEOUT_MS, assume the
    // trainer is permissive and proceed as if control were granted, so
    // Start/Resume / SetTargetPower still go out.
    scope.launch {
      kotlinx.coroutines.delay(REQUEST_CONTROL_TIMEOUT_MS)
      if (_status.value == FtmsControlStatus.REQUESTING) {
        com.trainerloop.ble.BleLog.w(
          "No Request Control response in ${REQUEST_CONTROL_TIMEOUT_MS}ms; " +
            "treating trainer as permissive (will still send Start/Resume)."
        )
        hasControl = true
        _status.value = FtmsControlStatus.READY
      }
    }
  }

  suspend fun disconnect() {
    // Do NOT close the shared BleConnection here — the application
    // owns it and will close it when both managers have been released.
    hasControl = false
    _isConnected.value = false
    _status.value = FtmsControlStatus.IDLE
    _error.value = null
  }

  suspend fun setTargetPower(watts: Int): Boolean {
    if (!hasControl || _status.value != FtmsControlStatus.READY) return false
    val now = System.currentTimeMillis()
    val nextTarget = watts.coerceIn(0, 2000)
    if (nextTarget == lastTargetWatts && now - lastSendTimeMs < MIN_SEND_INTERVAL_MS) return false
    if (now - lastSendTimeMs < MIN_SEND_INTERVAL_MS) return false

    val char = getControlPointCharacteristic() ?: return false
    val result = connection.writeCharacteristic(char, FtmsCommands.setTargetPower(nextTarget))
    if (result.isSuccess) {
      lastSendTimeMs = now
      lastTargetWatts = nextTarget
      return true
    }
    _error.value = "Trainer target write failed."
    return false
  }

  suspend fun startResume(): Boolean {
    com.trainerloop.ble.BleLog.d("Sending Start/Resume (0x07) to trainer")
    return sendCommand(FtmsCommands.startResume(), "Trainer start failed.")
  }

  suspend fun stopPause(stop: Boolean): Boolean {
    com.trainerloop.ble.BleLog.d("Sending Stop/Pause (0x08) to trainer")
    return sendCommand(FtmsCommands.stopPause(stop), if (stop) "Trainer stop failed." else "Trainer pause failed.")
  }

  private suspend fun sendCommand(payload: ByteArray, errorMsg: String): Boolean {
    if (!hasControl) {
      com.trainerloop.ble.BleLog.e("sendCommand: no control, hasControl=false. ${errorMsg}")
      return false
    }
    if (_status.value != FtmsControlStatus.READY) {
      com.trainerloop.ble.BleLog.w("sendCommand: status=${_status.value}, not READY. ${errorMsg}")
      return false
    }
    val char = getControlPointCharacteristic() ?: return false
    val result = connection.writeCharacteristic(char, payload)
    if (!result.isSuccess) {
      _error.value = errorMsg
    }
    return result.isSuccess
  }

  private fun getControlPointCharacteristic() =
    connection.getCharacteristic(BleConstants.FTMS_SERVICE, BleConstants.FITNESS_MACHINE_CONTROL_POINT)

  private fun handleResponse(bytes: ByteArray) {
    if (bytes.size < 3) return
    val responseOpcode = bytes[0].toInt() and 0xFF
    if (responseOpcode != 0x80) return
    val requestOpcode = bytes[1].toInt() and 0xFF
    val resultCode = bytes[2].toInt() and 0xFF
    val success = resultCode == 0x01
    com.trainerloop.ble.BleLog.d(
      "FTMS response op=0x${"%02X".format(requestOpcode)} " +
        "result=0x${"%02X".format(resultCode)} (${if (success) "OK" else "FAIL"})"
    )

    when (requestOpcode) {
      0x00 -> { // Request Control
        if (success) {
          hasControl = true
          _status.value = FtmsControlStatus.READY
          _error.value = null
          com.trainerloop.ble.BleLog.d("FTMS control GRANTED, status=READY")
        } else {
          hasControl = false
          _status.value = FtmsControlStatus.ERROR
          _error.value = "Trainer denied control."
          com.trainerloop.ble.BleLog.e("FTMS control DENIED by trainer")
        }
      }
      0x05 -> { if (!success) _error.value = "Trainer rejected target power." }
      0x07 -> {
        if (!success) _error.value = "Trainer rejected start command."
        com.trainerloop.ble.BleLog.d("FTMS start/resume ${if (success) "ack" else "nack"}")
      }
      0x08 -> {
        if (!success) _error.value = "Trainer rejected stop command."
        com.trainerloop.ble.BleLog.d("FTMS stop/pause ${if (success) "ack" else "nack"}")
      }
    }
  }

  companion object {
    private const val MIN_SEND_INTERVAL_MS = 900L
    private const val REQUEST_CONTROL_TIMEOUT_MS = 2_000L
  }
}
