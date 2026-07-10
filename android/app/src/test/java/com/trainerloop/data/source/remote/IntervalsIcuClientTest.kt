package com.trainerloop.data.source.remote

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.coroutines.runBlocking
import com.trainerloop.domain.WorkoutNameCodec
import java.net.ServerSocket
import kotlin.concurrent.thread

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
  fun `decodes ICU form name once and preserves a literal plus in a normal name`() {
    assertEquals(
      "Z2 Endurance   1x8m SS primer + 100%",
      WorkoutNameCodec.decodeIcuName("Z2+Endurance+++1x8m+SS+primer+%2B+100%25")
    )
    assertEquals(
      "Ride + 100% FTP",
      WorkoutNameCodec.decodeIcuName("Ride + 100% FTP")
    )
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

  @Test
  fun `GET failures throw typed HTTP exception`() = runBlocking {
    val server = ServerSocket(0)
    val serverThread = thread {
      server.accept().use { socket ->
        val input = socket.getInputStream().bufferedReader()
        while (input.readLine().orEmpty().isNotEmpty()) { }
        val body = "unauthorized"
        socket.getOutputStream().bufferedWriter().use { output ->
          output.write("HTTP/1.1 401 Unauthorized\r\nContent-Length: ${body.length}\r\n\r\n$body")
          output.flush()
        }
      }
    }
    try {
      val client = IntervalsIcuClient("key", "http://127.0.0.1:${server.localPort}")
      try {
        client.getAthlete("a")
        org.junit.Assert.fail("Expected HTTP failure")
      } catch (e: IntervalsIcuHttpException) {
        assertEquals(401, e.statusCode)
        assertEquals("unauthorized", e.responseBody)
      }
    } finally {
      server.close()
      serverThread.join(1000)
    }
  }
}
