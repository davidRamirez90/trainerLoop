package com.trainerloop.ui.devices

import com.trainerloop.ble.BleConstants
import com.trainerloop.ble.model.BleDevice
import org.junit.Assert.assertEquals
import org.junit.Test

class DevicesViewModelTest {

  @Test
  fun `aggregation merges categories for one physical device`() {
    val trainer = device(
      address = "AA:BB:CC:DD:EE:FF",
      name = "Zwift Hub",
      service = BleConstants.FTMS_SERVICE
    )
    val hrSensor = device(
      address = "AA:BB:CC:DD:EE:FF",
      name = "Zwift Hub",
      service = BleConstants.HEART_RATE_SERVICE
    )

    val aggregated = aggregateDevices(
      trainers = listOf(trainer),
      hrSensors = listOf(hrSensor)
    )

    assertEquals(1, aggregated.size)
    assertEquals("AA:BB:CC:DD:EE:FF", aggregated.single().device.address)
    assertEquals(
      setOf(DeviceCapability.TRAINER, DeviceCapability.HEART_RATE),
      aggregated.single().capabilities
    )
  }

  @Test
  fun `connecting aggregated device fans out to every applicable role`() {
    val device = device(
      address = "AA:BB:CC:DD:EE:FF",
      name = "Zwift Hub",
      service = BleConstants.FTMS_SERVICE
    )
    val aggregated = AggregatedDevice(
      device = device,
      capabilities = setOf(DeviceCapability.TRAINER, DeviceCapability.HEART_RATE)
    )
    val connectedRoles = mutableListOf<DeviceCapability>()

    connectAggregatedDevice(
      device = aggregated,
      connectTrainer = { connectedRoles += DeviceCapability.TRAINER },
      connectHr = { connectedRoles += DeviceCapability.HEART_RATE }
    )

    assertEquals(
      listOf(DeviceCapability.TRAINER, DeviceCapability.HEART_RATE),
      connectedRoles
    )
  }

  private fun device(address: String, name: String, service: java.util.UUID): BleDevice {
    return BleDevice(
      address = address,
      name = name,
      services = listOf(service),
      rssi = -40
    )
  }
}
