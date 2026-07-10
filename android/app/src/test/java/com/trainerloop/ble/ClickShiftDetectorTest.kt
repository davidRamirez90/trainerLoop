package com.trainerloop.ble

import org.junit.Assert.assertEquals
import org.junit.Test

class ClickShiftDetectorTest {

  private val detector = ClickShiftDetector()

  private fun state(plus: Boolean = false, minus: Boolean = false) =
    ClickMessage.ButtonState(plusPressed = plus, minusPressed = minus)

  @Test
  fun `plus press emits UP once`() {
    assertEquals(listOf(ClickShift.UP), detector.onState(state(plus = true)))
  }

  @Test
  fun `repeated pressed frames do not re-emit`() {
    detector.onState(state(plus = true))
    assertEquals(emptyList<ClickShift>(), detector.onState(state(plus = true)))
    assertEquals(emptyList<ClickShift>(), detector.onState(state(plus = true)))
  }

  @Test
  fun `release then press emits again`() {
    detector.onState(state(plus = true))
    detector.onState(state())
    assertEquals(listOf(ClickShift.UP), detector.onState(state(plus = true)))
  }

  @Test
  fun `minus press emits DOWN`() {
    assertEquals(listOf(ClickShift.DOWN), detector.onState(state(minus = true)))
  }

  @Test
  fun `release frames emit nothing`() {
    assertEquals(emptyList<ClickShift>(), detector.onState(state()))
  }

  @Test
  fun `simultaneous press emits both`() {
    assertEquals(
      listOf(ClickShift.UP, ClickShift.DOWN),
      detector.onState(state(plus = true, minus = true))
    )
  }

  @Test
  fun `reset forgets held buttons`() {
    detector.onState(state(plus = true))
    detector.reset()
    // After a reconnect the first frame may still say "pressed"; it must
    // count as a fresh press, not be swallowed as a stale repeat.
    assertEquals(listOf(ClickShift.UP), detector.onState(state(plus = true)))
  }
}
