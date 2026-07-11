package com.trainerloop.ui.workout.detail

import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trainerloop.app.trainerLoopApp
import com.trainerloop.data.model.Workout
import com.trainerloop.ui.components.WorkoutMiniChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
  workout: Workout,
  onStartWorkout: () -> Unit,
  onBack: () -> Unit,
  viewModel: WorkoutDetailViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
    factory = WorkoutDetailViewModelFactory(workout)
  )
) {
  val context = LocalContext.current
  val ftp = remember { context.trainerLoopApp.profileRepository.getProfileSync().ftp }
  Scaffold(
    contentWindowInsets = WindowInsets(0),
    topBar = {
      TopAppBar(
        windowInsets = WindowInsets(0),
        title = { Text("Workout Preview") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back"
            )
          }
        }
      )
    },
    bottomBar = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp)
          .navigationBarsPadding()
      ) {
        Button(
          onClick = onStartWorkout,
          modifier = Modifier.fillMaxWidth()
        ) {
          Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp)
          )
          Text("Start Workout")
        }
      }
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      item {
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = workout.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = viewModel.category.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
          )
        }
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly
        ) {
          SummaryPill(label = "Duration", value = formatDuration(viewModel.totalDurationSec))
          SummaryPill(label = "IF", value = String.format(Locale.ROOT, "%.2f", viewModel.stats.intensityFactor))
          SummaryPill(label = "TSS", value = "${viewModel.stats.tss}")
        }
      }

      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
          )
        ) {
          WorkoutMiniChart(
            workout = workout,
            ftp = ftp,
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            chartHeight = 160.dp
          )
        }
      }

      item {
        Text(
          text = "Description",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold
        )
        Text(
          text = workout.description ?: "No description available.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      item {
        Text(
          text = "Intervals",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold
        )
      }

      items(viewModel.intervals) { interval ->
        IntervalRowItem(interval = interval)
      }

      item {
        Spacer(modifier = Modifier.height(80.dp))
      }
    }
  }
}

@Composable
private fun SummaryPill(
  label: String,
  value: String
) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = value,
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun IntervalRowItem(interval: IntervalRow) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(interval.color)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text(
            text = interval.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
          )
          Text(
            text = formatDuration(interval.durationSec),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      Text(
        text = interval.targetFtpPct,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold
      )
    }
  }
}

private fun formatDuration(totalSec: Int): String {
  val h = totalSec / 3600
  val m = (totalSec % 3600) / 60
  val s = totalSec % 60
  return when {
    h > 0 -> "${h}h ${m}m"
    m > 0 -> "${m}m ${s}s"
    else -> "${s}s"
  }
}
