package com.trainerloop.data.repository

import app.cash.turbine.test
import com.trainerloop.data.model.SessionData
import com.trainerloop.data.model.SessionSummary
import com.trainerloop.data.source.local.SessionDao
import com.trainerloop.data.source.local.SessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Fast JVM unit tests for [SessionRepository] that use a fake [SessionDao].
 *
 * The real Room/SQLite path is exercised by Robolectric/instrumentation tests
 * in a later phase; here we verify the repository's mapping, flow emission,
 * and CRUD delegation logic.
 */
class SessionRepositoryTest {

  @Test
  fun `save and read back a session by id`() = runTest {
    val dao = FakeSessionDao()
    val repository = SessionRepository(dao)
    val data = sampleSession(id = "s1")

    repository.save(data)

    val loaded = repository.getById("s1")
    assertNotNull(loaded)
    assertEquals(data.id, loaded!!.id)
    assertEquals(data.workoutName, loaded.workoutName)
    assertEquals(data.samplesJson, loaded.samplesJson)
    assertEquals(data.coachEventsJson, loaded.coachEventsJson)
    assertEquals(data.completed, loaded.completed)
    assertEquals(data.avgPower, loaded.avgPower)
    assertEquals(data.maxPower, loaded.maxPower)
  }

  @Test
  fun `summaries exposes inserted sessions without json payloads`() = runTest {
    val dao = FakeSessionDao()
    val repository = SessionRepository(dao)
    repository.save(sampleSession(id = "a", workoutName = "Alpha"))
    repository.save(sampleSession(id = "b", workoutName = "Beta"))

    repository.summaries().test {
      val items = awaitItem()
      assertEquals(2, items.size)
      assertEquals(setOf("Alpha", "Beta"), items.map { it.workoutName }.toSet())
      // Summaries are lightweight — samplesJson / coachEventsJson are not exposed.
      items.forEach { _: SessionSummary -> }
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `getById returns null when missing`() = runTest {
    val repository = SessionRepository(FakeSessionDao())
    assertNull(repository.getById("missing"))
  }

  @Test
  fun `deleteById removes the session`() = runTest {
    val dao = FakeSessionDao()
    val repository = SessionRepository(dao)
    repository.save(sampleSession(id = "del"))

    repository.deleteById("del")
    assertNull(repository.getById("del"))
  }

  private fun sampleSession(
    id: String,
    workoutName: String = "Test Workout"
  ): SessionData = SessionData(
    id = id,
    workoutId = "workout-1",
    workoutName = workoutName,
    startedAt = "2026-06-17T10:00:00Z",
    endedAt = "2026-06-17T10:30:00Z",
    durationSec = 1800,
    samplesJson = "[]",
    coachEventsJson = "[]",
    completed = true,
    avgPower = 200,
    maxPower = 350,
    avgCadence = 90,
    avgHr = 150
  )
}

private class FakeSessionDao : SessionDao {
  private val rows = MutableStateFlow<List<SessionEntity>>(emptyList())

  override fun getAll(): Flow<List<SessionEntity>> = rows

  override suspend fun insert(entity: SessionEntity) {
    rows.value = rows.value
      .filterNot { it.id == entity.id } + entity
  }

  override suspend fun getById(id: String): SessionEntity? =
    rows.value.firstOrNull { it.id == id }

  override suspend fun delete(entity: SessionEntity) {
    rows.value = rows.value.filterNot { it.id == entity.id }
  }

  override suspend fun deleteById(id: String) {
    rows.value = rows.value.filterNot { it.id == id }
  }
}
