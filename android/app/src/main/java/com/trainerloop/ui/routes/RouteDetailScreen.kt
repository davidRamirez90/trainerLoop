package com.trainerloop.ui.routes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trainerloop.data.model.Route
import com.trainerloop.data.model.RoutePoint
import com.trainerloop.data.repository.RouteRepository
import com.trainerloop.data.source.local.AppDatabase
import com.trainerloop.ui.components.MetricTile
import com.trainerloop.ui.components.PrimaryActionButton
import com.trainerloop.ui.components.TrainerLoopTopBar
import com.trainerloop.ui.theme.Spacing
import com.trainerloop.ui.theme.ZoneColors
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailScreen(
  routeId: String,
  onStartRide: (String) -> Unit,
  onBack: () -> Unit
) {
  val context = LocalContext.current
  var route by remember { mutableStateOf<Route?>(null) }
  LaunchedEffect(routeId) {
    route = RouteRepository.create(AppDatabase.getInstance(context)).getById(routeId)
  }

  Scaffold(
    contentWindowInsets = WindowInsets(0),
    topBar = {
      TrainerLoopTopBar(
        title = {
          Text(
            text = route?.name ?: "Route",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        },
        windowInsets = WindowInsets(0),
        onBack = onBack
      )
    }
  ) { padding ->
    val loadedRoute = route
    if (loadedRoute == null) {
      Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center
      ) {
        CircularProgressIndicator()
      }
      return@Scaffold
    }

    Box(
      modifier = Modifier.fillMaxSize().padding(padding)
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
      ) {
        ElevationHero(points = loadedRoute.points)

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenMargin)
        ) {
          RouteStatsRow(route = loadedRoute)
          Spacer(modifier = Modifier.height(Spacing.xxl + 72.dp))
        }
      }

      Surface(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
      ) {
        PrimaryActionButton(
          onClick = { onStartRide(routeId) },
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenMargin, vertical = Spacing.controlGap)
        ) {
          Icon(Icons.Default.PlayArrow, contentDescription = null)
          Text("Start Ride")
        }
      }
    }
  }
}

@Composable
private fun ElevationHero(points: List<RoutePoint>) {
  val primaryContainer = MaterialTheme.colorScheme.primaryContainer
  val outlineVariant = MaterialTheme.colorScheme.outlineVariant
  val darkTheme = isSystemInDarkTheme()
  val amberLine = ZoneColors.forZone(zone = 4, dark = darkTheme).line
  val redLine = ZoneColors.forZone(zone = 6, dark = darkTheme).line
  val elevationSummary = if (points.size >= 2) {
    String.format(
      Locale.US,
      "Elevation profile: %.1f kilometers, elevations %.0f to %.0f meters.",
      points.last().distanceM / 1000.0,
      points.minOf { it.elevationM },
      points.maxOf { it.elevationM }
    )
  } else {
    "Elevation profile unavailable."
  }

  Canvas(
    modifier = Modifier
      .fillMaxWidth()
      .height(200.dp)
      .semantics { contentDescription = elevationSummary }
  ) {
    if (points.size < 2) return@Canvas

    val totalDistance = points.last().distanceM.coerceAtLeast(1.0)
    val minElevation = points.minOf { it.elevationM }
    val elevationSpan = (points.maxOf { it.elevationM } - minElevation).coerceAtLeast(1.0)
    val chartTop = Spacing.sm.toPx()
    val chartBottom = size.height - Spacing.sm.toPx()
    val step = (points.size / 300).coerceAtLeast(1)
    val sampledPoints = points.filterIndexed { index, _ -> index % step == 0 }.let { sampled ->
      if (sampled.last() == points.last()) sampled else sampled + points.last()
    }

    fun x(point: RoutePoint): Float =
      (point.distanceM / totalDistance * size.width).toFloat()

    fun y(point: RoutePoint): Float =
      chartBottom - ((point.elevationM - minElevation) / elevationSpan *
        (chartBottom - chartTop)).toFloat()

    val area = Path().apply {
      moveTo(x(sampledPoints.first()), chartBottom)
      lineTo(x(sampledPoints.first()), y(sampledPoints.first()))
      sampledPoints.drop(1).forEach { point -> lineTo(x(point), y(point)) }
      lineTo(x(sampledPoints.last()), chartBottom)
      close()
    }
    drawPath(
      path = area,
      brush = Brush.verticalGradient(
        colors = listOf(primaryContainer.copy(alpha = 0.82f), Color.Transparent),
        startY = chartTop,
        endY = chartBottom
      )
    )

    sampledPoints.zipWithNext().forEach { (start, end) ->
      val grade = maxOf(abs(start.gradePercent), abs(end.gradePercent))
      val lineColor = when {
        grade > 6.0 -> redLine
        grade >= 3.0 -> amberLine
        else -> outlineVariant
      }
      drawLine(
        color = lineColor,
        start = Offset(x(start), y(start)),
        end = Offset(x(end), y(end)),
        strokeWidth = 2.dp.toPx()
      )
    }
  }
}

@Composable
private fun RouteStatsRow(route: Route) {
  val maxGrade = route.points.maxOf { abs(it.gradePercent) }
  Row(
    modifier = Modifier.fillMaxWidth().padding(top = Spacing.screenMargin),
    horizontalArrangement = Arrangement.spacedBy(Spacing.controlGap)
  ) {
    MetricTile(
      label = "Distance",
      value = String.format(Locale.US, "%.1f", route.totalDistanceM / 1000.0),
      unit = "km",
      modifier = Modifier.weight(1f)
    )
    MetricTile(
      label = "Ascent",
      value = "${route.totalAscentM}",
      unit = "m",
      modifier = Modifier.weight(1f)
    )
    MetricTile(
      label = "Max grade",
      value = String.format(Locale.US, "%.1f", maxGrade),
      unit = "%",
      modifier = Modifier.weight(1f)
    )
  }
}
