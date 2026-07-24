package com.trainerloop.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HrManagerSharedConnectionTest {

  @Test
  fun `shared trainer connection publishes HR without reconnecting or owning link`() =
    runTest(UnconfinedTestDispatcher()) {
      val notifications = MutableSharedFlow<ByteArray>(extraBufferCapacity = 1)
      val connection = FakeHeartRateBleConnection(notifications)
      val manager = HrManager(
        context = mockk<Context>(relaxed = true),
        device = mockk<BluetoothDevice>(relaxed = true),
        sharedConnection = connection,
        dispatcher = UnconfinedTestDispatcher(testScheduler)
      )

      assertTrue(manager.connect().isSuccess)
      assertFalse(connection.connectCalled)
      assertFalse(connection.discoverServicesCalled)

      notifications.emit(byteArrayOf(0x00, 0x91.toByte()))
      runCurrent()

      assertEquals(145, manager.heartRate.value?.value)

      manager.disconnect()
      assertFalse(connection.disconnectCalled)
    }

  private class FakeHeartRateBleConnection(
    private val notifications: Flow<ByteArray>
  ) : HeartRateBleConnection {
    var connectCalled = false
    var discoverServicesCalled = false
    var disconnectCalled = false

    override suspend fun connect(): Result<Unit> {
      connectCalled = true
      return Result.success(Unit)
    }

    override suspend fun discoverServices(): Result<Unit> {
      discoverServicesCalled = true
      return Result.success(Unit)
    }

    override fun getCharacteristic(
      serviceUuid: UUID,
      characteristicUuid: UUID
    ): BluetoothGattCharacteristic = mockk(relaxed = true)

    override suspend fun enableNotifications(
      characteristic: BluetoothGattCharacteristic
    ): Flow<ByteArray> = notifications

    override fun addReconnectHandler(handler: suspend () -> Unit) = Unit

    override suspend fun disconnect() {
      disconnectCalled = true
    }
  }
}
