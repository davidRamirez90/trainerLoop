package com.trainerloop.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class FtmsControlManagerTest {

  @Test
  fun `requestControl builds correct payload`() {
    val payload = FtmsCommands.requestControl()
    assertArrayEquals(byteArrayOf(0x00), payload)
  }

  @Test
  fun `startResume builds correct payload`() {
    val payload = FtmsCommands.startResume()
    assertArrayEquals(byteArrayOf(0x07), payload)
  }

  @Test
  fun `stopPause builds stop payload`() {
    val payload = FtmsCommands.stopPause(stop = true)
    assertArrayEquals(byteArrayOf(0x08, 0x01), payload)
  }

  @Test
  fun `stopPause builds pause payload`() {
    val payload = FtmsCommands.stopPause(stop = false)
    assertArrayEquals(byteArrayOf(0x08, 0x02), payload)
  }

  @Test
  fun `setTargetPower builds 0 watt payload`() {
    val payload = FtmsCommands.setTargetPower(0)
    assertArrayEquals(byteArrayOf(0x05, 0x00, 0x00), payload)
  }

  @Test
  fun `setTargetPower builds 200 watt payload`() {
    val payload = FtmsCommands.setTargetPower(200)
    assertArrayEquals(byteArrayOf(0x05, 0xC8.toByte(), 0x00), payload)
  }

  @Test
  fun `setTargetPower builds negative watt payload clamped to 0`() {
    val payload = FtmsCommands.setTargetPower(-50)
    assertArrayEquals(byteArrayOf(0x05, 0x00, 0x00), payload)
  }

  @Test
  fun `setTargetPower builds oversized watt payload clamped to 2000`() {
    val payload = FtmsCommands.setTargetPower(2500)
    // 2000 = 0x07D0
    assertArrayEquals(byteArrayOf(0x05, 0xD0.toByte(), 0x07), payload)
  }

  @Test
  fun `setTargetPower builds max value 2000`() {
    val payload = FtmsCommands.setTargetPower(2000)
    // 2000 = 0x07D0
    assertArrayEquals(byteArrayOf(0x05, 0xD0.toByte(), 0x07), payload)
  }
}
