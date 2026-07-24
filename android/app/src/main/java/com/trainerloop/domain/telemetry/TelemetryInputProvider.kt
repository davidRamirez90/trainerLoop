package com.trainerloop.domain.telemetry

import com.trainerloop.ble.model.IndoorBikeData
import com.trainerloop.ble.model.Stamped
import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import kotlinx.coroutines.flow.StateFlow

/**
 * Source boundary for workout telemetry.
 *
 * Bluetooth, deterministic scenarios, and future replay sources expose the
 * same raw measurements to [com.trainerloop.domain.TelemetryRecorder], keeping
 * recording, coaching, charts, and persistence on one production pipeline.
 */
interface TelemetryInputProvider {
  val data: StateFlow<Stamped<IndoorBikeData>?>
  val heartRate: StateFlow<Stamped<Int>?>
  /** Whether a post-tick update may replace that same second in the recorder. */
  val replacesCurrentTick: Boolean
    get() = false

  /**
   * Called once per workout-clock tick. Live providers ignore it; deterministic
   * providers use it to publish the next sample.
   */
  fun update(context: TelemetryInputContext) = Unit

  fun reset() = Unit
}

data class TelemetryInputContext(
  val elapsedSec: Int,
  val targetRange: TargetRange,
  val phase: SegmentPhase
)
