package com.trainerloop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.trainerloop.data.model.TelemetrySample

/** Post-ride telemetry chart with Power / HR / Cadence tabs. */
@Composable
fun SampleChart(samples: List<TelemetrySample>) {
  var selectedTab by rememberSaveable { mutableIntStateOf(0) }
  val tabs = listOf("Power", "Heart Rate", "Cadence")

  TabRow(selectedTabIndex = selectedTab) {
    tabs.forEachIndexed { index, title ->
      Tab(
        selected = selectedTab == index,
        onClick = { selectedTab = index },
        text = { Text(title) }
      )
    }
  }

  Spacer(modifier = Modifier.height(8.dp))

  val (values, color, unit) = when (selectedTab) {
    0 -> Triple(samples.map { it.powerWatts }, MaterialTheme.colorScheme.secondary, "W")
    1 -> Triple(samples.map { it.hrBpm }, MaterialTheme.colorScheme.error, "bpm")
    else -> Triple(samples.map { it.cadenceRpm }, MaterialTheme.colorScheme.primary, "rpm")
  }

  val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
  val maxValue = values.maxOrNull()?.coerceAtLeast(1) ?: 1
  val totalDuration = samples.lastOrNull()?.timeSec?.coerceAtLeast(1) ?: 1

  Canvas(
    modifier = Modifier
      .fillMaxWidth()
      .height(160.dp)
  ) {
    val width = size.width
    val heightPx = size.height
    val padding = 8.dp.toPx()
    val chartHeight = heightPx - padding * 2
    val chartBottom = heightPx - padding

    fun xForTime(sec: Int): Float =
      (sec / totalDuration.toFloat()) * width

    fun yForValue(value: Int): Float =
      chartBottom - (value / maxValue.toFloat()).coerceIn(0f, 1f) * chartHeight

    if (samples.size >= 2) {
      val path = Path()
      path.moveTo(xForTime(samples.first().timeSec), yForValue(values.first()))
      samples.drop(1).forEachIndexed { index, sample ->
        path.lineTo(xForTime(sample.timeSec), yForValue(values[index + 1]))
      }
      drawPath(path, color = color, style = Stroke(width = 3f))
    }

    drawLine(
      color = gridColor,
      start = Offset(0f, padding),
      end = Offset(width, padding),
      strokeWidth = 1f
    )
  }

  Text(
    text = "Max: ${values.maxOrNull() ?: 0} $unit",
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
  )
}
