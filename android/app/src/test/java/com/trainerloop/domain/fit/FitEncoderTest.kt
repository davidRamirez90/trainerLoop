package com.trainerloop.domain.fit

import com.trainerloop.data.model.TelemetrySample
import java.util.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FitEncoderTest {
  @Test
  fun `encode produces expected golden master bytes`() {
    val samples = listOf(
      TelemetrySample(0, 150, 85, 120),
      TelemetrySample(1, 200, 90, 130),
      TelemetrySample(2, 250, 95, 140)
    )

    val startTimeMs = utcMillis(2024, 0, 1, 12, 0, 0)
    val encoded = FitEncoder.encode(startTimeMs, elapsedSec = 3, samples = samples)

    val expected = loadExpectedBytes()
    assertArrayEquals(expected, encoded.toTypedArray())
  }

  @Test
  fun `encode produces valid FIT header`() {
    val encoded = FitEncoder.encode(
      startTimeMs = utcMillis(2024, 0, 1, 12, 0, 0),
      elapsedSec = 3,
      samples = listOf(TelemetrySample(0, 150, 85, 120))
    )

    assertEquals(14.toByte(), encoded[0])
    assertEquals(0x10.toByte(), encoded[1])
    assertEquals(0x2e.toByte(), encoded[8])
    assertEquals(0x46.toByte(), encoded[9])
    assertEquals(0x49.toByte(), encoded[10])
    assertEquals(0x54.toByte(), encoded[11])
  }

  @Test
  fun `session definition uses cadence and power profile field numbers`() {
    val bytes = FitEncoder.encode(
      startTimeMs = utcMillis(2024, 0, 1, 12, 0, 0),
      elapsedSec = 3,
      samples = listOf(TelemetrySample(0, 200, 90, 140))
    )
    val definition = (0 until bytes.size - 2).first { index ->
      (bytes[index].toInt() and 0xff) == 0x43 &&
        (bytes[index + 3].toInt() and 0xff) == 18 &&
        (bytes[index + 4].toInt() and 0xff) == 0
    }
    val fieldCount = bytes[definition + 5].toInt() and 0xff
    val fields = (0 until fieldCount).map { offset ->
      bytes[definition + 6 + offset * 3].toInt() and 0xff
    }
    assertEquals(listOf(253, 2, 5, 7, 8, 9, 18, 16, 17, 20, 21), fields)
  }

  @Test
  fun `trailing CRC covers header and data`() {
    val bytes = FitEncoder.encode(1_700_000_000_000L, 5, listOf(TelemetrySample(0, 200, 90, 140)))
    val expected = crc16(bytes.dropLast(2).map { it.toInt() and 0xff })
    val actual = (bytes[bytes.size - 2].toInt() and 0xff) or
      ((bytes[bytes.size - 1].toInt() and 0xff) shl 8)
    assertEquals(expected, actual)
  }

  @Test
  fun `virtual ride fields survive an encode decode round trip`() {
    val samples = (1..10).map { t ->
      TelemetrySample(
        timeSec = t, powerWatts = 200, cadenceRpm = 90, hrBpm = 140,
        virtualSpeedKph = 36.0,
        virtualDistanceM = t * 10.0,
        virtualAltitudeM = 100.0 + t,
        gradePercent = 2.5
      )
    }
    val bytes = FitEncoder.encode(startTimeMs = 1_700_000_000_000L, elapsedSec = 10, samples = samples)
    val decoded = FitDecoder.decode(bytes)
    val last = decoded.samples.last()
    assertEquals(36.0, last.virtualSpeedKph!!, 0.1)
    assertEquals(100.0, last.virtualDistanceM!!, 0.1)
    assertEquals(110.0, last.virtualAltitudeM!!, 0.3)
  }

  @Test
  fun `samples without virtual data decode with null virtual fields`() {
    val samples = (1..5).map { t ->
      TelemetrySample(timeSec = t, powerWatts = 200, cadenceRpm = 90, hrBpm = 140)
    }
    val bytes = FitEncoder.encode(1_700_000_000_000L, 5, samples)
    val decoded = FitDecoder.decode(bytes)
    assertNull(decoded.samples.last().virtualSpeedKph)
    assertNull(decoded.samples.last().virtualDistanceM)
    assertNull(decoded.samples.last().virtualAltitudeM)
  }

  @Test
  fun `gps position survives an encode decode round trip`() {
    val samples = (1..10).map { t ->
      TelemetrySample(
        timeSec = t, powerWatts = 200, cadenceRpm = 90, hrBpm = 140,
        virtualSpeedKph = 25.0, virtualDistanceM = t * 7.0, virtualAltitudeM = 500.0,
        positionLat = 47.05 + t * 0.0001,
        positionLon = -8.5 // negative longitude must survive (signed field)
      )
    }
    val bytes = FitEncoder.encode(1_700_000_000_000L, 10, samples)
    val decoded = FitDecoder.decode(bytes)
    val last = decoded.samples.last()
    assertEquals(47.051, last.positionLat!!, 1e-5)
    assertEquals(-8.5, last.positionLon!!, 1e-5)
  }

  @Test
  fun `samples without position decode with null position`() {
    val samples = (1..5).map { t ->
      TelemetrySample(timeSec = t, powerWatts = 200, cadenceRpm = 90, hrBpm = 140)
    }
    val decoded = FitDecoder.decode(FitEncoder.encode(1_700_000_000_000L, 5, samples))
    assertNull(decoded.samples.last().positionLat)
    assertNull(decoded.samples.last().positionLon)
  }

  private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long {
    val calendar = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    calendar.set(year, month, day, hour, minute, second)
    calendar.set(java.util.Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
  }

  private fun loadExpectedBytes(): Array<Byte> {
    val json = this::class.java.classLoader
      ?.getResourceAsStream("fit/expected-fit-bytes.json")
      ?.bufferedReader()
      ?.use { it.readText() }
      ?: throw IllegalStateException("Missing expected-fit-bytes.json")

    val array = Json.decodeFromString(JsonArray.serializer(), json)
    return array.map { it.jsonPrimitive.int.toByte() }.toTypedArray()
  }

  private fun crc16(bytes: List<Int>): Int {
    val table = intArrayOf(
      0x0000, 0xcc01, 0xd801, 0x1400, 0xf001, 0x3c00, 0x2800, 0xe401,
      0xa001, 0x6c00, 0x7800, 0xb401, 0x5000, 0x9c01, 0x8801, 0x4400
    )
    var crc = 0
    bytes.forEach { byte ->
      val value = byte and 0xff
      var tmp = table[crc and 0x0f]
      crc = (crc shr 4) and 0x0fff
      crc = crc xor tmp xor table[value and 0x0f]
      tmp = table[crc and 0x0f]
      crc = (crc shr 4) and 0x0fff
      crc = crc xor tmp xor table[(value shr 4) and 0x0f]
    }
    return crc and 0xffff
  }
}
