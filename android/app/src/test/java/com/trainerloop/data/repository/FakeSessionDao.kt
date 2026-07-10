package com.trainerloop.data.repository

import com.trainerloop.data.source.local.SessionDao
import com.trainerloop.data.source.local.SessionEntity
import com.trainerloop.data.source.local.SessionSummaryRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [SessionDao] fake shared by the repository/uploader JVM tests. */
class FakeSessionDao : SessionDao {
  private val rows = MutableStateFlow<List<SessionEntity>>(emptyList())

  override fun getSummaries(): Flow<List<SessionSummaryRow>> = rows.map { entities ->
    entities.map { entity ->
      SessionSummaryRow(
        id = entity.id,
        workoutId = entity.workoutId,
        workoutName = entity.workoutName,
        startedAt = entity.startedAt,
        endedAt = entity.endedAt,
        durationSec = entity.durationSec,
        completed = entity.completed,
        avgPower = entity.avgPower,
        maxPower = entity.maxPower,
        avgCadence = entity.avgCadence,
        avgHr = entity.avgHr,
        icuSyncedAt = entity.icuSyncedAt
      )
    }
  }

  override suspend fun insert(entity: SessionEntity) {
    rows.value = rows.value
      .filterNot { it.id == entity.id } + entity
  }

  override suspend fun getById(id: String): SessionEntity? =
    rows.value.firstOrNull { it.id == id }

  override suspend fun delete(entity: SessionEntity) {
    rows.value = rows.value.filterNot { it.id == entity.id }
  }

  override suspend fun deleteById(id: String) {
    rows.value = rows.value.filterNot { it.id == id }
  }

  override suspend fun markIcuSynced(id: String, syncedAt: String) {
    rows.value = rows.value.map {
      if (it.id == id) it.copy(icuSyncedAt = syncedAt) else it
    }
  }
}
