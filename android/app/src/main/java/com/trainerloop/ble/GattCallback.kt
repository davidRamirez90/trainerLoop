package com.trainerloop.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import java.util.UUID

class GattCallback(
  private val scope: CoroutineScope? = null
) : BluetoothGattCallback() {

  private var connectionDeferred = CompletableDeferred<Boolean>()
  private var servicesDeferred = CompletableDeferred<Boolean>()

  // Per-characteristic deferreds. A previous version used a single shared
  // deferred for all descriptor writes / reads / writes, which meant two
  // concurrent operations on different characteristics clobbered each other
  // (the second reset*() call replaced the deferred the first caller was
  // still awaiting, so the first caller hung forever). Keying by the
  // characteristic UUID makes each caller await its own result. Android
  // still only allows one outstanding GATT op at a time, so BleConnection
  // serialises dispatch via a Mutex — these deferreds just make sure the
  // right callback completes the right awaiter.
  private val writeDeferreds = mutableMapOf<String, CompletableDeferred<Boolean>>()
  private val readDeferreds = mutableMapOf<String, CompletableDeferred<ByteArray>>()
  private val descriptorWriteDeferreds = mutableMapOf<String, CompletableDeferred<Boolean>>()
  private val notificationChannels = mutableMapOf<String, Channel<ByteArray>>()

  val connectionResult: CompletableDeferred<Boolean> get() = connectionDeferred
  val servicesResult: CompletableDeferred<Boolean> get() = servicesDeferred

  fun resetConnectionDeferred() { connectionDeferred = CompletableDeferred() }
  fun resetServicesDeferred() { servicesDeferred = CompletableDeferred() }

  fun resetWriteDeferred(uuid: UUID): CompletableDeferred<Boolean> {
    val d = CompletableDeferred<Boolean>()
    synchronized(writeDeferreds) { writeDeferreds[uuid.toString()] = d }
    return d
  }

  fun resetReadDeferred(uuid: UUID): CompletableDeferred<ByteArray> {
    val d = CompletableDeferred<ByteArray>()
    synchronized(readDeferreds) { readDeferreds[uuid.toString()] = d }
    return d
  }

  fun resetDescriptorWriteDeferred(uuid: UUID): CompletableDeferred<Boolean> {
    val d = CompletableDeferred<Boolean>()
    synchronized(descriptorWriteDeferreds) { descriptorWriteDeferreds[uuid.toString()] = d }
    return d
  }

  var onUnexpectedDisconnect: (() -> Unit)? = null

  private var _status = BluetoothProfile.STATE_DISCONNECTED
  val status: Int get() = _status

  override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
    com.trainerloop.ble.BleLog.d(
      "onConnectionStateChange status=$status newState=$newState"
    )
    _status = newState
    if (status == BluetoothGatt.GATT_SUCCESS) {
      if (newState == BluetoothProfile.STATE_CONNECTED) {
        connectionDeferred.complete(true)
      } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
        if (!connectionDeferred.isCompleted) {
          connectionDeferred.complete(false)
        } else {
          scope?.launch {
            onUnexpectedDisconnect?.invoke()
          }
        }
        closeAllChannels()
      }
    } else {
      if (!connectionDeferred.isCompleted) {
        if (newState == BluetoothProfile.STATE_DISCONNECTED) {
          connectionDeferred.complete(false)
        } else {
          connectionDeferred.completeExceptionally(
            GattException("Connection state change failed with status $status")
          )
        }
      } else {
        scope?.launch {
          onUnexpectedDisconnect?.invoke()
        }
      }
      closeAllChannels()
    }
  }

  override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
    com.trainerloop.ble.BleLog.d("onServicesDiscovered status=$status")
    servicesDeferred.complete(status == BluetoothGatt.GATT_SUCCESS)
  }

  override fun onCharacteristicChanged(
    gatt: BluetoothGatt,
    characteristic: BluetoothGattCharacteristic,
    value: ByteArray
  ) {
    com.trainerloop.ble.BleLog.d {
      "notification char=${characteristic.uuid} len=${value.size} bytes=${value.toHex()}"
    }
    dispatchNotification(characteristic.uuid, value)
  }

  private fun ByteArray.toHex(): String =
    joinToString(separator = " ") { "%02X".format(it.toInt() and 0xFF) }

  @Deprecated("Deprecated in Android SDK", ReplaceWith("onCharacteristicChanged(gatt, characteristic, value)"))
  @Suppress("DEPRECATION")
  override fun onCharacteristicChanged(
    gatt: BluetoothGatt,
    characteristic: BluetoothGattCharacteristic
  ) {
    com.trainerloop.ble.BleLog.d {
      "notification(legacy) char=${characteristic.uuid} bytes=${characteristic.value?.toHex() ?: "null"}"
    }
    dispatchNotification(characteristic.uuid, characteristic.value ?: return)
  }

  override fun onCharacteristicWrite(
    gatt: BluetoothGatt,
    characteristic: BluetoothGattCharacteristic,
    status: Int
  ) {
    val key = characteristic.uuid.toString()
    val ok = status == BluetoothGatt.GATT_SUCCESS
    com.trainerloop.ble.BleLog.d("onCharacteristicWrite char=$key status=$status ok=$ok")
    synchronized(writeDeferreds) { writeDeferreds.remove(key) }?.complete(ok)
  }

  override fun onCharacteristicRead(
    gatt: BluetoothGatt,
    characteristic: BluetoothGattCharacteristic,
    value: ByteArray,
    status: Int
  ) {
    val key = characteristic.uuid.toString()
    com.trainerloop.ble.BleLog.d("onCharacteristicRead char=$key status=$status len=${value.size}")
    val d = synchronized(readDeferreds) { readDeferreds.remove(key) }
    if (d != null) {
      if (status == BluetoothGatt.GATT_SUCCESS) {
        d.complete(value)
      } else {
        d.completeExceptionally(GattException("Read failed with status $status"))
      }
    }
  }

  @Deprecated("Deprecated in Android SDK", ReplaceWith("onCharacteristicRead(gatt, characteristic, value, status)"))
  @Suppress("DEPRECATION")
  override fun onCharacteristicRead(
    gatt: BluetoothGatt,
    characteristic: BluetoothGattCharacteristic,
    status: Int
  ) {
    val key = characteristic.uuid.toString()
    com.trainerloop.ble.BleLog.d("onCharacteristicRead(legacy) char=$key status=$status")
    val d = synchronized(readDeferreds) { readDeferreds.remove(key) }
    if (d != null) {
      if (status == BluetoothGatt.GATT_SUCCESS) {
        @Suppress("DEPRECATION")
        d.complete(characteristic.value ?: ByteArray(0))
      } else {
        d.completeExceptionally(GattException("Read failed with status $status"))
      }
    }
  }

  override fun onDescriptorWrite(
    gatt: BluetoothGatt,
    descriptor: BluetoothGattDescriptor,
    status: Int
  ) {
    // Key by the parent characteristic UUID so enableNotifications() can
    // await the specific descriptor write it issued.
    val key = descriptor.characteristic?.uuid?.toString()
    val ok = status == BluetoothGatt.GATT_SUCCESS
    com.trainerloop.ble.BleLog.d("onDescriptorWrite char=$key status=$status ok=$ok")
    if (key != null) {
      synchronized(descriptorWriteDeferreds) { descriptorWriteDeferreds.remove(key) }?.complete(ok)
    }
  }

  fun notificationsFor(characteristicUuid: UUID): Flow<ByteArray> {
    val key = characteristicUuid.toString()
    val channel = Channel<ByteArray>(Channel.BUFFERED)
    synchronized(notificationChannels) {
      notificationChannels[key]?.close()
      notificationChannels[key] = channel
    }
    return channel.consumeAsFlow()
  }

  private fun dispatchNotification(uuid: UUID, value: ByteArray) {
    val key = uuid.toString()
    synchronized(notificationChannels) {
      notificationChannels[key]?.trySend(value)
    }
  }

  private fun closeAllChannels() {
    synchronized(notificationChannels) {
      notificationChannels.values.forEach { it.close() }
      notificationChannels.clear()
    }
  }

  class GattException(message: String) : Exception(message)
}
