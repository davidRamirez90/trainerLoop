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
    val avgHr: Int,
    val icuSyncedAt: String? = null
)

/**
 * Full session data used when persisting a session. The samples and
 * legacy coach events are retained as serialized JSON for compatibility so the
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
    /** Legacy coaching payload; new rides store an empty string. */
    val coachEventsJson: String,
    val completed: Boolean,
    val avgPower: Int,
    val maxPower: Int,
    val avgCadence: Int,
    val avgHr: Int,
    val icuSyncedAt: String? = null,
    /** "WORKOUT" or "FREE_RIDE". */
    val sessionType: String = "WORKOUT",
    /** Legacy route reference retained for existing sessions; new workouts use null. */
    val routeId: String? = null
)
