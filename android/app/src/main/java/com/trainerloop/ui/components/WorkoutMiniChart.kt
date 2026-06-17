package com.trainerloop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.trainerloop.data.model.Workout
import com.trainerloop.domain.WorkoutMath
import com.trainerloop.ui.theme.Green40

@Composable
fun WorkoutMiniChart(
  workout: Workout,
  modifier: Modifier = Modifier,
  chartHeight: Dp = 60.dp,
  maxPowerAxis: Int = 400,
  fillColor: Color = Green40.copy(alpha = 0.35f),
  lineColor: Color = Green40
) {
  val totalDuration = remember(workout) {
    WorkoutMath.totalDurationSec(workout.segments)
  }

  Canvas(
    modifier = modifier
      .fillMaxWidth()
      .height(chartHeight)
  ) {
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

    val step = (totalDuration / 120).coerceAtLeast(1)
    var sec = 0
    val path = Path()
    var firstPoint: Offset? = null

    while (sec <= totalDuration) {
      val range = WorkoutMath.targetRangeAt(workout.segments, sec)
      val power = (range.low + range.high) / 2
      val x = xForTime(sec)
      val y = yForPower(power)
      val point = Offset(x, y)

      if (firstPoint == null) {
        firstPoint = point
        path.moveTo(point.x, point.y)
      } else {
        path.lineTo(point.x, point.y)
      }

      drawRect(
        color = fillColor,
        topLeft = Offset(x, y),
        size = Size(
          width = (xForTime((sec + step).coerceAtMost(totalDuration)) - x).coerceAtLeast(1f),
          height = chartBottom - y
        ),
        style = Fill
      )

      sec += step
    }

    drawPath(path, color = lineColor, style = Stroke(width = 2f))
  }
}
