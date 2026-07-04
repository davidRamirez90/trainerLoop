package com.trainerloop.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainerloop.data.model.SessionSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(
  viewModel: HistoryViewModel = viewModel()
) {
  val sessions by viewModel.sessions.collectAsState()

  if (sessions.isEmpty()) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = "No saved workouts yet.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    return
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    item {
      Text(
        text = "History",
        style = MaterialTheme.typography.headlineLarge
      )
    }
    items(sessions, key = { it.id }) { session ->
      SessionCard(session)
    }
  }
}

@Composable
private fun SessionCard(session: SessionSummary) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = session.workoutName,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
      )
      Spacer(modifier = Modifier.height(4.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        Text(
          text = formatSessionDate(session.startedAt),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = formatDuration(session.durationSec),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (session.avgPower > 0) {
          Text(
            text = "${session.avgPower} W avg",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        if (session.avgHr > 0) {
          Text(
            text = "${session.avgHr} bpm avg",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}

private fun formatSessionDate(startedAt: String): String {
  return try {
    val instant = Instant.parse(startedAt)
    DateTimeFormatter.ofPattern("MMM d, yyyy")
      .withZone(ZoneId.systemDefault())
      .format(instant)
  } catch (_: Exception) {
    startedAt
  }
}

private fun formatDuration(seconds: Int): String {
  val hours = seconds / 3600
  val minutes = (seconds % 3600) / 60
  val secs = seconds % 60
  return if (hours > 0) {
    "$hours:${minutes.pad2()}:${secs.pad2()}"
  } else {
    "$minutes:${secs.pad2()}"
  }
}

private fun Int.pad2(): String = if (this < 10) "0$this" else "$this"
