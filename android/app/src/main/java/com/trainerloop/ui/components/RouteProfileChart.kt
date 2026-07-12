package com.trainerloop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.trainerloop.data.model.RoutePoint
import com.trainerloop.ui.theme.trainerLoopColors
import java.util.Locale

/** Elevation-vs-distance silhouette with an optional rider position marker. */
@Composable
fun RouteProfileChart(
  points: List<RoutePoint>,
  positionM: Double?,
  modifier: Modifier = Modifier
) {
  if (points.size < 2) return
  val semanticColors = MaterialTheme.trainerLoopColors
  val fillColor = semanticColors.chartElevation.copy(alpha = 0.25f)
  val lineColor = semanticColors.chartElevation
  val markerColor = semanticColors.chartCursor
  val totalDistanceM = points.last().distanceM
  val minElevationM = points.minOf { it.elevationM }
  val maxElevationM = points.maxOf { it.elevationM }
  val summary = String.format(
    Locale.US,
    "Elevation profile: %.1f kilometers, elevations %.0f to %.0f meters.",
    totalDistanceM / 1000.0,
    minElevationM,
    maxElevationM
  )

  Canvas(
    modifier = modifier
      .fillMaxWidth()
      .height(120.dp)
      .semantics { contentDescription = summary }
  ) {
    val total = points.last().distanceM
    val minEle = points.minOf { it.elevationM }
    val eleSpan = (points.maxOf { it.elevationM } - minEle).coerceAtLeast(1.0)
    fun x(d: Double) = (d / total * size.width).toFloat()
    fun y(e: Double) = size.height - ((e - minEle) / eleSpan * size.height * 0.9).toFloat()

    val step = (points.size / 300).coerceAtLeast(1)
    val path = Path().apply {
      moveTo(0f, size.height)
      for (i in points.indices step step) {
        lineTo(x(points[i].distanceM), y(points[i].elevationM))
      }
      lineTo(size.width, y(points.last().elevationM))
      lineTo(size.width, size.height)
      close()
    }
    drawPath(path, color = fillColor)
    drawPath(path, color = lineColor, style = Stroke(width = 2.dp.toPx()))

    positionM?.let { pos ->
      val px = x(pos.coerceIn(0.0, total))
      drawLine(
        color = markerColor,
        start = androidx.compose.ui.geometry.Offset(px, 0f),
        end = androidx.compose.ui.geometry.Offset(px, size.height),
        strokeWidth = 3.dp.toPx()
      )
    }
  }
}
