package com.trainerloop.ble

import com.trainerloop.ble.model.IndoorBikeDataParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class IndoorBikeDataParserTest {

  @Test
  fun `null for empty data`() {
    assertNull(IndoorBikeDataParser.parse(byteArrayOf()))
  }

  @Test
  fun `null for single byte`() {
    assertNull(IndoorBikeDataParser.parse(byteArrayOf(0x00)))
  }

  @Test
  fun `parses power and cadence`() {
    // Flags: bit 2 (cadence) + bit 6 (power) = 0x44
    // Speed: 3000 = 30.00 km/h
    // Cadence: 170 / 2 = 85 rpm
    // Power: 200 W
    val data = byteArrayOf(
      0x44, 0x00,              // flags
      0xB8.toByte(), 0x0B,     // speed 3000
      0xAA.toByte(), 0x00,     // cadence 170
      0xC8.toByte(), 0x00      // power 200
    )
    val result = IndoorBikeDataParser.parse(data)
    assertNotNull(result)
    assertEquals(200, result!!.powerWatts)
    assertEquals(85.0, result.cadenceRpm!!, 0.01)
    assertEquals(30.0, result.speedKph!!, 0.01)
  }

  @Test
  fun `parses minimal data with only speed`() {
    // Flags: 0x00 (no optional fields)
    // Only mandatory speed
    val data = byteArrayOf(
      0x00, 0x00,              // flags
      0xE8.toByte(), 0x03      // speed 1000 = 10.00 km/h
    )
    val result = IndoorBikeDataParser.parse(data)
    assertNotNull(result)
    assertEquals(10.0, result!!.speedKph!!, 0.01)
    assertNull(result.powerWatts)
    assertNull(result.cadenceRpm)
  }

  @Test
  fun `parses all fields`() {
    // All optional fields present
    val flags = (1 shl 1) or (1 shl 2) or (1 shl 3) or (1 shl 4) or
      (1 shl 5) or (1 shl 6) or (1 shl 7) or (1 shl 8) or
      (1 shl 9) or (1 shl 10) or (1 shl 11) or (1 shl 12)
    val data = byteArrayOf(
      flags.toByte(), (flags shr 8).toByte(), // flags
      0x90.toByte(), 0x01,        // speed 400 = 4.00 km/h
      0x00, 0x00,                 // avg speed 0
      0x8C.toByte(), 0x00,        // cadence 140 / 2 = 70 rpm
      0x8C.toByte(), 0x00,        // avg cadence 70 rpm
      0x00, 0x01, 0x00,           // distance 256 m (uint24 LE)
      0x0A, 0x00,                 // resistance 10 (raw, 0.1 res = 1.0 -> rounds to 1)
      0xF4.toByte(), 0x01,        // power 500 W
      0xF4.toByte(), 0x01,        // avg power 500 W
      0x00, 0x00, 0x00, 0x00, 0x00, // expended energy
      0x5A.toByte(),              // HR 90 bpm
      0x05,                       // MET 5 (0.5 MET)
      0xB4.toByte(), 0x00,        // elapsed 180 sec
      0x3C.toByte(), 0x00         // remaining 60 sec
    )
    val result = IndoorBikeDataParser.parse(data)
    assertNotNull(result)
    assertEquals(500, result!!.powerWatts)
    assertEquals(70.0, result.cadenceRpm!!, 0.01)
    assertEquals(4.0, result.speedKph!!, 0.01)
    assertEquals(1, result.resistanceLevel)
    assertEquals(500, result.averagePower)
    assertEquals(0.0, result.averageSpeed!!, 0.01)
    assertEquals(256, result.totalDistanceMeters)
    assertEquals(90, result.heartRateBpm)
    assertEquals(180, result.elapsedTimeSec)
    assertEquals(60, result.remainingTimeSec)
  }

  @Test
  fun `negative power is supported`() {
    // Flags: bit 6 (power) = 0x40
    val flags = 1 shl 6
    val data = byteArrayOf(
      flags.toByte(), 0x00,       // flags
      0x00, 0x00,                 // speed 0
      0xFE.toByte(), 0xFF.toByte() // power -2 (int16 LE)
    )
    val result = IndoorBikeDataParser.parse(data)
    assertNotNull(result)
    assertEquals(-2, result!!.powerWatts)
  }

  @Test
  fun `returns null when buffer too short for flagged fields`() {
    val flags = 1 shl 6 // 0x40 says power present, but no power bytes follow
    val data = byteArrayOf(
      flags.toByte(), 0x00, // flags
      0x00, 0x00            // speed only, missing power
    )
    assertNull(IndoorBikeDataParser.parse(data))
  }

  @Test
  fun `rounds resistance level correctly`() {
    // Resistance raw value 15 (= 1.5) should round to 2
    val flags = 1 shl 5 // 0x20
    val data = byteArrayOf(
      flags.toByte(), 0x00,       // flags
      0x00, 0x00,                 // speed 0
      0x0F, 0x00                  // resistance 15 (0.1 res = 1.5 -> rounds to 2)
    )
    val result = IndoorBikeDataParser.parse(data)
    assertNotNull(result)
    assertEquals(2, result!!.resistanceLevel)
  }

  @Test
  fun `more data flag omits speed before optional fields`() {
    val flags = (1 shl 0) or (1 shl 5) or (1 shl 7)
    val data = byteArrayOf(
      flags.toByte(), 0x00,
      0xF1.toByte(), 0xFF.toByte(), // resistance -15 -> -2
      0xFB.toByte(), 0xFF.toByte()  // average power -5
    )
    val result = IndoorBikeDataParser.parse(data)
    assertNotNull(result)
    assertNull(result!!.speedKph)
    assertEquals(-2, result.resistanceLevel)
    assertEquals(-5, result.averagePower)
  }
}
