package com.trainerloop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.trainerloop.data.model.Workout
import com.trainerloop.domain.WorkoutMath
import com.trainerloop.domain.WorkoutSummaryMath
import com.trainerloop.ui.theme.ZoneColors

@Composable
fun WorkoutMiniChart(
  workout: Workout,
  ftp: Int,
  modifier: Modifier = Modifier,
  chartHeight: Dp = 60.dp,
  maxPowerAxis: Int = 400,
  lineColor: Color = MaterialTheme.colorScheme.primary
) {
  val darkTheme = isSystemInDarkTheme()
  val totalDuration = remember(workout) {
    WorkoutMath.totalDurationSec(workout.segments)
  }
  val isFreeRideOnly = remember(workout) {
    WorkoutSummaryMath.isFreeRideOnly(workout)
  }
  val placeholderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)

  Canvas(
    modifier = modifier
      .fillMaxWidth()
      .height(chartHeight)
      .semantics { contentDescription = workoutProfileSummary(workout.segments) }
  ) {
    if (isFreeRideOnly) {
      val bandHeight = size.height * 0.4f
      val bandWidth = size.width - 4.dp.toPx()
      val bandTop = (size.height - bandHeight) / 2f
      val dash = PathEffect.dashPathEffect(
        floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
        0f
      )
      drawRoundRect(
        color = placeholderColor,
        topLeft = Offset(2.dp.toPx(), bandTop),
        size = Size(bandWidth, bandHeight),
        cornerRadius = CornerRadius(8.dp.toPx()),
        style = Stroke(width = 2.dp.toPx(), pathEffect = dash)
      )
      return@Canvas
    }

    if (totalDuration == 0) return@Canvas

    val width = size.width
    val heightPx = size.height
    val padding = 2.dp.toPx()
    val drawHeight = heightPx - padding * 2
    val chartBottom = heightPx - padding

    fun xForTime(sec: Int): Float =
      (sec / totalDuration.toFloat()) * width

    fun yForPower(power: Int): Float {
      val ratio = (power / maxPowerAxis.toFloat()).coerceIn(0f, 1f)
      return chartBottom - ratio * drawHeight
    }

    val stepSec = (totalDuration / 120f).coerceAtLeast(1f)
    val bands = zoneBands(
      segments = workout.segments,
      ftp = ftp,
      winStartSec = 0f,
      winEndSec = totalDuration.toFloat(),
      stepSec = stepSec
    )

    // Zone fills: one Path per zone, so abutting bands can't leave AA seams.
    val zonePaths = Array(6) { Path() }
    bands.forEach { band ->
      val yTop = yForPower(band.targetWatts)
      zonePaths[band.zone - 1].addRect(
        androidx.compose.ui.geometry.Rect(
          left = xForTime(band.startSec.toInt()),
          top = yTop,
          right = xForTime(band.endSec.toInt()),
          bottom = chartBottom
        )
      )
    }
    zonePaths.forEachIndexed { index, path ->
      if (!path.isEmpty) {
        drawPath(path, color = ZoneColors.forZone(index + 1, darkTheme).fill, style = Fill)
      }
    }

    // Stepped outline along the top of the profile.
    val outline = Path()
    var started = false
    bands.forEach { band ->
      val y = yForPower(band.targetWatts)
      val xStart = xForTime(band.startSec.toInt())
      val xEnd = xForTime(band.endSec.toInt())
      if (!started) {
        outline.moveTo(xStart, y)
        started = true
      } else {
        outline.lineTo(xStart, y)
      }
      outline.lineTo(xEnd, y)
    }
    if (started) drawPath(outline, color = lineColor, style = Stroke(width = 2.dp.toPx()))
  }
}
