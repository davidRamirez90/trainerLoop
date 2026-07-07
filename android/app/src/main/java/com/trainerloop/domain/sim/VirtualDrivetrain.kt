package com.trainerloop.domain.sim

import kotlin.math.pow

/**
 * ERG-backed virtual gears for a single-cog trainer: pedaling speed comes
 * from cadence × gear ratio, but the wheel can also freewheel — coasting
 * speed is the zero-power terminal velocity on the current grade, so
 * descents stay fast when you stop pedaling and flats roll to a stop.
 * Cadence is EMA-smoothed (~3 s) to stop cadence→resistance oscillation.
 */
class VirtualDrivetrain(private val physics: PhysicsParams) {

  var gear: Int = START_GEAR
    private set

  private var cadenceEma = 0.0

  fun shiftUp() {
    gear = (gear + 1).coerceAtMost(GEAR_COUNT)
  }

  fun shiftDown() {
    gear = (gear - 1).coerceAtLeast(1)
  }

  /** One 1 Hz tick: smooth the cadence, return the virtual speed in m/s. */
  fun tick(cadenceRpm: Int, gradePercent: Double): Double {
    cadenceEma += (cadenceRpm - cadenceEma) * CADENCE_EMA_ALPHA
    val vGear = cadenceEma / 60.0 * RATIOS[gear - 1] * WHEEL_CIRCUMFERENCE_M
    val vCoast = VirtualSpeed.speedMps(0, gradePercent, physics)
    return maxOf(vGear, vCoast)
  }

  companion object {
    const val GEAR_COUNT = 14
    const val START_GEAR = 7
    const val WHEEL_CIRCUMFERENCE_M = 2.096
    private const val CADENCE_EMA_ALPHA = 0.28 // ~3 s time constant at 1 Hz

    /** Geometric spacing 1.0 → 4.6 (≈ 34×34 to 50×11 on a real bike). */
    val RATIOS: DoubleArray = DoubleArray(GEAR_COUNT) { i ->
      4.6.pow(i.toDouble() / (GEAR_COUNT - 1))
    }
  }
}
