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
import androidx.compose.ui.unit.dp
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.data.model.Workout
import com.trainerloop.domain.WorkoutMath
import com.trainerloop.ui.theme.Blue40
import com.trainerloop.ui.theme.Green40

@Composable
fun WorkoutChart(
  workout: Workout,
  samples: List<TelemetrySample>,
  elapsedSec: Int,
  modifier: Modifier = Modifier,
  maxPowerAxis: Int = 400
) {
  val totalDuration = remember(workout) {
    WorkoutMath.totalDurationSec(workout.segments)
  }

  Canvas(
    modifier = modifier
      .fillMaxWidth()
      .height(160.dp)
  ) {
    if (totalDuration == 0) return@Canvas

    val width = size.width
    val heightPx = size.height
    val padding = 8.dp.toPx()
    val chartHeight = heightPx - padding * 2
    val chartBottom = heightPx - padding

    fun xForTime(sec: Int): Float =
      (sec / totalDuration.toFloat()) * width

    fun yForPower(power: Int): Float {
      val ratio = (power / maxPowerAxis.toFloat()).coerceIn(0f, 1f)
      return chartBottom - ratio * chartHeight
    }

    val minBandHeightPx = 3.dp.toPx()
    val step = (totalDuration / 200).coerceAtLeast(1)
    var sec = 0
    while (sec <= totalDuration) {
      val range = WorkoutMath.targetRangeAt(workout.segments, sec)
      val nextSec = (sec + step).coerceAtMost(totalDuration)
      val xStart = xForTime(sec)
      val xEnd = xForTime(nextSec)
      var yLow = yForPower(range.low)
      var yHigh = yForPower(range.high)
      val bandHeight = yLow - yHigh
      if (bandHeight < minBandHeightPx) {
        val deficit = minBandHeightPx - bandHeight
        yHigh -= deficit / 2f
        yLow += deficit / 2f
      }
      drawRect(
        color = Green40.copy(alpha = 0.25f),
        topLeft = Offset(xStart, yHigh),
        size = Size(xEnd - xStart, yLow - yHigh)
      )
      sec += step
    }

    if (samples.size >= 2) {
      val path = Path()
      samples.firstOrNull()?.let { first ->
        path.moveTo(xForTime(first.timeSec), yForPower(first.powerWatts))
      }
      samples.drop(1).forEach { sample ->
        path.lineTo(xForTime(sample.timeSec), yForPower(sample.powerWatts))
      }
      drawPath(path, color = Blue40, style = Stroke(width = 3f))
    }

    val currentX = xForTime(elapsedSec)
    drawLine(
      color = Color.White,
      start = Offset(currentX, 0f),
      end = Offset(currentX, heightPx),
      strokeWidth = 2f
    )
  }
}
