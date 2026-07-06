package com.trainerloop.domain.sim

import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.Workout
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.data.model.WorkoutSource
import kotlin.math.abs
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteGeneratorTest {
  private val params = PhysicsParams(riderKg = 75.0)

  private fun step(id: String, sec: Int, watts: Int, phase: SegmentPhase) =
    WorkoutSegment.Step(
      id = id, durationSec = sec, label = null, phase = phase,
      isWork = phase == SegmentPhase.WORK, targetRange = TargetRange(watts, watts)
    )

  private fun workout(id: String = "w1") = Workout(
    id = id, name = "Test", description = null, source = WorkoutSource.MANUAL,
    segments = listOf(
      step("warm", 300, 150, SegmentPhase.WARMUP),
      step("vo2", 300, 300, SegmentPhase.WORK),
      step("rec", 300, 100, SegmentPhase.RECOVERY),
      step("cool", 300, 130, SegmentPhase.COOLDOWN)
    )
  )

  @Test
  fun `same workout id generates the same route`() {
    val a = RouteGenerator.generate(workout(), ftp = 250, params = params)
    val b = RouteGenerator.generate(workout(), ftp = 250, params = params)
    assertArrayEquals(a.gradePercent, b.gradePercent, 1e-12)
  }

  @Test
  fun `different workout ids generate different routes`() {
    val a = RouteGenerator.generate(workout("w1"), 250, params)
    val b = RouteGenerator.generate(workout("w2"), 250, params)
    assertTrue(!a.gradePercent.contentEquals(b.gradePercent))
  }

  @Test
  fun `one grade point per second`() {
    val route = RouteGenerator.generate(workout(), 250, params)
    assertEquals(1200, route.gradePercent.size)
    assertEquals(1200, route.expectedAltitudeM.size)
  }

  @Test
  fun `grades stay within sane bounds`() {
    val route = RouteGenerator.generate(workout(), 250, params)
    assertTrue(route.gradePercent.all { it in -4.0..9.0 })
  }

  @Test
  fun `grade changes are smooth`() {
    val route = RouteGenerator.generate(workout(), 250, params)
    val maxDelta = route.gradePercent.toList().zipWithNext { a, b -> abs(b - a) }.max()
    assertTrue("max per-second grade delta $maxDelta too steep", maxDelta <= 1.5)
  }

  @Test
  fun `hard intervals climb and recoveries descend`() {
    val route = RouteGenerator.generate(workout(), 250, params)
    // Sample well inside each segment so boundary smoothing doesn't blur it.
    val vo2Grade = route.gradePercent.slice(450..550).average()   // 300 W = 120% FTP
    val recGrade = route.gradePercent.slice(750..850).average()   // 100 W = 40% FTP
    assertTrue("vo2 $vo2Grade should be a climb", vo2Grade > 2.0)
    assertTrue("recovery $recGrade should descend", recGrade < 0.0)
  }

  @Test
  fun `gradeAt clamps out of range lookups`() {
    val route = RouteGenerator.generate(workout(), 250, params)
    assertEquals(route.gradePercent.first(), route.gradeAt(-5), 1e-12)
    assertEquals(route.gradePercent.last(), route.gradeAt(99999), 1e-12)
  }

  @Test
  fun `empty workout yields empty route and zero grade`() {
    val empty = Workout("e", "Empty", null, WorkoutSource.MANUAL, emptyList())
    val route = RouteGenerator.generate(empty, 250, params)
    assertEquals(0, route.gradePercent.size)
    assertEquals(0.0, route.gradeAt(10), 1e-12)
  }
}
