package com.trainerloop.domain.sim

import com.trainerloop.data.model.Route
import com.trainerloop.data.model.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeRideTrackerTest {
  private val params = PhysicsParams(riderKg = 75.0)

  /** Straight 1 km route on a uniform grade, 10 m grid heading north. */
  private fun route(gradePct: Double, lengthM: Double = 1000.0): Route {
    val count = (lengthM / Route.GRID_M).toInt() + 1
    return Route("test", List(count) { i ->
      RoutePoint(
        distanceM = i * Route.GRID_M,
        lat = 47.0 + i * 0.0001,
        lon = 8.0,
        elevationM = 500.0 + i * Route.GRID_M * gradePct / 100.0,
        gradePercent = gradePct
      )
    })
  }

  private fun ride(tracker: FreeRideTracker, from: Int, to: Int, cadence: Int): FreeRideTracker.FreeRidePoint {
    var p = tracker.onTick(from, cadence)
    for (t in (from + 1)..to) p = tracker.onTick(t, cadence)
    return p
  }

  @Test
  fun `distance accumulates and position moves along the track`() {
    val tracker = FreeRideTracker(route(0.0), params)
    val p = ride(tracker, 0, 120, cadence = 90)
    assertTrue("distance ${p.distanceM}", p.distanceM > 400.0)
    assertTrue("lat should advance north, got ${p.lat}", p.lat > 47.0)
    assertEquals(8.0, p.lon, 1e-9)
    assertFalse(p.routeComplete)
  }

  @Test
  fun `repeated ticks for the same second do not double integrate`() {
    val tracker = FreeRideTracker(route(0.0), params)
    ride(tracker, 0, 10, cadence = 90)
    val a = tracker.onTick(10, 90)
    val b = tracker.onTick(10, 90)
    assertEquals(a.distanceM, b.distanceM, 1e-9)
  }

  @Test
  fun `climbing needs more target power than flat at same cadence`() {
    val flat = ride(FreeRideTracker(route(0.0), params), 0, 30, cadence = 85)
    val climb = ride(FreeRideTracker(route(6.0), params), 0, 30, cadence = 85)
    assertTrue(
      "climb ${climb.targetPowerWatts}W vs flat ${flat.targetPowerWatts}W",
      climb.targetPowerWatts > flat.targetPowerWatts + 50
    )
  }

  @Test
  fun `difficulty scales target power but not speed or altitude`() {
    val full = ride(FreeRideTracker(route(6.0), params, difficulty = 1.0), 0, 30, cadence = 85)
    val half = ride(FreeRideTracker(route(6.0), params, difficulty = 0.5), 0, 30, cadence = 85)
    assertTrue(half.targetPowerWatts < full.targetPowerWatts)
    assertEquals(full.speedKph, half.speedKph, 1e-6)
    assertEquals(full.altitudeM, half.altitudeM, 1e-6)
    assertEquals(full.gradePercent, half.gradePercent, 1e-9)
  }

  @Test
  fun `no pedaling on a climb floors target at zero and stops advancing`() {
    val tracker = FreeRideTracker(route(5.0), params)
    val p = ride(tracker, 0, 30, cadence = 0)
    assertEquals(0, p.targetPowerWatts)
    assertEquals(0.0, p.distanceM, 1.0)
  }

  @Test
  fun `route completes at the end and holds zero grade`() {
    val tracker = FreeRideTracker(route(0.0, lengthM = 100.0), params)
    val p = ride(tracker, 0, 120, cadence = 95)
    assertTrue(p.routeComplete)
    assertEquals(100.0, p.distanceM, 1e-6)
    assertEquals(0.0, p.gradePercent, 1e-9)
  }

  @Test
  fun `stamp adapts onTick for the recorder`() {
    val tracker = FreeRideTracker(route(2.0), params)
    for (t in 0..20) tracker.stamp(t, powerWatts = 200, cadenceRpm = 90, dropout = false)
    val stamp = tracker.stamp(21, 200, 90, false)
    assertTrue(stamp.distanceM > 0.0)
    assertTrue(stamp.lat != null && stamp.lat!! > 47.0)
    assertEquals(2.0, stamp.gradePercent, 1e-9)
  }
}
