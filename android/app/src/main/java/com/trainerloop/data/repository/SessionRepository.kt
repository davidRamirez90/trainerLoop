package com.trainerloop.data.repository

import com.trainerloop.data.model.SessionData
import com.trainerloop.data.model.SessionSummary
import com.trainerloop.data.source.local.AppDatabase
import com.trainerloop.data.source.local.SessionDao
import com.trainerloop.data.source.local.SessionEntity
import com.trainerloop.data.source.local.SessionSummaryRow
import com.trainerloop.domain.WorkoutNameCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

open class SessionRepository(private val dao: SessionDao) {

    open fun summaries(): Flow<List<SessionSummary>> =
        dao.getSummaries().map { rows -> rows.map(::toSummary) }

    open suspend fun save(session: SessionData) {
        dao.insert(toEntity(session))
    }

    suspend fun getById(id: String): SessionData? =
        dao.getById(id)?.let(::toData)

    open suspend fun deleteById(id: String) {
        dao.deleteById(id)
    }

    suspend fun markIcuSynced(id: String, syncedAt: String) {
        dao.markIcuSynced(id, syncedAt)
    }

    private fun toEntity(s: SessionData): SessionEntity = SessionEntity(
        id = s.id,
        workoutId = s.workoutId,
        workoutName = WorkoutNameCodec.normalizeStoredName(s.workoutName),
        startedAt = s.startedAt,
        endedAt = s.endedAt,
        durationSec = s.durationSec,
        samplesJson = s.samplesJson,
        coachEventsJson = s.coachEventsJson,
        completed = s.completed,
        avgPower = s.avgPower,
        maxPower = s.maxPower,
        avgCadence = s.avgCadence,
        avgHr = s.avgHr,
        icuSyncedAt = s.icuSyncedAt,
        sessionType = s.sessionType,
        routeId = s.routeId
    )

    private fun toSummary(e: SessionSummaryRow): SessionSummary = SessionSummary(
        id = e.id,
        workoutId = e.workoutId,
        workoutName = WorkoutNameCodec.normalizeStoredName(e.workoutName),
        startedAt = e.startedAt,
        endedAt = e.endedAt,
        durationSec = e.durationSec,
        completed = e.completed,
        avgPower = e.avgPower,
        maxPower = e.maxPower,
        avgCadence = e.avgCadence,
        avgHr = e.avgHr,
        icuSyncedAt = e.icuSyncedAt
    )

    private fun toData(e: SessionEntity): SessionData = SessionData(
        id = e.id,
        workoutId = e.workoutId,
        workoutName = WorkoutNameCodec.normalizeStoredName(e.workoutName),
        startedAt = e.startedAt,
        endedAt = e.endedAt,
        durationSec = e.durationSec,
        samplesJson = e.samplesJson,
        coachEventsJson = e.coachEventsJson,
        completed = e.completed,
        avgPower = e.avgPower,
        maxPower = e.maxPower,
        avgCadence = e.avgCadence,
        avgHr = e.avgHr,
        icuSyncedAt = e.icuSyncedAt,
        sessionType = e.sessionType,
        routeId = e.routeId
    )

    companion object {
        fun create(database: AppDatabase): SessionRepository =
            SessionRepository(database.sessionDao())
    }
}
