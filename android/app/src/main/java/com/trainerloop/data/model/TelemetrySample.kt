package com.trainerloop.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TelemetrySample(
  val timeSec: Int,
  val powerWatts: Int,
  val cadenceRpm: Int,
  val hrBpm: Int,
  val dropout: Boolean = false,
  val lagCompensated: Boolean = false
)
