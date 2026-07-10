package com.trainerloop.domain.parser

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxParserTest {

  private fun load() = GpxParser.parse(javaClass.getResourceAsStream("/sample.gpx")!!)

  @Test
  fun `parses name distance and grid`() {
    val route = load()
    assertEquals("Test Climb", route.name)
    assertTrue("distance ${route.totalDistanceM}", route.totalDistanceM in 250.0..330.0)
    // Uniform 10 m grid
    route.points.forEachIndexed { i, p ->
      assertEquals(i * 10.0, p.distanceM, 1e-6)
    }
  }

  @Test
  fun `smoothing flattens the single point elevation spike`() {
    val route = load()
    // Raw point 15 (at ~150 m) spikes to 514 m; the ±37.5 m window averages
    // ~7 points, so the smoothed elevation there must sit far below the spike.
    val atSpike = route.pointAt(150.0).elevationM
    assertTrue("smoothed elevation at spike $atSpike", atSpike < 508.0)
    val grades = route.points.map { it.gradePercent }
    assertTrue("max |grade| = ${grades.maxOf { abs(it) }}", grades.all { abs(it) <= 20.0 })
    // True climb is 11.6 m; the spike must add only a small smoothed remnant.
    assertTrue("ascent ${route.totalAscentM}", route.totalAscentM in 8..25)
  }

  @Test
  fun `gradeAt and pointAt clamp and interpolate`() {
    val route = load()
    assertEquals(route.points.first().gradePercent, route.gradeAt(-50.0), 1e-9)
    assertEquals(route.points.last().gradePercent, route.gradeAt(1e9), 1e-9)
    val mid = route.pointAt(15.0) // halfway between grid points 1 and 2
    assertEquals(
      (route.points[1].lat + route.points[2].lat) / 2.0, mid.lat, 1e-9
    )
    assertEquals(15.0, mid.distanceM, 1e-9)
  }

  @Test
  fun `latitude increases monotonically along the track`() {
    val route = load()
    route.points.zipWithNext().forEach { (a, b) -> assertTrue(b.lat >= a.lat) }
  }

  @Test
  fun `rejects gpx with fewer than two usable points`() {
    val gpx = """<?xml version="1.0"?><gpx><trk><trkseg>
      <trkpt lat="47.0" lon="8.0"><ele>500</ele></trkpt>
    </trkseg></trk></gpx>"""
    val ex = assertThrows(GpxParseException::class.java) {
      GpxParser.parse(gpx.byteInputStream())
    }
    assertNotNull(ex.message)
  }

  @Test
  fun `rejects gpx without elevation`() {
    val gpx = """<?xml version="1.0"?><gpx><trk><trkseg>
      <trkpt lat="47.0" lon="8.0"/><trkpt lat="47.001" lon="8.0"/>
    </trkseg></trk></gpx>"""
    assertThrows(GpxParseException::class.java) { GpxParser.parse(gpx.byteInputStream()) }
  }

  @Test
  fun `rejects unparseable xml`() {
    assertThrows(GpxParseException::class.java) {
      GpxParser.parse("not xml at all".byteInputStream())
    }
  }

  @Test
  fun `caps parsed points for large gpx`() {
    val gpx = buildString {
      append("<?xml version=\"1.0\"?><gpx><trk><trkseg>")
      repeat(60_000) { index ->
        append("<trkpt lat=\"${47.0 + index * 0.0001}\" lon=\"8.0\"><ele>${500.0 + index * 0.01}</ele></trkpt>")
      }
      append("</trkseg></trk></gpx>")
    }

    val route = GpxParser.parse(gpx.byteInputStream())

    assertTrue(route.points.size <= 50_000)
  }
}
