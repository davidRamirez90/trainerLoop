package com.trainerloop.data.model

/**
 * Lightweight summary of a stored session, used for the session list UI.
 * Excludes the full telemetry and coach-event JSON payloads.
 */
data class SessionSummary(
    val id: String,
    val workoutId: String,
    val workoutName: String,
    val startedAt: String,
    val endedAt: String?,
    val durationSec: Int,
    val completed: Boolean,
    val avgPower: Int,
    val maxPower: Int,
    val avgCadence: Int,
    val avgHr: Int
)

/**
 * Full session data used when persisting a session. The samples and
 * coach events are passed as already-serialized JSON strings so the
 * repository does not need to depend on kotlinx-serialization directly.
 */
data class SessionData(
    val id: String,
    val workoutId: String,
    val workoutName: String,
    val startedAt: String,
    val endedAt: String?,
    val durationSec: Int,
    val samplesJson: String,
    val coachEventsJson: String,
    val completed: Boolean,
    val avgPower: Int,
    val maxPower: Int,
    val avgCadence: Int,
    val avgHr: Int
)
