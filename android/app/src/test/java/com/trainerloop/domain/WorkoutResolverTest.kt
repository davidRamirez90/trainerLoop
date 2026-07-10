package com.trainerloop.domain

import com.trainerloop.ui.library.BuiltInWorkouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutResolverTest {

  @Test
  fun `resolves built-in by id`() {
    val w = WorkoutResolver.resolve("sweet_spot", ftp = 200, imported = emptyList())
    assertEquals("sweet_spot", w?.id)
  }

  @Test
  fun `resolves imported by id`() {
    val custom = BuiltInWorkouts.all().first().copy(id = "custom_1")
    assertEquals("custom_1", WorkoutResolver.resolve("custom_1", 200, listOf(custom))?.id)
  }

  @Test
  fun `free ride id generates an open-ended free ride workout`() {
    val w = WorkoutResolver.resolve(WorkoutResolver.FREE_RIDE_ID, 200, emptyList())
    assertTrue(w!!.segments.all { it is com.trainerloop.data.model.WorkoutSegment.FreeRide })
  }

  @Test
  fun `ramp test id regenerates from ftp`() {
    val ramp = RampTest.generate(220)
    assertEquals(ramp.segments.size, WorkoutResolver.resolve(ramp.id, 220, emptyList())?.segments?.size)
  }

  @Test
  fun `unknown id returns null`() {
    assertNull(WorkoutResolver.resolve("nope", 200, emptyList()))
  }
}
