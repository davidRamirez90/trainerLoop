package com.trainerloop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.data.model.Workout
import com.trainerloop.domain.WorkoutMath

/** Zone color for a target power, banded by percent of FTP. */
fun zoneColor(targetWatts: Int, ftp: Int): Color {
  if (ftp <= 0) return Color(0xFF9CA3AF).copy(alpha = 0.55f)
  val pct = targetWatts * 100f / ftp
  // Mid-bright hues: pastel over light surfaces, vivid over dark ones.
  val base = when {
    pct < 55 -> Color(0xFF9CA3AF)
    pct < 75 -> Color(0xFF60A5FA)
    pct < 90 -> Color(0xFF4ADE80)
    pct < 105 -> Color(0xFFFBBF24)
    pct < 120 -> Color(0xFFFB923C)
    else -> Color(0xFFF87171)
  }
  return base.copy(alpha = 0.55f)
}

private const val HR_AXIS_MIN = 40f
private const val HR_AXIS_MAX = 200f

@Composable
fun WorkoutChart(
  workout: Workout,
  samples: List<TelemetrySample>,
  elapsedSec: Int,
  ftp: Int,
  modifier: Modifier = Modifier
) {
  val totalDuration = remember(workout) {
    WorkoutMath.totalDurationSec(workout.segments)
  }
  val cursorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
  val hrLineColor = MaterialTheme.colorScheme.error
  val powerLineColor = MaterialTheme.colorScheme.secondary

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

    val peakTarget = (0..totalDuration step (totalDuration / 100).coerceAtLeast(1))
      .maxOf { WorkoutMath.targetRangeAt(workout.segments, it).high }
    val peakSample = samples.maxOfOrNull { it.powerWatts } ?: 0
    val maxPowerAxis = (maxOf(peakTarget, peakSample, 1) * 1.1f)

    fun xForTime(sec: Int): Float =
      (sec / totalDuration.toFloat()) * width

    fun yForPower(power: Int): Float {
      val ratio = (power / maxPowerAxis).coerceIn(0f, 1f)
      return chartBottom - ratio * chartHeight
    }

    fun yForHr(bpm: Int): Float {
      val ratio = ((bpm - HR_AXIS_MIN) / (HR_AXIS_MAX - HR_AXIS_MIN)).coerceIn(0f, 1f)
      return chartBottom - ratio * chartHeight
    }

    // Gridlines at FTP and FTP/2.
    if (ftp > 0) {
      val gridColor = cursorColor.copy(alpha = 0.15f)
      listOf(ftp, ftp / 2).forEach { watts ->
        val y = yForPower(watts)
        drawLine(color = gridColor, start = Offset(0f, y), end = Offset(width, y), strokeWidth = 1.dp.toPx())
      }
    }

    // Full-height-from-zero interval blocks, subdivided ramps included via
    // targetRangeAt sampling, colored by power zone.
    val step = (totalDuration / 200).coerceAtLeast(1)
    var sec = 0
    while (sec <= totalDuration) {
      val range = WorkoutMath.targetRangeAt(workout.segments, sec)
      val nextSec = (sec + step).coerceAtMost(totalDuration)
      val xStart = xForTime(sec)
      val xEnd = xForTime(nextSec)
      val target = (range.low + range.high) / 2
      val yTop = yForPower(target)
      drawRect(
        color = zoneColor(target, ftp),
        topLeft = Offset(xStart, yTop),
        size = Size(xEnd - xStart, chartBottom - yTop)
      )
      sec += step
    }

    // HR line, own axis, broken across dropouts (hrBpm == 0).
    if (samples.size >= 2) {
      var hrPath: Path? = null
      samples.forEach { sample ->
        if (sample.hrBpm <= 0) {
          hrPath = null
        } else {
          val point = Offset(xForTime(sample.timeSec), yForHr(sample.hrBpm))
          val current = hrPath
          if (current == null) {
            hrPath = Path().apply { moveTo(point.x, point.y) }
          } else {
            current.lineTo(point.x, point.y)
          }
        }
      }
      hrPath?.let { drawPath(it, color = hrLineColor, style = Stroke(width = 2.dp.toPx())) }
    }

    // Power line, drawn last so it stays on top of the zone blocks.
    if (samples.size >= 2) {
      val path = Path()
      samples.firstOrNull()?.let { first ->
        path.moveTo(xForTime(first.timeSec), yForPower(first.powerWatts))
      }
      samples.drop(1).forEach { sample ->
        path.lineTo(xForTime(sample.timeSec), yForPower(sample.powerWatts))
      }
      drawPath(path, color = powerLineColor, style = Stroke(width = 2.5.dp.toPx()))
    }

    val currentX = xForTime(elapsedSec)
    drawLine(
      color = cursorColor,
      start = Offset(currentX, 0f),
      end = Offset(currentX, heightPx),
      strokeWidth = 1.5.dp.toPx()
    )
  }
}
