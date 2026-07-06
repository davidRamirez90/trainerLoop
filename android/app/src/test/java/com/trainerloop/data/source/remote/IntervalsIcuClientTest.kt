package com.trainerloop.data.source.remote

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class IntervalsIcuClientTest {

  @Test
  fun `parses events response`() {
    val body = """
      [
        {"id": 123, "name": "Sweet Spot Intervals", "category": "WORKOUT"},
        {"id": 456, "category": "WORKOUT"}
      ]
    """.trimIndent()

    val events = Json { ignoreUnknownKeys = true }
      .decodeFromString(ListSerializer(IntervalsIcuEvent.serializer()), body)

    assertEquals(2, events.size)
    assertEquals(123L, events[0].id)
    assertEquals("Sweet Spot Intervals", events[0].name)
    assertEquals(456L, events[1].id)
    assertEquals(null, events[1].name)
  }

  @Test
  fun `parses athlete response`() {
    val body = """{"ftp": 250, "icu_weight": 74.5, "extra_unused_field": "x"}"""

    val athlete = Json { ignoreUnknownKeys = true }
      .decodeFromString(IntervalsIcuAthlete.serializer(), body)

    assertEquals(250, athlete.ftp)
    assertEquals(74.5, athlete.icu_weight!!, 0.001)
  }

  @Test
  fun `upload accepted on 2xx and on 422 duplicate`() {
    org.junit.Assert.assertTrue(IntervalsIcuClient.isUploadAccepted(200, ""))
    org.junit.Assert.assertTrue(IntervalsIcuClient.isUploadAccepted(201, "{}"))
    org.junit.Assert.assertTrue(
      IntervalsIcuClient.isUploadAccepted(422, """{"error":"Duplicate of activity i12345"}""")
    )
    org.junit.Assert.assertFalse(IntervalsIcuClient.isUploadAccepted(422, """{"error":"Invalid file"}"""))
    org.junit.Assert.assertFalse(IntervalsIcuClient.isUploadAccepted(401, "unauthorized"))
    org.junit.Assert.assertFalse(IntervalsIcuClient.isUploadAccepted(500, "boom"))
  }
}
