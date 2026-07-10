package com.trainerloop.ui.library

import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
  onStartRampTest: () -> Unit,
  viewModel: WorkoutLibraryViewModel = viewModel()
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current

  // Pick up workouts saved by the builder while this ViewModel was alive
  androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.refresh() }
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
      item(key = "ftp-ramp-test-card") {
        RampTestCard(onClick = onStartRampTest)
      }
      items(uiState.filteredWorkouts, key = { it.workout.id }) { item ->
        WorkoutCard(
          item = item,
          ftp = ftp,
          isFavorite = item.workout.id in uiState.favoriteIds,
          canDelete = item.workout.id in uiState.deletableIds,
          onClick = { onWorkoutSelected(item.workout) },
          onToggleFavorite = { viewModel.toggleFavorite(item.workout.id) },
          onDuplicate = { viewModel.duplicateWorkout(item.workout) },
          onDelete = { viewModel.deleteWorkout(item.workout.id) }
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
private fun RampTestCard(onClick: () -> Unit) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.primaryContainer
    )
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Text(
        text = "FTP Ramp Test",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onPrimaryContainer
      )
      Text(
        text = "Ramp to exhaustion — new FTP is 75% of your best 1-minute power",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer
      )
    }
  }
}

@Composable
private fun WorkoutCard(
  item: WorkoutListItem,
  ftp: Int,
  isFavorite: Boolean,
  canDelete: Boolean,
  onClick: () -> Unit,
  onToggleFavorite: () -> Unit,
  onDuplicate: () -> Unit,
  onDelete: () -> Unit
) {
  val workout = item.workout
  val stats = item.stats
  var menuOpen by remember { mutableStateOf(false) }

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

      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = workout.name,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onToggleFavorite) {
          Icon(
            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = if (isFavorite) "Unfavorite" else "Favorite",
            tint = if (isFavorite) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Box {
          IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More")
          }
          DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
              text = { Text("Duplicate") },
              onClick = { menuOpen = false; onDuplicate() }
            )
            if (canDelete) {
              DropdownMenuItem(
                text = { Text("Delete") },
                onClick = { menuOpen = false; onDelete() }
              )
            }
          }
        }
      }
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
