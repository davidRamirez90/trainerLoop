package com.trainerloop.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TelemetrySample(
  val timeSec: Int,
  val powerWatts: Int,
  val cadenceRpm: Int,
  val hrBpm: Int,
  val dropout: Boolean = false,
  val lagCompensated: Boolean = false,
  /** Virtual-ride simulation (null when the feature is off or for old sessions). */
  val virtualSpeedKph: Double? = null,
  val virtualDistanceM: Double? = null,
  val virtualAltitudeM: Double? = null,
  val gradePercent: Double? = null,
  /** Position retained for compatibility with previously recorded sessions. */
  val positionLat: Double? = null,
  val positionLon: Double? = null,
  /** Position within the workout plan; actual riding time remains in timeSec. */
  val planTimeSec: Int? = null
) {
  val workoutTimeSec: Int get() = planTimeSec ?: timeSec
}
