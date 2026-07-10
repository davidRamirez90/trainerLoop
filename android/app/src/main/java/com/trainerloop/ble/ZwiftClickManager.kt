package com.trainerloop.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Zwift Click BLE client. The Click is its own peripheral, so unlike the
 * FTMS managers this owns a private [BleConnection] (same pattern as
 * [HrManager]). Performs the proprietary RideOn handshake and turns button
 * notifications into [ClickShift] events — see [ZwiftClickProtocol] for the
 * wire format and its provenance.
 */
class ZwiftClickManager(
  private val context: Context,
  val device: BluetoothDevice
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var connection: BleConnection? = null
  private val shiftDetector = ClickShiftDetector()

  // extraBufferCapacity so tryEmit from the notification collector never
  // drops a shift while the ViewModel collector is momentarily busy.
  private val _shiftEvents = MutableSharedFlow<ClickShift>(extraBufferCapacity = 16)
  val shiftEvents: SharedFlow<ClickShift> = _shiftEvents.asSharedFlow()

  private val _batteryLevel = MutableStateFlow<Int?>(null)
  val batteryLevel: StateFlow<Int?> = _batteryLevel.asStateFlow()

  private val _isConnected = MutableStateFlow(false)
  val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

  suspend fun connect(): Result<Unit> {
    BleLog.d("ZwiftClickManager.connect device=${device.address}")
    val conn = BleConnection(context, device)
    connection = conn

    conn.connect().getOrElse {
      BleLog.e("ZwiftClickManager.connect: connect() failed: ${it.message}")
      return Result.failure(it)
    }
    conn.discoverServices().getOrElse {
      BleLog.e("ZwiftClickManager.connect: discoverServices failed: ${it.message}")
      return Result.failure(it)
    }
    subscribeAndHandshake(conn).getOrElse {
      BleLog.e("ZwiftClickManager.connect: handshake failed: ${it.message}")
      conn.disconnect()
      connection = null
      return Result.failure(it)
    }

    // Auto re-handshake on reconnect. Service discovery is re-run once by
    // BleConnection before handlers fire; notification channels were closed
    // on the drop, so re-arming creates fresh collectors (GattCallback
    // replaces the per-characteristic channel — old collectors end cleanly).
    conn.addReconnectHandler {
      shiftDetector.reset()
      subscribeAndHandshake(conn)
        .onFailure { BleLog.e("Zwift Click re-handshake failed: ${it.message}") }
    }

    _isConnected.value = true
    BleLog.d("ZwiftClickManager.connect success")
    return Result.success(Unit)
  }

  private suspend fun subscribeAndHandshake(conn: BleConnection): Result<Unit> {
    val asyncChar = conn.getCharacteristic(
      BleConstants.ZWIFT_CLICK_SERVICE, BleConstants.ZWIFT_CLICK_ASYNC
    )
    val syncTxChar = conn.getCharacteristic(
      BleConstants.ZWIFT_CLICK_SERVICE, BleConstants.ZWIFT_CLICK_SYNC_TX
    )
    val syncRxChar = conn.getCharacteristic(
      BleConstants.ZWIFT_CLICK_SERVICE, BleConstants.ZWIFT_CLICK_SYNC_RX
    )
    if (asyncChar == null || syncTxChar == null || syncRxChar == null) {
      return Result.failure(
        Exception(
          "Zwift Click service/characteristics not found — " +
            "device may need a firmware update via the Zwift Companion app"
        )
      )
    }

    val handshakeAck = CompletableDeferred<Unit>()

    // Arm both notification sources BEFORE writing RideOn so the ack (an
    // indication on sync TX) cannot be missed.
    collectFrames(conn.enableNotifications(asyncChar), handshakeAck)
    collectFrames(conn.enableNotifications(syncTxChar), handshakeAck)

    // The Click's sync RX is write-without-response; fall back to a
    // response write if a future firmware drops the no-response property.
    val withResponse =
      syncRxChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE == 0
    conn.writeCharacteristic(syncRxChar, ZwiftClickProtocol.RIDE_ON, withResponse)
      .getOrElse { return Result.failure(it) }

    withTimeoutOrNull(HANDSHAKE_TIMEOUT_MS) { handshakeAck.await() }
      ?: return Result.failure(Exception("Zwift Click RideOn handshake timed out"))

    // Standard battery service (0x180F) seeds the level; 0x19 notification
    // frames keep it fresh afterwards. Null (service absent) is fine.
    scope.launch {
      conn.read(BleConstants.BATTERY_SERVICE, BleConstants.BATTERY_LEVEL) {
        if (it.isEmpty()) null else it[0].toInt() and 0xFF
      }?.let { _batteryLevel.value = it }
    }
    return Result.success(Unit)
  }

  private fun collectFrames(flow: Flow<ByteArray>, handshakeAck: CompletableDeferred<Unit>) {
    scope.launch {
      try {
        flow.collect { bytes -> onFrame(bytes, handshakeAck) }
      } catch (t: Throwable) {
        BleLog.e("Zwift Click notification collector crashed", t)
      }
    }
  }

  private fun onFrame(bytes: ByteArray, handshakeAck: CompletableDeferred<Unit>) {
    when (val message = ZwiftClickProtocol.parse(bytes)) {
      is ClickMessage.HandshakeAck -> {
        BleLog.d("Zwift Click handshake acknowledged")
        handshakeAck.complete(Unit)
      }
      is ClickMessage.ButtonState -> {
        shiftDetector.onState(message).forEach { shift ->
          BleLog.d("Zwift Click shift $shift")
          _shiftEvents.tryEmit(shift)
        }
      }
      is ClickMessage.Battery -> _batteryLevel.value = message.percent
      ClickMessage.KeepAlive -> {}
      ClickMessage.Unknown -> BleLog.w(
        "Zwift Click unknown frame: " +
          bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
      )
    }
  }

  suspend fun disconnect() {
    BleLog.d("ZwiftClickManager.disconnect device=${device.address}")
    connection?.disconnect()
    connection = null
    _isConnected.value = false
    _batteryLevel.value = null
    scope.cancel()
  }

  companion object {
    private const val HANDSHAKE_TIMEOUT_MS = 5_000L
  }
}
