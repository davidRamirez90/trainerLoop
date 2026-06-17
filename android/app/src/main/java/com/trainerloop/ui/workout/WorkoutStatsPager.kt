package com.trainerloop.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trainerloop.data.model.TelemetrySample
import kotlinx.coroutines.launch

@Composable
fun WorkoutStatsPager(
  powerSamples: List<TelemetrySample>,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  val pages = listOf("Main", "Power", "Trainer")
  val pagerState = rememberPagerState(pageCount = { pages.size })
  val scope = rememberCoroutineScope()

  Column(modifier = modifier.fillMaxWidth()) {
    TabRow(selectedTabIndex = pagerState.currentPage) {
      pages.forEachIndexed { index, title ->
        Tab(
          selected = pagerState.currentPage == index,
          onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
          text = { Text(title) }
        )
      }
    }

    HorizontalPager(
      state = pagerState,
      modifier = Modifier.fillMaxWidth()
    ) { page ->
      when (page) {
        0 -> {
          Column(modifier = Modifier.fillMaxWidth()) {
            content()
          }
        }
        1 -> PowerTab(powerSamples = powerSamples)
        2 -> TrainerTab()
      }
    }
  }
}

@Composable
private fun PowerTab(powerSamples: List<TelemetrySample>) {
  val powers = powerSamples.map { it.powerWatts }.filter { it > 0 }
  val avg3s = powers.takeLast(3).average().toInt()
  val avg = if (powers.isNotEmpty()) powers.average().toInt() else 0
  val max = if (powers.isNotEmpty()) powers.maxOrNull() ?: 0 else 0
  val hrs = powerSamples.map { it.hrBpm }.filter { it > 0 }
  val avgHr = if (hrs.isNotEmpty()) hrs.average().toInt() else 0
  val maxHr = if (hrs.isNotEmpty()) hrs.maxOrNull() ?: 0 else 0

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    StatRow(label = "3s Avg Power", value = "$avg3s W")
    StatRow(label = "Avg Power", value = "$avg W")
    StatRow(label = "Max Power", value = "$max W")
    StatRow(label = "Avg Heart Rate", value = if (avgHr > 0) "$avgHr bpm" else "—")
    StatRow(label = "Max Heart Rate", value = if (maxHr > 0) "$maxHr bpm" else "—")
  }
}

@Composable
private fun TrainerTab() {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    StatRow(label = "Resistance", value = "Auto")
    StatRow(label = "Power Smoothing", value = "3s")
    StatRow(label = "Temperature", value = "—")
    StatRow(label = "Connection", value = "Bluetooth LE")
    StatRow(label = "Control Mode", value = "ERG")
  }
}

@Composable
private fun StatRow(label: String, value: String) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Text(
        text = value,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold
      )
    }
  }
}
