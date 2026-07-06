package com.trainerloop.domain.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualSpeedTest {
  private val params = PhysicsParams(riderKg = 75.0)

  @Test
  fun `250W on the flat is roughly 36 kmh`() {
    val v = VirtualSpeed.speedMps(250, 0.0, params)
    assertTrue("expected ~10.2 m/s, got $v", v in 9.5..10.8)
  }

  @Test
  fun `more power is faster`() {
    assertTrue(
      VirtualSpeed.speedMps(300, 0.0, params) > VirtualSpeed.speedMps(200, 0.0, params)
    )
  }

  @Test
  fun `climbing is slower than flat`() {
    assertTrue(
      VirtualSpeed.speedMps(250, 8.0, params) < VirtualSpeed.speedMps(250, 0.0, params)
    )
  }

  @Test
  fun `climbing 8pct at 250W is roughly 12 kmh`() {
    val v = VirtualSpeed.speedMps(250, 8.0, params)
    assertTrue("expected ~3.4 m/s, got $v", v in 2.8..4.0)
  }

  @Test
  fun `zero power on flat or climb means standstill`() {
    assertEquals(0.0, VirtualSpeed.speedMps(0, 0.0, params), 1e-9)
    assertEquals(0.0, VirtualSpeed.speedMps(0, 3.0, params), 1e-9)
  }

  @Test
  fun `coasting a descent reaches terminal velocity`() {
    val v = VirtualSpeed.speedMps(0, -5.0, params)
    assertTrue("expected >3 m/s coasting -5%, got $v", v > 3.0)
  }

  @Test
  fun `grade is clamped to plus minus 20`() {
    assertEquals(
      VirtualSpeed.speedMps(0, -20.0, params),
      VirtualSpeed.speedMps(0, -35.0, params),
      1e-6
    )
  }

  @Test
  fun `solver round trips through powerAt`() {
    val v = VirtualSpeed.speedMps(250, 2.0, params)
    assertEquals(250.0, VirtualSpeed.powerAt(v, 2.0, params), 0.5)
  }
}
