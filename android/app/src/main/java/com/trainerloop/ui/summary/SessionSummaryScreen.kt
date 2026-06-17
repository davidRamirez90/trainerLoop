package com.trainerloop.ui.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SessionSummaryScreen(
  viewModel: SessionSummaryViewModel,
  onDone: () -> Unit
) {
  val uiState by viewModel.uiState.collectAsState()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp)
  ) {
    Text(
      text = "Session Summary",
      style = MaterialTheme.typography.headlineLarge
    )

    if (uiState.workoutName.isNotBlank()) {
      Text(
        text = uiState.workoutName,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Summary stats
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant
      )
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp)
      ) {
        StatRow("Duration", formatDuration(uiState.durationSec))
        Spacer(modifier = Modifier.height(8.dp))
        StatRow("Avg Power", "${uiState.avgPower} W")
        StatRow("Max Power", "${uiState.maxPower} W")
        Spacer(modifier = Modifier.height(8.dp))
        StatRow("Avg Cadence", "${uiState.avgCadence} RPM")
        StatRow("Avg HR", "${uiState.avgHr} BPM")
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Status
    if (uiState.isSaved) {
      Text(
        text = "Session saved",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary
      )
    }

    Spacer(modifier = Modifier.weight(1f))

    // Actions
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Button(
        onClick = { viewModel.shareFit() },
        enabled = uiState.fitFile != null,
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("Share FIT File")
      }

      OutlinedButton(
        onClick = onDone,
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("Done")
      }
    }
  }

  // Error snackbar
  uiState.error?.let { error ->
    Snackbar(
      modifier = Modifier.padding(16.dp),
      action = {
        androidx.compose.material3.TextButton(onClick = { viewModel.clearError() }) {
          Text("Dismiss")
        }
      }
    ) {
      Text(error)
    }
  }
}

@Composable
private fun StatRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp)
    )
  }
}

private fun formatDuration(totalSec: Int): String {
  val h = totalSec / 3600
  val m = (totalSec % 3600) / 60
  val s = totalSec % 60
  return if (h > 0) "%d:%02d:%02d".format(h, m, s)
  else "%d:%02d".format(m, s)
}
