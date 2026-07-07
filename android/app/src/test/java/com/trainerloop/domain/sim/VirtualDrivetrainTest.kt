package com.trainerloop.domain.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualDrivetrainTest {
  private val params = PhysicsParams(riderKg = 75.0)

  /** Runs enough constant-cadence ticks for the EMA to converge. */
  private fun settled(dt: VirtualDrivetrain, cadence: Int, grade: Double): Double {
    var v = 0.0
    repeat(30) { v = dt.tick(cadence, grade) }
    return v
  }

  @Test
  fun `gear table is strictly increasing from 1 to 4point6`() {
    assertEquals(14, VirtualDrivetrain.RATIOS.size)
    assertEquals(1.0, VirtualDrivetrain.RATIOS.first(), 1e-9)
    assertEquals(4.6, VirtualDrivetrain.RATIOS.last(), 1e-9)
    VirtualDrivetrain.RATIOS.toList().zipWithNext().forEach { (a, b) -> assertTrue(b > a) }
  }

  @Test
  fun `90 rpm in start gear is a plausible speed`() {
    val dt = VirtualDrivetrain(params)
    assertEquals(7, dt.gear)
    val v = settled(dt, 90, 0.0)
    // ratio ~2.0 -> ~6.3 m/s -> ~23 km/h
    assertTrue("expected ~6.3 m/s, got $v", v in 5.0..8.0)
  }

  @Test
  fun `shifting up raises speed at constant cadence`() {
    val dt = VirtualDrivetrain(params)
    val before = settled(dt, 90, 0.0)
    dt.shiftUp()
    val after = settled(dt, 90, 0.0)
    assertTrue(after > before)
  }

  @Test
  fun `shift clamps at 1 and 14`() {
    val dt = VirtualDrivetrain(params)
    repeat(30) { dt.shiftDown() }
    assertEquals(1, dt.gear)
    repeat(30) { dt.shiftUp() }
    assertEquals(14, dt.gear)
  }

  @Test
  fun `cadence changes are smoothed not instant`() {
    val dt = VirtualDrivetrain(params)
    settled(dt, 90, 0.0)
    val v1 = dt.tick(0, 0.0) // cadence drops to zero
    assertTrue("one tick after dropout speed should not be 0, got $v1", v1 > 1.0)
    val vLater = settled(dt, 0, 0.0)
    assertEquals("EMA decays to standstill on the flat", 0.0, vLater, 0.3)
  }

  @Test
  fun `freewheel floor wins on steep descents at low cadence`() {
    val dt = VirtualDrivetrain(params)
    repeat(6) { dt.shiftDown() } // gear 1
    val v = settled(dt, 30, -8.0)
    val coast = VirtualSpeed.speedMps(0, -8.0, params)
    assertEquals("coasting terminal velocity should dominate", coast, v, 1e-6)
    assertTrue(coast > 5.0)
  }
}
