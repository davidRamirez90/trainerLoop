package com.trainerloop.data.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetrySampleTest {
  @Test
  fun `legacy samples use recorded time for chart position`() {
    val sample = Json.decodeFromString<TelemetrySample>(
      """{"timeSec":10,"powerWatts":200,"cadenceRpm":90,"hrBpm":150}"""
    )
    assertEquals(10, sample.workoutTimeSec)
    val skipped = sample.copy(planTimeSec = 300)
    assertEquals(300, Json.decodeFromString<TelemetrySample>(Json.encodeToString(skipped)).workoutTimeSec)
    assertEquals(10, skipped.timeSec)
  }

  @Test
  fun `sample round trips through json`() {
    val sample = TelemetrySample(10, 200, 90, 150)
    val json = Json.encodeToString(sample)
    val restored = Json.decodeFromString<TelemetrySample>(json)
    assertEquals(sample, restored)
  }
}
