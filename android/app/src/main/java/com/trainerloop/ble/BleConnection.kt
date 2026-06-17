package com.trainerloop.ble

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
  var onReconnected: (suspend () -> Unit)? = null

  init {
    callback.onUnexpectedDisconnect = { handleUnexpectedDisconnect() }
  }

  @Suppress("MissingPermission")
  suspend fun connect(): Result<Unit> {
    userInitiatedDisconnect = false
    _connectionStatus.value = ConnectionStatus.CONNECTING
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
  }

  internal fun handleUnexpectedDisconnect() {
    if (!autoReconnectEnabled || userInitiatedDisconnect) return
    _connectionState.value = false
    _connectionStatus.value = ConnectionStatus.RECONNECTING
    scope.launch { reconnectWithBackoff() }
  }

  private suspend fun reconnectWithBackoff() {
    var attempt = 0
    while (isActiveForReconnect()) {
      val delayMs = BACKOFF_DELAYS[attempt.coerceAtMost(BACKOFF_DELAYS.lastIndex)]
      delay(delayMs)
      if (!isActiveForReconnect()) return
      _connectionStatus.value = ConnectionStatus.CONNECTING
      gatt?.close()
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
        // Rediscover services after reconnect
        discoverServices()
        // Notify manager to re-subscribe notifications
        onReconnected?.invoke()
        return
      }
      attempt++
    }
  }

  private fun isActiveForReconnect(): Boolean =
    scope.isActive && autoReconnectEnabled && !userInitiatedDisconnect

  @Suppress("MissingPermission")
  suspend fun discoverServices(): Result<Unit> {
    val gattInstance = gatt ?: return Result.failure(Exception("Not connected"))
    callback.resetServicesDeferred()
    return if (gattInstance.discoverServices()) {
      val success = callback.servicesResult.await()
      if (success) Result.success(Unit) else Result.failure(Exception("Service discovery failed"))
    } else {
      Result.failure(Exception("Could not start service discovery"))
    }
  }

  fun getCharacteristic(serviceUuid: UUID, characteristicUuid: UUID): BluetoothGattCharacteristic? {
    return gatt?.getService(serviceUuid)?.getCharacteristic(characteristicUuid)
  }

  @Suppress("MissingPermission")
  suspend fun enableNotifications(characteristic: BluetoothGattCharacteristic): Flow<ByteArray> {
    val gattInstance = gatt ?: throw IllegalStateException("Not connected")
    gattInstance.setCharacteristicNotification(characteristic, true)

    val descriptorUuid = CLIENT_CHARACTERISTIC_CONFIG_UUID
    val descriptor = characteristic.getDescriptor(descriptorUuid)
    if (descriptor != null) {
      callback.resetDescriptorWriteDeferred()
      val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        gattInstance.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothGatt.GATT_SUCCESS
      } else {
        @Suppress("DEPRECATION")
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        @Suppress("DEPRECATION")
        gattInstance.writeDescriptor(descriptor)
      }
      if (started) {
        callback.descriptorWriteResult.await()
      }
    }

    return callback.notificationsFor(characteristic.uuid)
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

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val status = gattInstance.writeCharacteristic(characteristic, bytes, writeType)
      if (status == BluetoothGatt.GATT_SUCCESS) {
        Result.success(Unit)
      } else {
        Result.failure(Exception("Write failed with status $status"))
      }
    } else {
      callback.resetWriteDeferred()
      @Suppress("DEPRECATION")
      characteristic.writeType = writeType
      @Suppress("DEPRECATION")
      characteristic.value = bytes
      @Suppress("DEPRECATION")
      val started = gattInstance.writeCharacteristic(characteristic)
      if (!started) return Result.failure(Exception("Could not start write"))
      val success = callback.writeResult.await()
      if (success) Result.success(Unit) else Result.failure(Exception("Write failed"))
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
    return try {
      callback.resetReadDeferred()
      @Suppress("DEPRECATION")
      val started = gattInstance.readCharacteristic(char)
      if (!started) return null
      val bytes = callback.readResult.await()
      parse(bytes)
    } catch (_: Throwable) {
      null
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
  }
}
