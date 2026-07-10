package com.trainerloop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.domain.WorkoutMath
import com.trainerloop.ui.theme.ZoneColors
import com.trainerloop.ui.theme.zoneColorSet

private const val HR_AXIS_MIN = 40f
private const val HR_AXIS_MAX = 200f
private const val ZOOM_PAD_SEC = 20

@Composable
fun WorkoutChart(
  segments: List<WorkoutSegment>,
  samples: List<TelemetrySample>,
  elapsedSec: Int,
  ftp: Int,
  modifier: Modifier = Modifier,
  elevationProfile: DoubleArray? = null
) {
  val darkTheme = isSystemInDarkTheme()
  val totalDuration = remember(segments) { WorkoutMath.totalDurationSec(segments) }
  // Segment bounds (startSec, endSec) for tap hit-testing.
  val bounds = remember(segments) {
    var acc = 0
    segments.map { seg -> Triple(acc, acc + seg.durationSec, seg).also { acc += seg.durationSec } }
  }

  var zoomToCurrent by remember { mutableStateOf(false) }
  var selectedIndex by remember { mutableStateOf<Int?>(null) }

  // Visible time window: whole session, or the current interval padded.
  val (winStart, winEnd) = if (zoomToCurrent && totalDuration > 0) {
    val curIdx = WorkoutMath.segmentIndexAt(segments, elapsedSec)
    val (s, e) = bounds.getOrNull(curIdx)?.let { it.first to it.second } ?: (0 to totalDuration)
    (s - ZOOM_PAD_SEC).coerceAtLeast(0) to (e + ZOOM_PAD_SEC).coerceAtMost(totalDuration)
  } else 0 to totalDuration
  val winSpan = (winEnd - winStart).coerceAtLeast(1)

  // Static across the ride (depends only on the plan), so don't rescan the plan
  // on every 1 Hz redraw.
  val peakTarget = remember(segments, totalDuration) {
    if (totalDuration == 0) 0
    else (0..totalDuration step (totalDuration / 100).coerceAtLeast(1))
      .maxOf { WorkoutMath.targetRangeAt(segments, it).high }
  }

  val cursorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
  val hrLineColor = MaterialTheme.colorScheme.error
  val powerLineColor = MaterialTheme.colorScheme.secondary
  val elevationColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f)

  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.End
    ) {
      FilterChip(
        selected = zoomToCurrent,
        onClick = { zoomToCurrent = !zoomToCurrent },
        label = { Text(if (zoomToCurrent) "Focus" else "Full") }
      )
    }

    Box {
      Canvas(
        modifier = Modifier
          .fillMaxWidth()
          .height(160.dp)
          .pointerInput(segments, winStart, winEnd) {
            detectTapGestures { offset ->
              val sec = winStart + (offset.x / size.width) * winSpan
              val idx = bounds.indexOfFirst { sec >= it.first && sec < it.second }
              selectedIndex = if (idx < 0 || idx == selectedIndex) null else idx
            }
          }
      ) {
        if (totalDuration == 0) return@Canvas

        val width = size.width
        val heightPx = size.height
        val padding = 8.dp.toPx()
        val chartHeight = heightPx - padding * 2
        val chartBottom = heightPx - padding

        val peakSample = samples.maxOfOrNull { it.powerWatts } ?: 0
        val maxPowerAxis = (maxOf(peakTarget, peakSample, 1) * 1.1f)

        fun xForTime(sec: Int): Float = ((sec - winStart) / winSpan.toFloat()) * width

        fun yForPower(power: Int): Float {
          val ratio = (power / maxPowerAxis).coerceIn(0f, 1f)
          return chartBottom - ratio * chartHeight
        }

        fun yForHr(bpm: Int): Float {
          val ratio = ((bpm - HR_AXIS_MIN) / (HR_AXIS_MAX - HR_AXIS_MIN)).coerceIn(0f, 1f)
          return chartBottom - ratio * chartHeight
        }

        // Soft terrain silhouette across the bottom 30% of the chart.
        if (elevationProfile != null && elevationProfile.isNotEmpty()) {
          val minAlt = elevationProfile.min()
          val altSpan = (elevationProfile.max() - minAlt).coerceAtLeast(1.0)
          val bandHeight = chartHeight * 0.3f
          val elevPath = Path()
          elevPath.moveTo(xForTime(winStart), chartBottom)
          val elevStep = (winSpan / 200).coerceAtLeast(1)
          var t = winStart
          while (t <= winEnd) {
            val alt = elevationProfile[t.coerceIn(0, elevationProfile.lastIndex)]
            val y = chartBottom - ((alt - minAlt) / altSpan).toFloat() * bandHeight
            elevPath.lineTo(xForTime(t), y)
            t += elevStep
          }
          elevPath.lineTo(xForTime(winEnd), chartBottom)
          elevPath.close()
          drawPath(elevPath, color = elevationColor)
        }

        // Gridlines at FTP and FTP/2.
        if (ftp > 0) {
          val gridColor = cursorColor.copy(alpha = 0.15f)
          listOf(ftp, ftp / 2).forEach { watts ->
            val y = yForPower(watts)
            drawLine(color = gridColor, start = Offset(0f, y), end = Offset(width, y), strokeWidth = 1.dp.toPx())
          }
        }

        // Full-height-from-zero interval blocks over the visible window.
        val step = (winSpan / 200).coerceAtLeast(1)
        var sec = winStart
        while (sec <= winEnd) {
          val range = WorkoutMath.targetRangeAt(segments, sec)
          val nextSec = (sec + step).coerceAtMost(winEnd)
          val xStart = xForTime(sec)
          val xEnd = xForTime(nextSec)
          val target = (range.low + range.high) / 2
          val yTop = yForPower(target)
          drawRect(
            color = ZoneColors.forTarget(target, ftp, darkTheme).fill,
            topLeft = Offset(xStart, yTop),
            size = Size(xEnd - xStart, chartBottom - yTop)
          )
          sec += step
        }

        // Highlight the tapped interval.
        selectedIndex?.let { idx ->
          bounds.getOrNull(idx)?.let { (s, e, _) ->
            val xs = xForTime(s).coerceIn(0f, width)
            val xe = xForTime(e).coerceIn(0f, width)
            drawRect(
              color = cursorColor.copy(alpha = 0.18f),
              topLeft = Offset(xs, 0f),
              size = Size(xe - xs, heightPx)
            )
          }
        }

        // Only draw sample lines for points inside the window so zoom doesn't
        // stretch straight lines to the edges.
        val visible = samples.filter { it.timeSec in winStart..winEnd }

        // HR line, own axis, broken across dropouts (hrBpm == 0).
        if (visible.size >= 2) {
          var hrPath: Path? = null
          visible.forEach { sample ->
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
        if (visible.size >= 2) {
          val path = Path()
          visible.firstOrNull()?.let { first ->
            path.moveTo(xForTime(first.timeSec), yForPower(first.powerWatts))
          }
          visible.drop(1).forEach { sample ->
            path.lineTo(xForTime(sample.timeSec), yForPower(sample.powerWatts))
          }
          drawPath(path, color = powerLineColor, style = Stroke(width = 2.5.dp.toPx()))
        }

        if (elapsedSec in winStart..winEnd) {
          val currentX = xForTime(elapsedSec)
          drawLine(
            color = cursorColor,
            start = Offset(currentX, 0f),
            end = Offset(currentX, heightPx),
            strokeWidth = 1.5.dp.toPx()
          )
        }
      }

      selectedIndex?.let { idx ->
        bounds.getOrNull(idx)?.let { (start, _, seg) ->
          IntervalTooltip(
            index = idx,
            count = segments.size,
            start = start,
            segment = seg,
            ftp = ftp,
            modifier = Modifier
              .align(Alignment.TopStart)
              .padding(8.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun IntervalTooltip(
  index: Int,
  count: Int,
  start: Int,
  segment: WorkoutSegment,
  ftp: Int,
  modifier: Modifier = Modifier
) {
  val range = WorkoutMath.targetRangeAt(listOf(segment), 0)
  val mid = (range.low + range.high) / 2
  val colors = zoneColorSet(mid, ftp)
  val title = segment.label ?: segment.phase.name.lowercase().replaceFirstChar { it.uppercase() }
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
  ) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(colors.line)
        )
        Text(
          text = "  $title",
          style = MaterialTheme.typography.titleSmall
        )
      }
      Text(
        text = "Interval ${index + 1}/$count · starts ${fmt(start)}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Text(
        text = if (range.low > 0) "${range.low}–${range.high} W · ${fmt(segment.durationSec)}"
        else "Free ride · ${fmt(segment.durationSec)}",
        style = MaterialTheme.typography.bodySmall
      )
    }
  }
}

private fun fmt(totalSec: Int): String {
  val m = totalSec / 60
  val s = totalSec % 60
  return "%d:%02d".format(m, s)
}
