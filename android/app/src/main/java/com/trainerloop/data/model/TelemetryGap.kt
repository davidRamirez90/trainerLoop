package com.trainerloop.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TelemetryGap(
  val startSec: Int,
  val endSec: Int,
  val kind: GapKind = GapKind.DROPOUT
)

@Serializable
enum class GapKind { DROPOUT }
