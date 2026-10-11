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
  fun `ramp test id regenerates from ftp`() {
    val ramp = RampTest.generate(220)
    assertEquals(ramp.segments.size, WorkoutResolver.resolve(ramp.id, 220, emptyList())?.segments?.size)
  }

  @Test
  fun `retired standalone free ride cannot be resolved`() {
    assertNull(WorkoutResolver.resolve("free-ride", 250, emptyList()))
  }

  @Test
  fun `built-in power targets scale with FTP and endurance is structured`() {
    val sweetSpot = WorkoutResolver.resolve("sweet_spot", 200, emptyList())!!
    val step = sweetSpot.segments[1] as com.trainerloop.data.model.WorkoutSegment.Step
    assertEquals(com.trainerloop.data.model.TargetRange(176, 184), step.targetRange)
    val endurance = WorkoutResolver.resolve("endurance", 200, emptyList())!!
    assertTrue(endurance.segments.any { it is com.trainerloop.data.model.WorkoutSegment.Step })
  }

  @Test
  fun `unknown id returns null`() {
    assertNull(WorkoutResolver.resolve("nope", 200, emptyList()))
  }
}
