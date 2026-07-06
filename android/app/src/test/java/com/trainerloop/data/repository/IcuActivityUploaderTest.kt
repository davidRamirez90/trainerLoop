package com.trainerloop.data.repository

import com.trainerloop.data.model.SessionData
import com.trainerloop.data.model.TelemetrySample
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IcuActivityUploaderTest {

  private fun session(id: String = "s1", samples: List<TelemetrySample>): SessionData {
    val json = Json.encodeToString(ListSerializer(TelemetrySample.serializer()), samples)
    return SessionData(
      id = id,
      workoutId = "w1",
      workoutName = "Sweet Spot",
      startedAt = "2026-07-06T09:00:00Z",
      endedAt = "2026-07-06T10:00:00Z",
      durationSec = 3600,
      samplesJson = json,
      coachEventsJson = "",
      completed = true,
      avgPower = 200,
      maxPower = 300,
      avgCadence = 90,
      avgHr = 140
    )
  }

  private fun repo() = SessionRepository(FakeSessionDao())

  private val someSamples = listOf(
    TelemetrySample(timeSec = 0, powerWatts = 200, cadenceRpm = 90, hrBpm = 140),
    TelemetrySample(timeSec = 1, powerWatts = 210, cadenceRpm = 91, hrBpm = 141)
  )

  @Test
  fun `successful upload marks session synced with timestamp`() = runTest {
    val repository = repo()
    val data = session(samples = someSamples)
    repository.save(data)
    var uploadedName: String? = null
    val uploader = IcuActivityUploader(
      sessionRepository = repository,
      upload = { bytes, name ->
        assertTrue(bytes.isNotEmpty())
        uploadedName = name
        true
      },
      nowIso = { "2026-07-06T11:00:00Z" }
    )

    assertTrue(uploader.uploadSession(data))
    assertEquals("Sweet Spot", uploadedName)
    assertEquals("2026-07-06T11:00:00Z", repository.getById("s1")!!.icuSyncedAt)
  }

  @Test
  fun `failed upload leaves session unsynced`() = runTest {
    val repository = repo()
    val data = session(samples = someSamples)
    repository.save(data)
    val uploader = IcuActivityUploader(repository, upload = { _, _ -> false })

    assertFalse(uploader.uploadSession(data))
    assertNull(repository.getById("s1")!!.icuSyncedAt)
  }

  @Test
  fun `upload exception is caught and leaves session unsynced`() = runTest {
    val repository = repo()
    val data = session(samples = someSamples)
    repository.save(data)
    val uploader = IcuActivityUploader(repository, upload = { _, _ -> throw RuntimeException("offline") })

    assertFalse(uploader.uploadSession(data))
    assertNull(repository.getById("s1")!!.icuSyncedAt)
  }

  @Test
  fun `session with no samples is not uploaded`() = runTest {
    val repository = repo()
    val data = session(samples = emptyList())
    repository.save(data)
    var called = false
    val uploader = IcuActivityUploader(repository, upload = { _, _ -> called = true; true })

    assertFalse(uploader.uploadSession(data))
    assertFalse(called)
  }
}
