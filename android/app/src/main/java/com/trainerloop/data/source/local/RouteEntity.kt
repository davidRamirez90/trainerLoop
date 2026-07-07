package com.trainerloop.data.source.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey val id: String,
    val name: String,
    val distanceM: Double,
    val ascentM: Int,
    /** JSON list of RoutePoint — same blob pattern as SessionEntity.samplesJson. */
    val pointsJson: String,
    val importedAt: String
)
