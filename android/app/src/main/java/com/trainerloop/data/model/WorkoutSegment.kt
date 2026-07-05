package com.trainerloop.data.model

sealed class WorkoutSegment(
  open val id: String,
  open val durationSec: Int,
  open val label: String?,
  open val phase: SegmentPhase,
  open val isWork: Boolean
) {
  data class Step(
    override val id: String,
    override val durationSec: Int,
    override val label: String?,
    override val phase: SegmentPhase,
    override val isWork: Boolean,
    val targetRange: TargetRange,
    val targetCadence: IntRange? = null
  ) : WorkoutSegment(id, durationSec, label, phase, isWork)

  data class Ramp(
    override val id: String,
    override val durationSec: Int,
    override val label: String?,
    override val phase: SegmentPhase,
    override val isWork: Boolean,
    val startPower: Int,
    val endPower: Int,
    val targetCadence: IntRange? = null
  ) : WorkoutSegment(id, durationSec, label, phase, isWork)

  data class FreeRide(
    override val id: String,
    override val durationSec: Int,
    override val label: String?,
    override val phase: SegmentPhase,
    override val isWork: Boolean = false
  ) : WorkoutSegment(id, durationSec, label, phase, isWork)
}

enum class SegmentPhase { WARMUP, WORK, RECOVERY, COOLDOWN }

/** Returns a copy of this segment with a new duration, preserving its concrete type. */
fun WorkoutSegment.withDurationSec(newDurationSec: Int): WorkoutSegment = when (this) {
  is WorkoutSegment.Step -> copy(durationSec = newDurationSec)
  is WorkoutSegment.Ramp -> copy(durationSec = newDurationSec)
  is WorkoutSegment.FreeRide -> copy(durationSec = newDurationSec)
}
