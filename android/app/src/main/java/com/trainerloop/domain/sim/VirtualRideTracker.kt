package com.trainerloop.domain.sim

/**
 * Integrates virtual speed into distance/altitude, one workout-second at a
 * time. Stateful and owned by the ViewModel so it survives recorder swaps.
 * Ticks are keyed by workout time: repeats for the same second are no-ops,
 * and dt is capped at 1 s so seeks don't teleport the rider.
 */
class VirtualRideTracker(
  private val route: RouteProfile,
  private val params: PhysicsParams
) : SampleStamper {

  override fun stamp(timeSec: Int, powerWatts: Int, cadenceRpm: Int, dropout: Boolean): VirtualStamp =
    onTick(timeSec, powerWatts, dropout).let {
      VirtualStamp(it.speedKph, it.distanceM, it.altitudeM, it.gradePercent)
    }

  data class VirtualPoint(
    val speedKph: Double,
    val distanceM: Double,
    val altitudeM: Double,
    val gradePercent: Double
  )

  private var lastTimeSec = 0
  private var distanceM = 0.0
  private var altitudeM = 0.0
  private var lastSpeedMps = 0.0

  @Synchronized
  fun onTick(timeSec: Int, powerWatts: Int, dropout: Boolean): VirtualPoint {
    val grade = route.gradeAt(timeSec)
    val v = if (dropout) lastSpeedMps else VirtualSpeed.speedMps(powerWatts, grade, params)
    // ponytail: dt capped at 1 s — a seek skips route, not rides it
    val dt = (timeSec - lastTimeSec).coerceIn(0, 1)
    if (dt > 0) {
      distanceM += v * dt
      altitudeM += v * dt * grade / 100.0
    }
    if (timeSec > lastTimeSec) lastTimeSec = timeSec
    lastSpeedMps = v
    return VirtualPoint(v * 3.6, distanceM, altitudeM, grade)
  }
}
