package com.trainerloop.data.source.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
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
    val icuSyncedAt: String? = null
)
