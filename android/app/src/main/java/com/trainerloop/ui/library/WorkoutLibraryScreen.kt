package com.trainerloop.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainerloop.data.model.Workout

@Composable
fun WorkoutLibraryScreen(
  onStartWorkout: (Workout) -> Unit,
  viewModel: WorkoutLibraryViewModel = viewModel()
) {
  val uiState by viewModel.uiState.collectAsState()
  val context = LocalContext.current

  val importLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null) {
      viewModel.importWorkout(uri)
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        text = "Workouts",
        style = MaterialTheme.typography.headlineLarge
      )
      Button(onClick = {
        importLauncher.launch(arrayOf("*/*"))
      }) {
        Text("Import")
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    if (uiState.isLoading) {
      CircularProgressIndicator()
    }

    LazyColumn(
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(uiState.workouts) { workout ->
        WorkoutCard(
          workout = workout,
          onClick = { onStartWorkout(workout) }
        )
      }
    }
  }

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
private fun WorkoutCard(
  workout: Workout,
  onClick: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp)
    ) {
      Text(
        text = workout.name,
        style = MaterialTheme.typography.titleMedium
      )
      if (workout.description != null) {
        Text(
          text = workout.description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Text(
        text = "${workout.segments.size} segments \u2022 ${formatDuration(workout.segments.sumOf { it.durationSec })}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

private fun formatDuration(totalSec: Int): String {
  val h = totalSec / 3600
  val m = (totalSec % 3600) / 60
  return if (h > 0) "${h}h ${m}m" else "${m}m"
}
