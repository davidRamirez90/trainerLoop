package com.trainerloop.data.repository

import com.trainerloop.data.model.SessionData
import com.trainerloop.data.model.SessionSummary
import com.trainerloop.data.source.local.AppDatabase
import com.trainerloop.data.source.local.SessionDao
import com.trainerloop.data.source.local.SessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SessionRepository(private val dao: SessionDao) {

    fun summaries(): Flow<List<SessionSummary>> =
        dao.getAll().map { rows -> rows.map(::toSummary) }

    suspend fun save(session: SessionData) {
        dao.insert(toEntity(session))
    }

    suspend fun getById(id: String): SessionData? =
        dao.getById(id)?.let(::toData)

    suspend fun deleteById(id: String) {
        dao.deleteById(id)
    }

    private fun toEntity(s: SessionData): SessionEntity = SessionEntity(
        id = s.id,
        workoutId = s.workoutId,
        workoutName = s.workoutName,
        startedAt = s.startedAt,
        endedAt = s.endedAt,
        durationSec = s.durationSec,
        samplesJson = s.samplesJson,
        coachEventsJson = s.coachEventsJson,
        completed = s.completed,
        avgPower = s.avgPower,
        maxPower = s.maxPower,
        avgCadence = s.avgCadence,
        avgHr = s.avgHr
    )

    private fun toSummary(e: SessionEntity): SessionSummary = SessionSummary(
        id = e.id,
        workoutId = e.workoutId,
        workoutName = e.workoutName,
        startedAt = e.startedAt,
        endedAt = e.endedAt,
        durationSec = e.durationSec,
        completed = e.completed,
        avgPower = e.avgPower,
        maxPower = e.maxPower,
        avgCadence = e.avgCadence,
        avgHr = e.avgHr
    )

    private fun toData(e: SessionEntity): SessionData = SessionData(
        id = e.id,
        workoutId = e.workoutId,
        workoutName = e.workoutName,
        startedAt = e.startedAt,
        endedAt = e.endedAt,
        durationSec = e.durationSec,
        samplesJson = e.samplesJson,
        coachEventsJson = e.coachEventsJson,
        completed = e.completed,
        avgPower = e.avgPower,
        maxPower = e.maxPower,
        avgCadence = e.avgCadence,
        avgHr = e.avgHr
    )

    companion object {
        fun create(database: AppDatabase): SessionRepository =
            SessionRepository(database.sessionDao())
    }
}
