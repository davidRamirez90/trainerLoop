package com.trainerloop.ble

import android.bluetooth.BluetoothDevice
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
  private val context: Context,
  private val device: BluetoothDevice
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var connection: BleConnection? = null
  private var hasControl = false

  private val _status = MutableStateFlow(FtmsControlStatus.IDLE)
  val status: StateFlow<FtmsControlStatus> = _status.asStateFlow()

  private val _error = MutableStateFlow<String?>(null)
  val error: StateFlow<String?> = _error.asStateFlow()

  private val _isConnected = MutableStateFlow(false)
  val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

  private var lastSendTimeMs: Long = 0L
  private var lastTargetWatts: Int = -1

  suspend fun connect(): Result<Unit> {
    val conn = BleConnection(context, device)
    connection = conn
    conn.connect().getOrElse { return Result.failure(it) }
    conn.discoverServices().getOrElse { return Result.failure(it) }

    val cpChar = conn.getCharacteristic(
      BleConstants.FTMS_SERVICE,
      BleConstants.FITNESS_MACHINE_CONTROL_POINT
    ) ?: return Result.failure(Exception("Control point characteristic not found"))

    val responseFlow = conn.enableNotifications(cpChar)
    scope.launch {
      responseFlow.collect { bytes -> handleResponse(bytes) }
    }

    _status.value = FtmsControlStatus.REQUESTING
    conn.writeCharacteristic(cpChar, FtmsCommands.requestControl())
      .getOrElse { return Result.failure(it) }

    _isConnected.value = true
    return Result.success(Unit)
  }

  suspend fun disconnect() {
    connection?.disconnect()
    connection = null
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
    val result = connection?.writeCharacteristic(char, FtmsCommands.setTargetPower(nextTarget))
    if (result?.isSuccess == true) {
      lastSendTimeMs = now
      lastTargetWatts = nextTarget
      return true
    }
    _error.value = "Trainer target write failed."
    return false
  }

  suspend fun startResume(): Boolean = sendCommand(FtmsCommands.startResume(), "Trainer start failed.")

  suspend fun stopPause(stop: Boolean): Boolean =
    sendCommand(FtmsCommands.stopPause(stop), if (stop) "Trainer stop failed." else "Trainer pause failed.")

  private suspend fun sendCommand(payload: ByteArray, errorMsg: String): Boolean {
    if (!hasControl || _status.value != FtmsControlStatus.READY) return false
    val char = getControlPointCharacteristic() ?: return false
    val result = connection?.writeCharacteristic(char, payload)
    if (result?.isSuccess != true) {
      _error.value = errorMsg
    }
    return result?.isSuccess == true
  }

  private fun getControlPointCharacteristic() =
    connection?.getCharacteristic(BleConstants.FTMS_SERVICE, BleConstants.FITNESS_MACHINE_CONTROL_POINT)

  private fun handleResponse(bytes: ByteArray) {
    if (bytes.size < 3) return
    val responseOpcode = bytes[0].toInt() and 0xFF
    if (responseOpcode != 0x80) return
    val requestOpcode = bytes[1].toInt() and 0xFF
    val resultCode = bytes[2].toInt() and 0xFF
    val success = resultCode == 0x01

    when (requestOpcode) {
      0x00 -> { // Request Control
        if (success) {
          hasControl = true
          _status.value = FtmsControlStatus.READY
          _error.value = null
        } else {
          hasControl = false
          _status.value = FtmsControlStatus.ERROR
          _error.value = "Trainer denied control."
        }
      }
      0x05 -> { // Set Target Power
        if (!success) _error.value = "Trainer rejected target power."
      }
      0x07 -> { // Start/Resume
        if (!success) _error.value = "Trainer rejected start command."
      }
      0x08 -> { // Stop/Pause
        if (!success) _error.value = "Trainer rejected stop command."
      }
    }
  }

  companion object {
    private const val MIN_SEND_INTERVAL_MS = 900L
  }
}
