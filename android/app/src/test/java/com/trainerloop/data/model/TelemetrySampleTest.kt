package com.trainerloop.data.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetrySampleTest {
  @Test
  fun `sample round trips through json`() {
    val sample = TelemetrySample(10, 200, 90, 150)
    val json = Json.encodeToString(sample)
    val restored = Json.decodeFromString<TelemetrySample>(json)
    assertEquals(sample, restored)
  }
}
