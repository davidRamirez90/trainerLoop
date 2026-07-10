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
    val controller = device(
      address = "AA:BB:CC:DD:EE:FF",
      name = "Zwift Hub",
      service = BleConstants.ZWIFT_CLICK_SERVICE
    )

    val aggregated = aggregateDevices(
      trainers = listOf(trainer),
      hrSensors = emptyList(),
      controllers = listOf(controller)
    )

    assertEquals(1, aggregated.size)
    assertEquals("AA:BB:CC:DD:EE:FF", aggregated.single().device.address)
    assertEquals(
      setOf(DeviceCapability.TRAINER, DeviceCapability.CONTROLLER),
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
      capabilities = setOf(DeviceCapability.TRAINER, DeviceCapability.CONTROLLER)
    )
    val connectedRoles = mutableListOf<DeviceCapability>()

    connectAggregatedDevice(
      device = aggregated,
      connectTrainer = { connectedRoles += DeviceCapability.TRAINER },
      connectHr = { connectedRoles += DeviceCapability.HEART_RATE },
      connectClick = { connectedRoles += DeviceCapability.CONTROLLER }
    )

    assertEquals(
      listOf(DeviceCapability.TRAINER, DeviceCapability.CONTROLLER),
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
