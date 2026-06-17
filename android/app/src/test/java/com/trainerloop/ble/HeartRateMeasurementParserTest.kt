package com.trainerloop.ble

import com.trainerloop.ble.model.HeartRateMeasurementParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateMeasurementParserTest {

  @Test
  fun `null for short data`() {
    assertNull(HeartRateMeasurementParser.parse(byteArrayOf(0x00)))
  }

  @Test
  fun `parses uint8 heart rate`() {
    // Flags: 0x00 (uint8 format)
    // HR value: 0x5A = 90 bpm
    val data = byteArrayOf(0x00, 0x5A.toByte())
    assertEquals(90, HeartRateMeasurementParser.parse(data))
  }

  @Test
  fun `parses uint16 heart rate`() {
    // Flags: 0x01 (uint16 format)
    // HR value: 0x00C8 = 200 bpm (little endian)
    val data = byteArrayOf(0x01, 0xC8.toByte(), 0x00)
    assertEquals(200, HeartRateMeasurementParser.parse(data))
  }

  @Test
  fun `null for uint16 data missing bytes`() {
    // Flags say uint16, but only 2 bytes total
    val data = byteArrayOf(0x01, 0x5A.toByte())
    assertNull(HeartRateMeasurementParser.parse(data))
  }

  @Test
  fun `handles all flags set`() {
    // Flags: 0x1F (bit0=uint16, bit1-4 all set)
    val data = byteArrayOf(0x1F, 0x78.toByte(), 0x00)
    assertEquals(120, HeartRateMeasurementParser.parse(data))
  }
}
