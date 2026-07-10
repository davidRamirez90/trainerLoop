package com.trainerloop.data.source.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sessions", indices = [Index(value = ["startedAt"])])
data class SessionEntity(
    @PrimaryKey val id: String,
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
    val avgHr: Int,
    /** ISO-8601 instant of the last successful intervals.icu upload; null = never synced. */
    val icuSyncedAt: String? = null,
    /** "WORKOUT" or "FREE_RIDE". */
    val sessionType: String = "WORKOUT",
    /** RouteEntity id for free rides; null for workouts. */
    val routeId: String? = null
)
