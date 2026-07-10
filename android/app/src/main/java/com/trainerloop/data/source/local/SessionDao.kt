package com.trainerloop.data.source.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SessionEntity)

    @Query(
        "SELECT id, workoutId, workoutName, startedAt, endedAt, durationSec, completed, " +
            "avgPower, maxPower, avgCadence, avgHr, icuSyncedAt FROM sessions ORDER BY startedAt DESC"
    )
    fun getSummaries(): Flow<List<SessionSummaryRow>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: String): SessionEntity?

    @Delete
    suspend fun delete(entity: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE sessions SET icuSyncedAt = :syncedAt WHERE id = :id")
    suspend fun markIcuSynced(id: String, syncedAt: String)
}

data class SessionSummaryRow(
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
    val icuSyncedAt: String?
)
