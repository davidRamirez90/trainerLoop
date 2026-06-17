package com.trainerloop.domain.fit

import com.trainerloop.data.model.TelemetrySample
import java.util.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
}
