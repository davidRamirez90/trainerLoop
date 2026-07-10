package com.trainerloop.domain.parser

import com.trainerloop.data.model.Route
import com.trainerloop.data.model.RoutePoint
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.w3c.dom.Element

class GpxParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Parses GPX trackpoints into a [Route]: haversine cumulative distance,
 * distance-windowed elevation smoothing (raw GPX elevation is noisy and
 * would slam the resistance), resampling to a uniform 10 m grid, grade
 * clamped to ±20 % as a bad-data guard.
 */
object GpxParser {
  private const val SMOOTH_HALF_WINDOW_M = 37.5 // ~75 m total window
  private const val MAX_GRADE_PCT = 20.0
  private const val MIN_POINT_SPACING_M = 0.5
  private const val EARTH_RADIUS_M = 6_371_000.0

  private data class Raw(val lat: Double, val lon: Double, val ele: Double, var distM: Double = 0.0)

  fun parse(input: InputStream): Route {
    val doc = try {
      DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
      }.newDocumentBuilder().parse(input)
    } catch (e: Exception) {
      throw GpxParseException("Not a valid GPX file", e)
    }

    val name = doc.getElementsByTagName("name").item(0)?.textContent?.trim()
      ?.takeIf { it.isNotBlank() }

    val trkpts = doc.getElementsByTagName("trkpt")
    val raw = ArrayList<Raw>(trkpts.length)
    for (i in 0 until trkpts.length) {
      val el = trkpts.item(i) as? Element ?: continue
      val lat = el.getAttribute("lat").toDoubleOrNull() ?: continue
      val lon = el.getAttribute("lon").toDoubleOrNull() ?: continue
      val ele = el.getElementsByTagName("ele").item(0)?.textContent?.trim()?.toDoubleOrNull()
        ?: continue // points without elevation are unusable
      val point = Raw(lat, lon, ele)
      val last = raw.lastOrNull()
      if (last != null) {
        val d = haversineM(last.lat, last.lon, lat, lon)
        if (d < MIN_POINT_SPACING_M) continue // duplicate / GPS jitter
        point.distM = last.distM + d
      }
      raw.add(point)
    }
    if (raw.size < 2) {
      throw GpxParseException("Route needs at least 2 trackpoints with elevation")
    }

    // Distance-windowed moving average over elevation.
    val smoothed = DoubleArray(raw.size) { i ->
      val center = raw[i].distM
      var sum = 0.0
      var count = 0
      for (p in raw) {
        if (p.distM >= center - SMOOTH_HALF_WINDOW_M && p.distM <= center + SMOOTH_HALF_WINDOW_M) {
          sum += p.ele
          count++
        }
      }
      sum / count
    }

    // Resample onto the uniform grid.
    val total = raw.last().distM
    val gridCount = (total / Route.GRID_M).toInt() + 1
    if (gridCount < 2) throw GpxParseException("Route too short")
    val lat = DoubleArray(gridCount)
    val lon = DoubleArray(gridCount)
    val ele = DoubleArray(gridCount)
    var seg = 0
    for (g in 0 until gridCount) {
      val d = (g * Route.GRID_M).coerceAtMost(total)
      while (seg < raw.size - 2 && raw[seg + 1].distM < d) seg++
      val a = raw[seg]
      val b = raw[seg + 1]
      val span = b.distM - a.distM
      val f = if (span <= 0.0) 0.0 else ((d - a.distM) / span).coerceIn(0.0, 1.0)
      lat[g] = a.lat + (b.lat - a.lat) * f
      lon[g] = a.lon + (b.lon - a.lon) * f
      ele[g] = smoothed[seg] + (smoothed[seg + 1] - smoothed[seg]) * f
    }

    val points = List(gridCount) { g ->
      val nextEle = ele[(g + 1).coerceAtMost(gridCount - 1)]
      val thisEle = ele[g]
      val grade = if (g == gridCount - 1 && gridCount >= 2) {
        (ele[g] - ele[g - 1]) / Route.GRID_M * 100.0
      } else {
        (nextEle - thisEle) / Route.GRID_M * 100.0
      }
      RoutePoint(
        distanceM = g * Route.GRID_M,
        lat = lat[g],
        lon = lon[g],
        elevationM = thisEle,
        gradePercent = grade.coerceIn(-MAX_GRADE_PCT, MAX_GRADE_PCT)
      )
    }
    return Route(name, points)
  }

  private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
      cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * EARTH_RADIUS_M * asin(sqrt(a))
  }
}
