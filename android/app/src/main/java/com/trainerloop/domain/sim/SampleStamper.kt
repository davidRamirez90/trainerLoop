package com.trainerloop.domain.sim

/** What a simulation stamps onto each 1 Hz telemetry sample. */
data class VirtualStamp(
  val speedKph: Double,
  val distanceM: Double,
  val altitudeM: Double,
  val gradePercent: Double,
  val lat: Double? = null,
  val lon: Double? = null
)

/** 1 Hz hook for [com.trainerloop.domain.TelemetryRecorder]. */
interface SampleStamper {
  fun stamp(timeSec: Int, powerWatts: Int, cadenceRpm: Int, dropout: Boolean): VirtualStamp
}
