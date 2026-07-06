package com.trainerloop.domain.sim

import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin

data class PhysicsParams(
  val riderKg: Double,
  val bikeKg: Double = 8.0,
  val crr: Double = 0.005,
  val cda: Double = 0.32
)

/**
 * Steady-state cycling physics: P = Crr·m·g·cosθ·v + m·g·sinθ·v + ½·ρ·CdA·v³.
 * Solved for v by bisection — the expression has exactly one sign change on
 * (0, MAX_SPEED] for any grade in range.
 */
object VirtualSpeed {
  private const val G = 9.81
  private const val RHO = 1.226
  private const val MAX_SPEED_MPS = 40.0

  fun powerAt(v: Double, gradePercent: Double, p: PhysicsParams): Double {
    val m = p.riderKg + p.bikeKg
    val theta = atan(gradePercent / 100.0)
    return (p.crr * m * G * cos(theta) + m * G * sin(theta)) * v +
      0.5 * RHO * p.cda * v * v * v
  }

  fun speedMps(powerWatts: Int, gradePercent: Double, p: PhysicsParams): Double {
    val power = powerWatts.coerceIn(0, 2000).toDouble()
    val grade = gradePercent.coerceIn(-20.0, 20.0)
    // Rider can't overcome resistance at all -> standstill. Covers P=0 on
    // flats/climbs and descents too shallow to overcome rolling resistance.
    if (powerAt(1e-3, grade, p) >= power) return 0.0
    var lo = 1e-3
    var hi = MAX_SPEED_MPS
    repeat(50) {
      val mid = (lo + hi) / 2
      if (powerAt(mid, grade, p) < power) lo = mid else hi = mid
    }
    return (lo + hi) / 2
  }
}
