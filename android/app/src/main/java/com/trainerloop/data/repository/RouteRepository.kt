package com.trainerloop.data.repository

import com.trainerloop.data.model.Route
import com.trainerloop.data.model.RoutePoint
import com.trainerloop.data.source.local.AppDatabase
import com.trainerloop.data.source.local.RouteDao
import com.trainerloop.data.source.local.RouteEntity
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

data class RouteSummary(
    val id: String,
    val name: String,
    val distanceM: Double,
    val ascentM: Int
)

open class RouteRepository(private val dao: RouteDao) {

    private val json = Json { ignoreUnknownKeys = true }
    private val pointsSerializer = ListSerializer(RoutePoint.serializer())

    open fun summaries(): Flow<List<RouteSummary>> = dao.getAll().map { rows ->
        rows.map { RouteSummary(it.id, it.name, it.distanceM, it.ascentM) }
    }

    /** [fallbackName] is the source filename, used when the GPX has no <name>. */
    suspend fun save(route: Route, fallbackName: String? = null): String {
        val id = UUID.randomUUID().toString()
        dao.insert(
            RouteEntity(
                id = id,
                name = route.name ?: fallbackName ?: "Imported route",
                distanceM = route.totalDistanceM,
                ascentM = route.totalAscentM,
                pointsJson = json.encodeToString(pointsSerializer, route.points),
                importedAt = Instant.now().toString()
            )
        )
        return id
    }

    suspend fun getById(id: String): Route? = dao.getById(id)?.let {
        Route(it.name, json.decodeFromString(pointsSerializer, it.pointsJson))
    }

    suspend fun deleteById(id: String) = dao.deleteById(id)

    companion object {
        fun create(database: AppDatabase): RouteRepository =
            RouteRepository(database.routeDao())
    }
}
