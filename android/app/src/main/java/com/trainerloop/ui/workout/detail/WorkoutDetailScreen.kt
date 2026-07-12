package com.trainerloop.ui.workout.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainerloop.app.trainerLoopApp
import com.trainerloop.data.model.Workout
import com.trainerloop.ui.components.MetricTile
import com.trainerloop.ui.components.PrimaryActionButton
import com.trainerloop.ui.components.SectionHeader
import com.trainerloop.ui.components.TrainerLoopCard
import com.trainerloop.ui.components.TrainerLoopTopBar
import com.trainerloop.ui.components.WorkoutChart
import com.trainerloop.ui.theme.Spacing
import java.util.Locale

@Composable
fun WorkoutDetailScreen(
  workout: Workout,
  onStartWorkout: () -> Unit,
  onBack: () -> Unit,
  viewModel: WorkoutDetailViewModel = viewModel(factory = WorkoutDetailViewModelFactory(workout))
) {
  val context = LocalContext.current
  val ftp = remember { context.trainerLoopApp.profileRepository.getProfileSync().ftp }
  Scaffold(
    contentWindowInsets = WindowInsets(0),
    topBar = {
      TrainerLoopTopBar(
        title = "Workout Preview",
        onBack = onBack,
        windowInsets = WindowInsets(0)
      )
    },
    bottomBar = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(Spacing.screenMargin)
          .navigationBarsPadding()
      ) {
        PrimaryActionButton(onClick = onStartWorkout, modifier = Modifier.fillMaxWidth()) {
          Icon(Icons.Default.PlayArrow, contentDescription = null)
          Spacer(modifier = Modifier.width(Spacing.controlGap))
          Text("Start Workout")
        }
      }
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = Spacing.screenMargin),
      contentPadding = PaddingValues(bottom = Spacing.sectionGap),
      verticalArrangement = Arrangement.spacedBy(Spacing.sectionGap)
    ) {
      item {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
          Text(
            workout.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
          )
          Text(
            viewModel.category.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
          )
        }
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(Spacing.controlGap)
        ) {
          MetricTile(
            label = "Duration",
            value = formatDuration(viewModel.totalDurationSec),
            unit = "",
            modifier = Modifier.weight(1f)
          )
          MetricTile(
            label = "IF",
            value = String.format(Locale.ROOT, "%.2f", viewModel.stats.intensityFactor),
            unit = "",
            modifier = Modifier.weight(1f)
          )
          MetricTile(
            label = "TSS",
            value = viewModel.stats.tss.toString(),
            unit = "",
            modifier = Modifier.weight(1f)
          )
        }
      }

      item {
        TrainerLoopCard(modifier = Modifier.fillMaxWidth()) {
          WorkoutChart(
            segments = workout.segments,
            samples = emptyList(),
            elapsedSec = 0,
            ftp = ftp,
            modifier = Modifier.fillMaxWidth()
          )
        }
      }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.controlGap)) {
          SectionHeader(title = "Description")
          Text(
            workout.description ?: "No description available.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      item {
        SectionHeader(title = "Intervals")
      }

      items(viewModel.intervals) { interval ->
        IntervalRowItem(interval = interval)
      }
    }
  }
}

@Composable
private fun IntervalRowItem(interval: IntervalRow) {
  TrainerLoopCard(modifier = Modifier.fillMaxWidth(), emphasized = true) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(interval.color ?: MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(Spacing.md))
        Column {
          Text(interval.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
          Text(
            formatDuration(interval.durationSec),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      Text(
        interval.targetFtpPct,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold
      )
    }
  }
}

private fun formatDuration(totalSec: Int): String {
  val hours = totalSec / 3600
  val minutes = (totalSec % 3600) / 60
  val seconds = totalSec % 60
  return when {
    hours > 0 -> "${hours}h ${minutes}m"
    minutes > 0 -> "${minutes}m ${seconds}s"
    else -> "${seconds}s"
  }
}
