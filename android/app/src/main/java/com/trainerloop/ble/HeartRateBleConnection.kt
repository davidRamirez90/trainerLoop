package com.trainerloop.ble

import android.bluetooth.BluetoothGattCharacteristic
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * The subset of a BLE link needed by [HrManager].
 *
 * A combined FTMS + Heart Rate peripheral must reuse the trainer's existing
 * GATT link. Android can behave unpredictably when the same app opens two GATT
 * clients to one peripheral.
 */
internal interface HeartRateBleConnection {
  suspend fun connect(): Result<Unit>

  suspend fun discoverServices(): Result<Unit>

  fun getCharacteristic(
    serviceUuid: UUID,
    characteristicUuid: UUID
  ): BluetoothGattCharacteristic?

  suspend fun enableNotifications(
    characteristic: BluetoothGattCharacteristic
  ): Flow<ByteArray>

  fun addReconnectHandler(handler: suspend () -> Unit)

  suspend fun disconnect()
}
