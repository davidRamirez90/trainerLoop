package com.trainerloop.ui.library

import java.util.Locale
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainerloop.data.model.Workout
import com.trainerloop.data.repository.ProfileRepository
import com.trainerloop.ui.components.WorkoutMiniChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutLibraryScreen(
  onWorkoutSelected: (Workout) -> Unit,
  viewModel: WorkoutLibraryViewModel = viewModel()
) {
  val uiState by viewModel.uiState.collectAsState()
  val context = LocalContext.current
  val ftp = remember { ProfileRepository(context).getProfileSync().ftp }

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
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Workouts",
        style = MaterialTheme.typography.headlineLarge
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (uiState.canSync) {
          Button(onClick = { viewModel.sync() }, enabled = !uiState.isSyncing) {
            Text(if (uiState.isSyncing) "Syncing…" else "Sync")
          }
        }
        Button(onClick = {
          importLauncher.launch(arrayOf("*/*"))
        }) {
          Text("Import")
        }
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
      value = uiState.searchQuery,
      onValueChange = viewModel::onSearchQueryChange,
      modifier = Modifier.fillMaxWidth(),
      placeholder = { Text("Search workouts") },
      leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
      singleLine = true
    )

    Spacer(modifier = Modifier.height(12.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      uiState.categories.forEach { category ->
        FilterChip(
          selected = uiState.selectedCategory == category,
          onClick = { viewModel.onCategorySelected(category) },
          label = { Text(category.label) }
        )
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    if (uiState.isLoading) {
      CircularProgressIndicator()
    }

    LazyColumn(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      items(uiState.filteredWorkouts, key = { it.workout.id }) { item ->
        WorkoutCard(
          item = item,
          ftp = ftp,
          onClick = { onWorkoutSelected(item.workout) }
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
  item: WorkoutListItem,
  ftp: Int,
  onClick: () -> Unit
) {
  val workout = item.workout
  val stats = item.stats

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
      WorkoutMiniChart(
        workout = workout,
        ftp = ftp,
        modifier = Modifier.fillMaxWidth(),
        chartHeight = 60.dp
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = workout.name,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
      )
      if (workout.description != null) {
        Text(
          text = workout.description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        Text(
          text = "${formatDuration(stats.durationSec)}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = "IF ${String.format(Locale.ROOT, "%.2f", stats.intensityFactor)}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = "TSS ${stats.tss}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

private fun formatDuration(totalSec: Int): String {
  val h = totalSec / 3600
  val m = (totalSec % 3600) / 60
  return if (h > 0) "${h}h ${m}m" else "${m}m"
}
