package com.trainerloop.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RoutePoint(
  val distanceM: Double,
  val lat: Double,
  val lon: Double,
  val elevationM: Double,
  val gradePercent: Double
)

/** A GPX route resampled to a uniform [GRID_M] distance grid. */
class Route(val name: String?, val points: List<RoutePoint>) {
  init {
    require(points.size >= 2) { "Route needs at least 2 points" }
  }

  val totalDistanceM: Double = points.last().distanceM

  val totalAscentM: Int = points.zipWithNext()
    .sumOf { (a, b) -> (b.elevationM - a.elevationM).coerceAtLeast(0.0) }
    .toInt()

  fun gradeAt(distanceM: Double): Double =
    points[gridIndex(distanceM)].gradePercent

  /** Linear interpolation between the two surrounding grid points. */
  fun pointAt(distanceM: Double): RoutePoint {
    val d = distanceM.coerceIn(0.0, totalDistanceM)
    val i = gridIndex(d)
    val a = points[i]
    val b = points.getOrElse(i + 1) { a }
    val span = b.distanceM - a.distanceM
    val f = if (span <= 0.0) 0.0 else (d - a.distanceM) / span
    return RoutePoint(
      distanceM = d,
      lat = a.lat + (b.lat - a.lat) * f,
      lon = a.lon + (b.lon - a.lon) * f,
      elevationM = a.elevationM + (b.elevationM - a.elevationM) * f,
      gradePercent = a.gradePercent
    )
  }

  private fun gridIndex(distanceM: Double): Int =
    (distanceM / GRID_M).toInt().coerceIn(0, points.lastIndex)

  companion object {
    const val GRID_M = 10.0
  }
}
