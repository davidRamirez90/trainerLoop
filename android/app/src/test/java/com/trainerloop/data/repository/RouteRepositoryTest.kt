package com.trainerloop.data.repository

import com.trainerloop.data.model.Route
import com.trainerloop.data.model.RoutePoint
import com.trainerloop.data.source.local.RouteDao
import com.trainerloop.data.source.local.RouteEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class FakeRouteDao : RouteDao {
  private val rows = MutableStateFlow<List<RouteEntity>>(emptyList())
  override suspend fun insert(entity: RouteEntity) {
    rows.value = rows.value.filter { it.id != entity.id } + entity
  }
  override fun getAll() = rows
  override suspend fun getById(id: String) = rows.value.firstOrNull { it.id == id }
  override suspend fun deleteById(id: String) {
    rows.value = rows.value.filter { it.id != id }
  }
}

class RouteRepositoryTest {

  private fun route() = Route("Alpe", List(11) { i ->
    RoutePoint(i * 10.0, 47.0 + i * 0.0001, 8.0, 500.0 + i, 1.0)
  })

  @Test
  fun `save and load round trips the route`() = runTest {
    val repo = RouteRepository(FakeRouteDao())
    val id = repo.save(route())
    val loaded = repo.getById(id)!!
    assertEquals("Alpe", loaded.name)
    assertEquals(11, loaded.points.size)
    assertEquals(47.0005, loaded.points[5].lat, 1e-9)
    assertEquals(100.0, loaded.totalDistanceM, 1e-9)
  }

  @Test
  fun `summaries carry name distance and ascent`() = runTest {
    val repo = RouteRepository(FakeRouteDao())
    repo.save(route())
    val summary = repo.summaries().first().single()
    assertEquals("Alpe", summary.name)
    assertEquals(100.0, summary.distanceM, 1e-9)
    assertEquals(10, summary.ascentM)
  }

  @Test
  fun `unnamed route falls back to the provided filename`() = runTest {
    val repo = RouteRepository(FakeRouteDao())
    repo.save(Route(null, route().points), fallbackName = "morning_ride")
    assertEquals("morning_ride", repo.summaries().first().single().name)
  }

  @Test
  fun `delete removes the route`() = runTest {
    val repo = RouteRepository(FakeRouteDao())
    val id = repo.save(route())
    repo.deleteById(id)
    assertNull(repo.getById(id))
  }
}
