package com.trainerloop.domain.sim

import com.trainerloop.data.model.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Free-ride engine: cadence + virtual gear give speed, speed on the route's
 * grade gives the ERG target power, distance advances along the real GPX
 * track. Stateful, owned by the ViewModel, ticked once per workout-second
 * (repeat ticks for the same second are no-ops, dt capped at 1 s).
 *
 * [difficulty] (0..1) scales the grade used for target power only — position,
 * speed, and altitude always use the true grade.
 */
class FreeRideTracker(
  private val route: Route,
  private val physics: PhysicsParams,
  private val difficulty: Double = 1.0
) : SampleStamper {

  data class FreeRidePoint(
    val speedKph: Double,
    val distanceM: Double,
    val altitudeM: Double,
    val gradePercent: Double,
    val lat: Double,
    val lon: Double,
    val targetPowerWatts: Int,
    val routeComplete: Boolean
  )

  val drivetrain = VirtualDrivetrain(physics)

  private val _latest = MutableStateFlow<FreeRidePoint?>(null)
  val latest: StateFlow<FreeRidePoint?> = _latest.asStateFlow()

  private var lastTimeSec = 0
  private var distanceM = 0.0

  @Synchronized
  fun onTick(timeSec: Int, cadenceRpm: Int): FreeRidePoint {
    val grade = if (distanceM >= route.totalDistanceM) 0.0 else route.gradeAt(distanceM)
    val v = drivetrain.tick(cadenceRpm, grade)
    val dt = (timeSec - lastTimeSec).coerceIn(0, 1)
    if (dt > 0) distanceM = (distanceM + v * dt).coerceAtMost(route.totalDistanceM)
    if (timeSec > lastTimeSec) lastTimeSec = timeSec

    val pos = route.pointAt(distanceM)
    val complete = distanceM >= route.totalDistanceM
    val effectiveGrade = (if (complete) 0.0 else grade) * difficulty
    val target = VirtualSpeed.powerAt(v, effectiveGrade, physics).toInt().coerceIn(0, 2000)

    return FreeRidePoint(
      speedKph = v * 3.6,
      distanceM = distanceM,
      altitudeM = pos.elevationM,
      gradePercent = if (complete) 0.0 else grade,
      lat = pos.lat,
      lon = pos.lon,
      targetPowerWatts = target,
      routeComplete = complete
    ).also { _latest.value = it }
  }

  /** Recorder hook — power/dropout are ignored; cadence drives the ride. */
  override fun stamp(timeSec: Int, powerWatts: Int, cadenceRpm: Int, dropout: Boolean): VirtualStamp =
    onTick(timeSec, cadenceRpm).let {
      VirtualStamp(it.speedKph, it.distanceM, it.altitudeM, it.gradePercent, it.lat, it.lon)
    }
}
