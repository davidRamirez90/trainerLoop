package com.trainerloop.ble

enum class ClickShift { UP, DOWN }

/**
 * Converts repeated [ClickMessage.ButtonState] frames into discrete shift
 * events. The Click re-sends the same state many times per press and while a
 * button is held; only a released→pressed transition emits an event, which
 * also serves as the debounce. Not thread-safe — call from one dispatcher
 * (the manager collects all notification flows on Dispatchers.Main).
 */
class ClickShiftDetector {
  private var plusWasPressed = false
  private var minusWasPressed = false

  fun onState(state: ClickMessage.ButtonState): List<ClickShift> {
    val events = buildList {
      if (state.plusPressed && !plusWasPressed) add(ClickShift.UP)
      if (state.minusPressed && !minusWasPressed) add(ClickShift.DOWN)
    }
    plusWasPressed = state.plusPressed
    minusWasPressed = state.minusPressed
    return events
  }

  /** Call on reconnect so a stale "pressed" memory can't swallow a real press. */
  fun reset() {
    plusWasPressed = false
    minusWasPressed = false
  }
}
