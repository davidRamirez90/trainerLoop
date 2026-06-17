package com.trainerloop.ui.workout

import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainerloop.data.model.Workout
import com.trainerloop.ui.coach.CoachPanel
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.trainerloop.app.WorkoutForegroundService
import com.trainerloop.ui.components.IntervalTimeline
import com.trainerloop.ui.components.MetricCard

@Composable
fun WorkoutScreen(
  workout: Workout,
  viewModel: WorkoutViewModel = viewModel(
    factory = WorkoutViewModelFactory(workout)
  ),
  onFinish: () -> Unit
) {
  val uiState by viewModel.uiState.collectAsState()
  val view = LocalView.current
  val context = LocalContext.current

  // Keep screen on during workout
  DisposableEffect(uiState.isRunning) {
    if (uiState.isRunning) {
      view.keepScreenOn = true
    } else {
      view.keepScreenOn = false
    }
    onDispose {
      view.keepScreenOn = false
    }
  }

  // Foreground service: start/stop based on running state
  LaunchedEffect(uiState.isRunning) {
    if (uiState.isRunning) {
      val timeStr = formatDuration(uiState.elapsedSec)
      WorkoutForegroundService.start(context, uiState.currentPowerWatts, timeStr)
    } else {
      WorkoutForegroundService.stop(context)
    }
  }

  // Update notification periodically during workout
  LaunchedEffect(uiState.currentPowerWatts, uiState.elapsedSec) {
    if (uiState.isRunning) {
      val timeStr = formatDuration(uiState.elapsedSec)
      WorkoutForegroundService.update(context, uiState.currentPowerWatts, timeStr, true)
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp)
  ) {
    // Header
    Text(
      text = workout.name,
      style = MaterialTheme.typography.headlineMedium
    )

    Spacer(modifier = Modifier.height(8.dp))

    // Timeline
    IntervalTimeline(
      segments = workout.segments,
      currentIndex = uiState.segmentIndex,
      modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Main metrics row
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      MetricCard(
        label = "Power",
        value = uiState.currentPowerWatts.toString(),
        unit = "W",
        modifier = Modifier.weight(1f),
        isHighlighted = uiState.isRunning
      )
      MetricCard(
        label = "Cadence",
        value = uiState.currentCadenceRpm.toString(),
        unit = "RPM",
        modifier = Modifier.weight(1f)
      )
      MetricCard(
        label = "HR",
        value = uiState.currentHrBpm.toString(),
        unit = "BPM",
        modifier = Modifier.weight(1f)
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Target / difficulty row
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      MetricCard(
        label = "Target",
        value = uiState.targetRange.let { "${it.low}-${it.high}" },
        unit = "W",
        modifier = Modifier.weight(1f),
        isHighlighted = true
      )
      MetricCard(
        label = "Elapsed",
        value = formatDuration(uiState.elapsedSec),
        modifier = Modifier.weight(1f)
      )
      MetricCard(
        label = if (uiState.isRunning) "Active" else "Timer",
        value = formatDuration(uiState.activeSec),
        modifier = Modifier.weight(1f)
      )
    }

    Spacer(modifier = Modifier.height(4.dp))

    // Segment info
    workout.segments.getOrNull(uiState.segmentIndex)?.let { segment ->
      Text(
        text = segment.label ?: segment.phase.name,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Text(
        text = "Segment ${uiState.segmentIndex + 1}/${workout.segments.size} \u2022 ${formatDuration(uiState.segmentEndSec - uiState.segmentStartSec)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    Spacer(modifier = Modifier.height(4.dp))

    // Intensity offset
    if (uiState.intensityOffsetPct != 0) {
      Text(
        text = "Intensity: ${if (uiState.intensityOffsetPct > 0) "+" else ""}${uiState.intensityOffsetPct}%",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.error
      )
    }

    // Coach panel (collapsible — shown when there's a suggestion)
    if (uiState.pendingSuggestion != null || uiState.coachEvents.isNotEmpty()) {
      CoachPanel(
        pendingSuggestion = uiState.pendingSuggestion,
        events = uiState.coachEvents,
        onAccept = { uiState.pendingSuggestion?.let { viewModel.acceptSuggestion(it.id) } },
        onReject = { uiState.pendingSuggestion?.let { viewModel.rejectSuggestion(it.id) } }
      )
      Spacer(modifier = Modifier.height(8.dp))
    }

    Spacer(modifier = Modifier.weight(1f))

    // Controls
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      when {
        !uiState.isRunning && !uiState.isComplete -> {
          Button(
            onClick = { viewModel.start() },
            modifier = Modifier.weight(1f)
          ) {
            Text("Start")
          }
        }
        uiState.isRunning -> {
          Button(
            onClick = { viewModel.pause() },
            modifier = Modifier.weight(1f)
          ) {
            Text("Pause")
          }
          Button(
            onClick = { viewModel.stop() },
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier.weight(1f)
          ) {
            Text("Stop")
          }
        }
        !uiState.isRunning && !uiState.isComplete -> {
          Button(
            onClick = { viewModel.resume() },
            modifier = Modifier.weight(1f)
          ) {
            Text("Resume")
          }
          Button(
            onClick = { viewModel.stop() },
            modifier = Modifier.weight(1f)
          ) {
            Text("Stop")
          }
        }
      }
      if (uiState.isComplete) {
        Button(
          onClick = onFinish,
          modifier = Modifier.weight(1f)
        ) {
          Text("Finish")
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Secondary controls row
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      FilledTonalButton(
        onClick = { viewModel.toggleErg() },
        modifier = Modifier.weight(1f)
      ) {
        Text(if (uiState.isErgEnabled) "ERG: ON" else "ERG: OFF")
      }
      FilledTonalButton(
        onClick = { viewModel.adjustIntensityDown() },
        enabled = uiState.intensityOffsetPct > -20
      ) {
        Text("-5%")
      }
      FilledTonalButton(
        onClick = { viewModel.adjustIntensityUp() },
        enabled = uiState.intensityOffsetPct < 20
      ) {
        Text("+5%")
      }
    }

    Spacer(modifier = Modifier.height(4.dp))

    // Seek bar
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      // Quick seek buttons at 25% increments
      val totalDur = workout.segments.sumOf { it.durationSec }
      listOf(0, 25, 50, 75, 90).forEach { pct ->
        val sec = totalDur * pct / 100
        FilledTonalButton(
          onClick = { viewModel.seek(sec) },
          modifier = Modifier.weight(1f)
        ) {
          Text(
            text = "${pct}%",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
          )
        }
      }
    }
  }
}

private fun formatDuration(totalSec: Int): String {
  val min = totalSec / 60
  val sec = totalSec % 60
  return "%d:%02d".format(min, sec)
}
