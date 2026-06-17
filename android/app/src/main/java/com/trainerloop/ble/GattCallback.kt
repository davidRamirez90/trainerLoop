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
  private var writeDeferred = CompletableDeferred<Boolean>()
  private var readDeferred = CompletableDeferred<ByteArray>()
  private var descriptorWriteDeferred = CompletableDeferred<Boolean>()

  private val notificationChannels = mutableMapOf<String, Channel<ByteArray>>()

  val connectionResult: CompletableDeferred<Boolean> get() = connectionDeferred
  val servicesResult: CompletableDeferred<Boolean> get() = servicesDeferred
  val writeResult: CompletableDeferred<Boolean> get() = writeDeferred
  val readResult: CompletableDeferred<ByteArray> get() = readDeferred
  val descriptorWriteResult: CompletableDeferred<Boolean> get() = descriptorWriteDeferred

  fun resetConnectionDeferred() { connectionDeferred = CompletableDeferred() }
  fun resetServicesDeferred() { servicesDeferred = CompletableDeferred() }
  fun resetWriteDeferred() { writeDeferred = CompletableDeferred() }
  fun resetReadDeferred() { readDeferred = CompletableDeferred() }
  fun resetDescriptorWriteDeferred() { descriptorWriteDeferred = CompletableDeferred() }

  var onUnexpectedDisconnect: (() -> Unit)? = null

  private var _status = BluetoothProfile.STATE_DISCONNECTED
  val status: Int get() = _status

  override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
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
    servicesDeferred.complete(status == BluetoothGatt.GATT_SUCCESS)
  }

  override fun onCharacteristicChanged(
    gatt: BluetoothGatt,
    characteristic: BluetoothGattCharacteristic,
    value: ByteArray
  ) {
    dispatchNotification(characteristic.uuid, value)
  }

  @Deprecated("Deprecated in Android SDK", ReplaceWith("onCharacteristicChanged(gatt, characteristic, value)"))
  @Suppress("DEPRECATION")
  override fun onCharacteristicChanged(
    gatt: BluetoothGatt,
    characteristic: BluetoothGattCharacteristic
  ) {
    dispatchNotification(characteristic.uuid, characteristic.value)
  }

  override fun onCharacteristicWrite(
    gatt: BluetoothGatt,
    characteristic: BluetoothGattCharacteristic,
    status: Int
  ) {
    writeDeferred.complete(status == BluetoothGatt.GATT_SUCCESS)
  }

  override fun onCharacteristicRead(
    gatt: BluetoothGatt,
    characteristic: BluetoothGattCharacteristic,
    value: ByteArray,
    status: Int
  ) {
    if (status == BluetoothGatt.GATT_SUCCESS) {
      readDeferred.complete(value)
    } else {
      readDeferred.completeExceptionally(GattException("Read failed with status $status"))
    }
  }

  @Deprecated("Deprecated in Android SDK", ReplaceWith("onCharacteristicRead(gatt, characteristic, value, status)"))
  @Suppress("DEPRECATION")
  override fun onCharacteristicRead(
    gatt: BluetoothGatt,
    characteristic: BluetoothGattCharacteristic,
    status: Int
  ) {
    if (status == BluetoothGatt.GATT_SUCCESS) {
      readDeferred.complete(characteristic.value)
    } else {
      readDeferred.completeExceptionally(GattException("Read failed with status $status"))
    }
  }

  override fun onDescriptorWrite(
    gatt: BluetoothGatt,
    descriptor: BluetoothGattDescriptor,
    status: Int
  ) {
    descriptorWriteDeferred.complete(status == BluetoothGatt.GATT_SUCCESS)
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
