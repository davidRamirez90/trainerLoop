package com.trainerloop.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import com.trainerloop.ble.model.IndoorBikeData
import com.trainerloop.ble.model.Stamped
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
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
  fun `standard HR source subscribes to heart rate measurement notifications`() =
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
      notifications.emit(byteArrayOf(0x00, 0x91.toByte()))
      runCurrent()

      assertEquals(145, manager.heartRate.value?.value)
      assertEquals(1, connection.getCharacteristicCalls)
      assertEquals(1, connection.enableNotificationsCalls)
    }

  @Test
  fun `same-device FTMS HR preserves timestamp without an HR GATT operation`() =
    runTest(UnconfinedTestDispatcher()) {
      val ftmsData = MutableStateFlow<Stamped<IndoorBikeData>?>(null)
      val connection = FakeHeartRateBleConnection()
      val manager = HrManager(
        context = mockk<Context>(relaxed = true),
        device = mockk<BluetoothDevice>(relaxed = true),
        sharedConnection = connection,
        ftmsData = ftmsData,
        dispatcher = UnconfinedTestDispatcher(testScheduler)
      )

      assertTrue(manager.connect().isSuccess)
      assertFalse(connection.connectCalled)
      assertFalse(connection.discoverServicesCalled)
      assertTrue(manager.isConnected.value)

      val stamped = Stamped(
        IndoorBikeData(
          powerWatts = 200,
          cadenceRpm = 90.0,
          speedKph = 30.0,
          resistanceLevel = null,
          averagePower = null,
          averageSpeed = null,
          totalDistanceMeters = null,
          heartRateBpm = 145,
          elapsedTimeSec = null,
          remainingTimeSec = null
        ),
        atMs = 123_456L
      )
      ftmsData.value = stamped
      runCurrent()

      assertEquals(145, manager.heartRate.value?.value)
      assertEquals(123_456L, manager.heartRate.value?.atMs)
      assertEquals(0, connection.getCharacteristicCalls)
      assertEquals(0, connection.enableNotificationsCalls)

      manager.disconnect()
      assertFalse(connection.disconnectCalled)
    }

  private class FakeHeartRateBleConnection(
    private val notifications: Flow<ByteArray> = emptyFlow()
  ) : HeartRateBleConnection {
    var connectCalled = false
    var discoverServicesCalled = false
    var disconnectCalled = false
    var getCharacteristicCalls = 0
    var enableNotificationsCalls = 0

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
    ): BluetoothGattCharacteristic {
      getCharacteristicCalls++
      return mockk(relaxed = true)
    }

    override suspend fun enableNotifications(
      characteristic: BluetoothGattCharacteristic
    ): Flow<ByteArray> {
      enableNotificationsCalls++
      return notifications
    }

    override fun addReconnectHandler(handler: suspend () -> Unit) = Unit

    override suspend fun disconnect() {
      disconnectCalled = true
    }
  }
}
