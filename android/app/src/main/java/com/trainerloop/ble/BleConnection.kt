package com.trainerloop.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

enum class ConnectionStatus {
  IDLE, CONNECTING, CONNECTED, RECONNECTING, DISCONNECTED
}

class BleConnection(
  private val context: Context,
  private val device: BluetoothDevice
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  private val _connectionState = MutableStateFlow(false)
  val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

  private val _connectionStatus = MutableStateFlow(ConnectionStatus.IDLE)
  val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

  private var gatt: BluetoothGatt? = null
  private val callback = GattCallback(scope)
  private var userInitiatedDisconnect = false
  private var autoReconnectEnabled = false

  // Serialises all GATT operations on this connection. Android allows only
  // one outstanding GATT operation at a time; issuing a second while the
  // first is in flight aborts the first. Previously the data manager's
  // descriptor write and the control manager's descriptor write raced,
  // leaving Indoor Bike Data notifications un-armed ("watts don't appear").
  private val gattMutex = Mutex()

  // Multiple managers (FtmsManager + FtmsControlManager) share one
  // BleConnection and each needs to re-arm its own characteristic
  // subscription after a drop. A single `var onReconnected` got
  // overwritten by whichever manager connected last, so the other
  // manager's notifications were never re-armed. Use a list.
  private val reconnectHandlers = mutableListOf<suspend () -> Unit>()

  init {
    callback.onUnexpectedDisconnect = { handleUnexpectedDisconnect() }
  }

  fun addReconnectHandler(handler: suspend () -> Unit) {
    synchronized(reconnectHandlers) { reconnectHandlers.add(handler) }
  }

  @Suppress("MissingPermission")
  suspend fun connect(): Result<Unit> {
    userInitiatedDisconnect = false
    _connectionStatus.value = ConnectionStatus.CONNECTING
    gatt?.close()
    gatt = null
    callback.resetConnectionDeferred()
    val gattInstance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    } else {
      device.connectGatt(context, false, callback)
    }
    gatt = gattInstance
    val success: Boolean = try {
      callback.connectionResult.await()
    } catch (e: Exception) {
      false
    }
    return if (success) {
      _connectionState.value = true
      _connectionStatus.value = ConnectionStatus.CONNECTED
      autoReconnectEnabled = true
      Result.success(Unit)
    } else {
      gattInstance.close()
      gatt = null
      _connectionState.value = false
      _connectionStatus.value = ConnectionStatus.DISCONNECTED
      Result.failure(Exception("Failed to connect to ${device.address}"))
    }
  }

  @Suppress("MissingPermission")
  suspend fun disconnect() {
    userInitiatedDisconnect = true
    autoReconnectEnabled = false
    _connectionStatus.value = ConnectionStatus.IDLE
    gatt?.disconnect()
    gatt?.close()
    gatt = null
    _connectionState.value = false
    scope.cancel()
  }

  internal fun handleUnexpectedDisconnect() {
    if (!autoReconnectEnabled || userInitiatedDisconnect) return
    _connectionState.value = false
    _connectionStatus.value = ConnectionStatus.RECONNECTING
    scope.launch { reconnectWithBackoff() }
  }

  @SuppressLint("MissingPermission")
  private suspend fun reconnectWithBackoff() {
    var attempt = 0
    while (isActiveForReconnect()) {
      val delayMs = BACKOFF_DELAYS[attempt.coerceAtMost(BACKOFF_DELAYS.lastIndex)]
      delay(delayMs)
      if (!isActiveForReconnect()) return
      _connectionStatus.value = ConnectionStatus.CONNECTING
      gatt?.close()
      gatt = null
      callback.resetConnectionDeferred()
      val gattInstance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
      } else {
        device.connectGatt(context, false, callback)
      }
      gatt = gattInstance
      val connected = try {
        callback.connectionResult.await()
      } catch (e: Exception) {
        false
      }
      if (connected) {
        _connectionState.value = true
        _connectionStatus.value = ConnectionStatus.CONNECTED
        // Rediscover services once, then let every registered manager
        // re-arm its own subscriptions. Previously each handler also
        // called discoverServices(), which raced with in-flight ops.
        discoverServices()
        val handlers = synchronized(reconnectHandlers) { reconnectHandlers.toList() }
        handlers.forEach { runCatching { it.invoke() } }
        return
      }
      gattInstance.close()
      gatt = null
      attempt++
    }
  }

  private fun isActiveForReconnect(): Boolean =
    scope.isActive && autoReconnectEnabled && !userInitiatedDisconnect

  @Suppress("MissingPermission")
  suspend fun discoverServices(): Result<Unit> {
    val gattInstance = gatt ?: return Result.failure(Exception("Not connected"))
    return gattMutex.withLock {
      callback.resetServicesDeferred()
      if (gattInstance.discoverServices()) {
        val success = withTimeoutOrNull(GATT_OPERATION_TIMEOUT_MS) {
          callback.servicesResult.await()
        } ?: run {
          callback.servicesResult.cancel()
          false
        }
        if (success) Result.success(Unit) else Result.failure(Exception("Service discovery failed"))
      } else {
        Result.failure(Exception("Could not start service discovery"))
      }
    }
  }

  fun getCharacteristic(serviceUuid: UUID, characteristicUuid: UUID): BluetoothGattCharacteristic? {
    return gatt?.getService(serviceUuid)?.getCharacteristic(characteristicUuid)
  }

  /**
   * Enables notifications (or indications) for [characteristic] and returns
   * a flow of its notification payloads.
   *
   * The CCCD value is chosen from the characteristic's properties: FTMS
   * Indoor Bike Data (0x2AD2) is Notify, but the FTMS Control Point
   * (0x2AD9) is **Indicate**. Writing ENABLE_NOTIFICATION_VALUE to an
   * indicate-only characteristic is rejected by the peripheral, so the
   * control-point responses never arrived — which is why the trainer was
   * not controllable. Branch on PROPERTY_INDICATE and write
   * ENABLE_INDICATION_VALUE in that case.
   *
   * The descriptor write is serialised via [gattMutex] against every other
   * GATT op on this connection, because Android only allows one
   * outstanding GATT operation at a time.
   */
  @Suppress("MissingPermission")
  @SuppressLint("WrongConstant")
  suspend fun enableNotifications(characteristic: BluetoothGattCharacteristic): Flow<ByteArray> {
    val gattInstance = gatt ?: throw IllegalStateException("Not connected")
    val uuid = characteristic.uuid
    val useIndicate =
      characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
    val cccdValue = if (useIndicate) {
      BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
    } else {
      BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
    }
    BleLog.d(
      "enableNotifications char=$uuid indicate=$useIndicate " +
        "props=0x${"%02X".format(characteristic.properties)}"
    )

    // Create the channel BEFORE arming the peripheral, so the first
    // indication/notification is not dropped.
    val flow = callback.notificationsFor(uuid)

    gattMutex.withLock {
      gattInstance.setCharacteristicNotification(characteristic, true)

      val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
      if (descriptor != null) {
        val deferred = callback.resetDescriptorWriteDeferred(uuid)
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          gattInstance.writeDescriptor(descriptor, cccdValue) == BluetoothGatt.GATT_SUCCESS
        } else {
          @Suppress("DEPRECATION")
          descriptor.value = cccdValue
          @Suppress("DEPRECATION")
          gattInstance.writeDescriptor(descriptor)
        }
        if (started) {
          val ok = withTimeoutOrNull(GATT_OPERATION_TIMEOUT_MS) {
            deferred.await()
          } ?: run {
            deferred.cancel()
            false
          }
          if (!ok) {
            BleLog.w("Descriptor write for $uuid returned GATT failure (indicate=$useIndicate)")
          }
        } else {
          deferred.cancel()
          BleLog.w("writeDescriptor returned false for $uuid (indicate=$useIndicate)")
        }
      } else {
        BleLog.w("CCCD not found for $uuid — notifications may not arrive")
      }
    }

    return flow
  }

  @Suppress("MissingPermission")
  suspend fun writeCharacteristic(
    characteristic: BluetoothGattCharacteristic,
    bytes: ByteArray,
    withResponse: Boolean = true
  ): Result<Unit> {
    val gattInstance = gatt ?: return Result.failure(Exception("Not connected"))
    val writeType = if (withResponse) {
      BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    } else {
      BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
    }

    return gattMutex.withLock {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val deferred = callback.resetWriteDeferred(characteristic.uuid)
        val status = gattInstance.writeCharacteristic(characteristic, bytes, writeType)
        if (status == BluetoothGatt.GATT_SUCCESS) {
          val success = withTimeoutOrNull(GATT_OPERATION_TIMEOUT_MS) {
            deferred.await()
          } ?: run {
            deferred.cancel()
            false
          }
          if (success) Result.success(Unit) else Result.failure(Exception("Write failed"))
        } else {
          deferred.cancel()
          Result.failure(Exception("Write failed with status $status"))
        }
      } else {
        val deferred = callback.resetWriteDeferred(characteristic.uuid)
        @Suppress("DEPRECATION")
        characteristic.writeType = writeType
        @Suppress("DEPRECATION")
        characteristic.value = bytes
        @Suppress("DEPRECATION")
        val started = gattInstance.writeCharacteristic(characteristic)
        if (!started) {
          deferred.cancel()
          Result.failure(Exception("Could not start write"))
        } else {
          val success = withTimeoutOrNull(GATT_OPERATION_TIMEOUT_MS) {
            deferred.await()
          } ?: run {
            deferred.cancel()
            false
          }
          if (success) Result.success(Unit) else Result.failure(Exception("Write failed"))
        }
      }
    }
  }

  @Suppress("MissingPermission")
  suspend fun <T> read(
    service: UUID,
    characteristic: UUID,
    parse: (ByteArray) -> T
  ): T? {
    val gattInstance = gatt ?: return null
    val char = gattInstance.getService(service)?.getCharacteristic(characteristic) ?: return null
    return gattMutex.withLock {
      val deferred = callback.resetReadDeferred(char.uuid)
      @Suppress("DEPRECATION")
      val started = gattInstance.readCharacteristic(char)
      if (!started) {
        deferred.cancel()
        return@withLock null
      }
      val bytes = withTimeoutOrNull(GATT_OPERATION_TIMEOUT_MS) {
        deferred.await()
      } ?: run {
        deferred.cancel()
        return@withLock null
      }
      try {
        parse(bytes)
      } catch (_: Throwable) {
        null
      }
    }
  }

  @Suppress("MissingPermission")
  suspend fun write(
    service: UUID,
    characteristic: UUID,
    bytes: ByteArray,
    withResponse: Boolean = true
  ) {
    val char = getCharacteristic(service, characteristic)
      ?: throw IllegalArgumentException("Characteristic not found")
    writeCharacteristic(char, bytes, withResponse).getOrThrow()
  }

  companion object {
    private val CLIENT_CHARACTERISTIC_CONFIG_UUID =
      UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private val BACKOFF_DELAYS = longArrayOf(1000, 2000, 4000, 8000, 15000, 30000)
    private const val GATT_OPERATION_TIMEOUT_MS = 10_000L
  }
}
