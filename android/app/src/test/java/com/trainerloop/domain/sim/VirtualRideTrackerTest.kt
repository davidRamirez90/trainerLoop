package com.trainerloop.domain.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualRideTrackerTest {
  private val params = PhysicsParams(riderKg = 75.0)

  /** 600 s of dead-flat route so speed is predictable. */
  private fun flatRoute() = RouteProfile(DoubleArray(600), DoubleArray(600))

  @Test
  fun `distance accumulates at physics speed`() {
    val tracker = VirtualRideTracker(flatRoute(), params)
    var last = tracker.onTick(0, 250, dropout = false)
    for (t in 1..60) last = tracker.onTick(t, 250, dropout = false)
    val expectedV = VirtualSpeed.speedMps(250, 0.0, params)
    assertEquals(expectedV * 60, last.distanceM, 1.0)
    assertEquals(expectedV * 3.6, last.speedKph, 0.1)
    assertEquals(0.0, last.altitudeM, 1e-9)
  }

  @Test
  fun `repeated ticks for the same second do not double integrate`() {
    val tracker = VirtualRideTracker(flatRoute(), params)
    tracker.onTick(1, 250, dropout = false)
    val a = tracker.onTick(5, 250, dropout = false)
    val b = tracker.onTick(5, 250, dropout = false)
    assertEquals(a.distanceM, b.distanceM, 1e-9)
  }

  @Test
  fun `seek forward adds at most one second of distance`() {
    val tracker = VirtualRideTracker(flatRoute(), params)
    tracker.onTick(10, 250, dropout = false)
    val before = tracker.onTick(10, 250, dropout = false).distanceM
    val after = tracker.onTick(400, 250, dropout = false).distanceM
    val v = VirtualSpeed.speedMps(250, 0.0, params)
    assertTrue(after - before <= v + 1e-9)
  }

  @Test
  fun `dropout holds previous speed`() {
    val tracker = VirtualRideTracker(flatRoute(), params)
    for (t in 0..10) tracker.onTick(t, 250, dropout = false)
    val point = tracker.onTick(11, 0, dropout = true)
    assertEquals(VirtualSpeed.speedMps(250, 0.0, params) * 3.6, point.speedKph, 0.1)
  }

  @Test
  fun `climbing gains altitude`() {
    val route = RouteProfile(DoubleArray(600) { 5.0 }, DoubleArray(600))
    val tracker = VirtualRideTracker(route, params)
    var last = tracker.onTick(0, 250, dropout = false)
    for (t in 1..60) last = tracker.onTick(t, 250, dropout = false)
    assertTrue("altitude should rise on a 5% climb, got ${last.altitudeM}", last.altitudeM > 5.0)
    assertEquals(5.0, last.gradePercent, 1e-9)
  }
}
