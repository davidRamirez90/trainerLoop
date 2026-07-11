package com.trainerloop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.trainerloop.data.model.TelemetrySample
import kotlinx.coroutines.launch

/** Post-ride telemetry chart with Power / HR / Cadence pager pages. */
@Composable
fun SampleChart(samples: List<TelemetrySample>) {
  val tabs = listOf("Power", "Heart Rate", "Cadence")
  val pagerState = rememberPagerState(pageCount = { tabs.size })
  val scope = rememberCoroutineScope()

  HorizontalPager(
    state = pagerState,
    modifier = Modifier.fillMaxWidth(),
    beyondViewportPageCount = 1
  ) { page ->
    ChartPage(
      samples = samples,
      selectedTab = page
    )
  }

  PagerDots(
    pageTitles = tabs,
    currentPage = pagerState.currentPage,
    onPageSelected = { page -> scope.launch { pagerState.animateScrollToPage(page) } }
  )
}

@Composable
private fun ChartPage(
  samples: List<TelemetrySample>,
  selectedTab: Int
) {
  val (values, color, unit) = when (selectedTab) {
    0 -> Triple(samples.map { it.powerWatts }, MaterialTheme.colorScheme.secondary, "W")
    1 -> Triple(samples.map { it.hrBpm }, MaterialTheme.colorScheme.error, "bpm")
    else -> Triple(samples.map { it.cadenceRpm }, MaterialTheme.colorScheme.primary, "rpm")
  }

  val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
  val maxValue = values.maxOrNull()?.coerceAtLeast(1) ?: 1
  val minValue = values.minOrNull() ?: 0
  val totalDuration = samples.lastOrNull()?.timeSec?.coerceAtLeast(1) ?: 1
  val metricName = when (selectedTab) {
    0 -> "Power"
    1 -> "Heart rate"
    else -> "Cadence"
  }
  val summary = "$metricName chart: ${totalDuration / 60} minutes, values $minValue to $maxValue $unit."

  Canvas(
    modifier = Modifier
      .fillMaxWidth()
      .height(160.dp)
      .semantics { contentDescription = summary }
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
