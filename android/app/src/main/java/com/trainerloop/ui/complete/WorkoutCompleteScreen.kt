package com.trainerloop.ui.complete

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.ui.theme.Blue40
import com.trainerloop.ui.theme.Green40
import com.trainerloop.ui.theme.Red40

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutCompleteScreen(
  viewModel: WorkoutCompleteViewModel,
  onDiscard: () -> Unit,
  onDone: () -> Unit
) {
  val uiState by viewModel.uiState.collectAsState()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Workout Complete") },
        navigationIcon = {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
          )
        }
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp)
        .verticalScroll(rememberScrollState())
    ) {
      Text(
        text = uiState.workoutName,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = "${formatDuration(uiState.durationSec)} · ${uiState.avgPower} W avg",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Summary grid: TSS, IF, NP
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        SummaryCard(
          label = "TSS",
          value = uiState.tss.toString(),
          modifier = Modifier.weight(1f)
        )
        SummaryCard(
          label = "IF",
          value = "%.2f".format(uiState.intensityFactor),
          modifier = Modifier.weight(1f)
        )
        SummaryCard(
          label = "NP",
          value = "${uiState.normalizedPower} W",
          modifier = Modifier.weight(1f)
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Stats list
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          StatRow("Avg Power", "${uiState.avgPower} W")
          StatRow("Max Power", "${uiState.maxPower} W")
          StatRow("Avg Heart Rate", "${uiState.avgHr} bpm")
          StatRow("Avg Cadence", "${uiState.avgCadence} rpm")
          StatRow("Calories", "${uiState.calories} kcal")
          StatRow("Total Work", "${uiState.totalWorkKj} kJ")
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Post-ride chart with tabs
      if (viewModel.samples.isNotEmpty()) {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
          )
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              text = "Ride Chart",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            SampleChart(samples = viewModel.samples)
          }
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      // Actions
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        OutlinedButton(
          onClick = {
            viewModel.onDiscard()
            onDiscard()
          },
          modifier = Modifier.weight(1f),
          colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
          )
        ) {
          Text("Discard")
        }
        Button(
          onClick = { viewModel.onSave() },
          enabled = !uiState.isSaved,
          modifier = Modifier.weight(1f)
        ) {
          Text(if (uiState.isSaved) "Saved" else "Save")
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Button(
        onClick = { viewModel.onShare() },
        enabled = uiState.fitFile != null,
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("Share FIT")
      }

      uiState.uploadStatus?.let { status ->
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = status,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      OutlinedButton(
        onClick = onDone,
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("Done")
      }

      Spacer(modifier = Modifier.height(16.dp))
    }

    // Error snackbar
    uiState.error?.let { error ->
      Snackbar(
        modifier = Modifier.padding(16.dp),
        action = {
          TextButton(onClick = { viewModel.clearError() }) {
            Text("Dismiss")
          }
        }
      ) {
        Text(error)
      }
    }
  }
}

@Composable
private fun SummaryCard(
  label: String,
  value: String,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.primaryContainer
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        text = value,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimaryContainer
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer
      )
    }
  }
}

@Composable
private fun StatRow(label: String, value: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyLarge,
      fontWeight = FontWeight.Bold
    )
  }
}

@Composable
private fun SampleChart(samples: List<TelemetrySample>) {
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
    0 -> Triple(samples.map { it.powerWatts }, Blue40, "W")
    1 -> Triple(samples.map { it.hrBpm }, Red40, "bpm")
    else -> Triple(samples.map { it.cadenceRpm }, Green40, "rpm")
  }

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

    // Draw horizontal grid line at max
    drawLine(
      color = Color.White.copy(alpha = 0.3f),
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

private fun formatDuration(totalSec: Int): String {
  val h = totalSec / 3600
  val m = (totalSec % 3600) / 60
  val s = totalSec % 60
  return if (h > 0) "%d:%02d:%02d".format(h, m, s)
  else "%d:%02d".format(m, s)
}
