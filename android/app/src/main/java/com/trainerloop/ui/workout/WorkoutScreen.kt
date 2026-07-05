package com.trainerloop.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainerloop.app.WorkoutForegroundService
import com.trainerloop.data.model.Workout
import com.trainerloop.domain.WorkoutMath
import com.trainerloop.ui.components.WorkoutChart
import com.trainerloop.ui.components.zoneColor
import com.trainerloop.ui.theme.Green40
import com.trainerloop.ui.workout.WorkoutStatsPager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
  workout: Workout,
  viewModel: WorkoutViewModel,
  onSessionFinished: (WorkoutFinishData) -> Unit,
  onExit: () -> Unit
) {
  val uiState by viewModel.uiState.collectAsState()
  val finishData by viewModel.finishEvent.collectAsState()
  val view = LocalView.current
  val context = LocalContext.current
  val ftp = remember { com.trainerloop.data.repository.ProfileRepository(context).getProfileSync().ftp }
  var showStopConfirm by remember { mutableStateOf(false) }

  fun requestStop() {
    if (uiState.elapsedSec == 0) onExit() else showStopConfirm = true
  }

  BackHandler(enabled = uiState.elapsedSec > 0) { showStopConfirm = true }

  LaunchedEffect(finishData) {
    finishData?.let {
      viewModel.consumeFinishEvent()
      onSessionFinished(it)
    }
  }

  DisposableEffect(uiState.isRunning) {
    view.keepScreenOn = uiState.isRunning
    onDispose { view.keepScreenOn = false }
  }

  LaunchedEffect(uiState.isRunning) {
    if (uiState.isRunning) {
      val timeStr = formatDuration(uiState.elapsedSec)
      WorkoutForegroundService.start(context, uiState.currentPowerWatts, timeStr)
    } else {
      WorkoutForegroundService.stop(context)
    }
  }

  LaunchedEffect(uiState.currentPowerWatts, uiState.elapsedSec) {
    if (uiState.isRunning) {
      val timeStr = formatDuration(uiState.elapsedSec)
      WorkoutForegroundService.update(context, uiState.currentPowerWatts, timeStr, true)
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(workout.name) },
        navigationIcon = {
          IconButton(onClick = { requestStop() }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          SuggestionChip(
            onClick = { viewModel.toggleErg() },
            label = {
              Text(
                text = if (uiState.isErgEnabled) "ERG ON" else "ERG OFF",
                color = if (uiState.isErgEnabled) Green40 else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          )
          Spacer(modifier = Modifier.width(8.dp))
        }
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp)
    ) {
      // Top bar: elapsed / target
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "DURATION",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = formatDuration(uiState.elapsedSec),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
          )
        }
        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = "TARGET",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = "${uiState.targetRange.low}-${uiState.targetRange.high} W",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Main metrics row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        BigMetric(
          label = "Power",
          value = uiState.currentPowerWatts.toString(),
          unit = "W",
          modifier = Modifier.weight(1f),
          highlight = true,
          valueColor = zoneColor(uiState.currentPowerWatts, ftp).copy(alpha = 1f)
        )
        BigMetric(
          label = "HR",
          value = if (uiState.currentHrBpm > 0) uiState.currentHrBpm.toString() else "—",
          unit = "bpm",
          modifier = Modifier.weight(1f)
        )
        BigMetric(
          label = "Cadence",
          value = if (uiState.currentCadenceRpm > 0) uiState.currentCadenceRpm.toString() else "—",
          unit = "rpm",
          modifier = Modifier.weight(1f)
        )
        BigMetric(
          label = "To Interval",
          value = formatShortDuration((uiState.segmentEndSec - uiState.elapsedSec).coerceAtLeast(0)),
          unit = "",
          modifier = Modifier.weight(1f)
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Chart section with tabs
      WorkoutStatsPager(
        powerSamples = uiState.samples,
        modifier = Modifier.fillMaxWidth()
      ) {
        val currentSegment = workout.segments.getOrNull(uiState.segmentIndex)
        Text(
          text = currentSegment?.label ?: currentSegment?.phase?.name ?: "",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold
        )
        Text(
          text = "Segment ${uiState.segmentIndex + 1}/${workout.segments.size}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        WorkoutChart(
          workout = workout,
          samples = uiState.samples,
          elapsedSec = uiState.elapsedSec,
          ftp = ftp,
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          FooterStat("Elapsed", formatDuration(uiState.elapsedSec))
          FooterStat(
            "Remaining",
            formatDuration((WorkoutMath.totalDurationSec(workout.segments) - uiState.elapsedSec).coerceAtLeast(0))
          )
          FooterStat("Total", formatDuration(WorkoutMath.totalDurationSec(workout.segments)))
        }
      }

      Spacer(modifier = Modifier.weight(1f))

      // Intensity controls
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        FilledTonalButton(
          onClick = { viewModel.adjustIntensityDown() },
          modifier = Modifier.weight(1f)
        ) {
          Text("-5%", style = MaterialTheme.typography.labelSmall)
        }
        FilledTonalButton(
          onClick = { viewModel.fineIntensityDown() },
          modifier = Modifier.weight(1f)
        ) {
          Text("-1%", style = MaterialTheme.typography.labelSmall)
        }
        Text(
          text = "${if (uiState.intensityOffsetPct >= 0) "+" else ""}${uiState.intensityOffsetPct}%",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.align(Alignment.CenterVertically)
        )
        FilledTonalButton(
          onClick = { viewModel.fineIntensityUp() },
          modifier = Modifier.weight(1f)
        ) {
          Text("+1%", style = MaterialTheme.typography.labelSmall)
        }
        FilledTonalButton(
          onClick = { viewModel.adjustIntensityUp() },
          modifier = Modifier.weight(1f)
        ) {
          Text("+5%", style = MaterialTheme.typography.labelSmall)
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Main controls
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        when {
          !uiState.isRunning && !uiState.isComplete -> {
            Button(
              onClick = { viewModel.start() },
              modifier = Modifier.weight(1f)
            ) {
              Icon(Icons.Default.PlayArrow, contentDescription = null)
              Spacer(modifier = Modifier.width(4.dp))
              Text("Start")
            }
          }
          uiState.isRunning -> {
            Button(
              onClick = { viewModel.pause() },
              modifier = Modifier.weight(1f)
            ) {
              Icon(Icons.Default.Pause, contentDescription = null)
              Spacer(modifier = Modifier.width(4.dp))
              Text("Pause")
            }
          }
          else -> {
            Button(
              onClick = { viewModel.resume() },
              modifier = Modifier.weight(1f)
            ) {
              Icon(Icons.Default.PlayArrow, contentDescription = null)
              Spacer(modifier = Modifier.width(4.dp))
              Text("Resume")
            }
          }
        }

        FilledTonalButton(
          onClick = { viewModel.skipSegment() },
          enabled = uiState.segmentEndSec < WorkoutMath.totalDurationSec(workout.segments)
        ) {
          Icon(Icons.Default.SkipNext, contentDescription = null)
          Spacer(modifier = Modifier.width(4.dp))
          Text("Skip")
        }

        Button(
          onClick = { requestStop() },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
          Icon(Icons.Default.Stop, contentDescription = null)
        }
      }

      if (uiState.isComplete) {
        Spacer(modifier = Modifier.height(12.dp))
        Button(
          onClick = { viewModel.maybeEmitFinish() },
          modifier = Modifier.fillMaxWidth()
        ) {
          Text("Finish Workout")
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
    }
  }

  if (showStopConfirm) {
    AlertDialog(
      onDismissRequest = { showStopConfirm = false },
      title = { Text("End workout?") },
      text = { Text("Your ride will be saved.") },
      confirmButton = {
        TextButton(onClick = {
          showStopConfirm = false
          viewModel.stop()
        }) {
          Text("End")
        }
      },
      dismissButton = {
        TextButton(onClick = { showStopConfirm = false }) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
private fun BigMetric(
  label: String,
  value: String,
  unit: String,
  modifier: Modifier = Modifier,
  highlight: Boolean = false,
  valueColor: androidx.compose.ui.graphics.Color? = null
) {
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(
      containerColor = if (highlight) MaterialTheme.colorScheme.primaryContainer
      else MaterialTheme.colorScheme.surfaceVariant
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Row(verticalAlignment = Alignment.Bottom) {
        Text(
          text = value,
          style = MaterialTheme.typography.displaySmall.copy(
            fontFeatureSettings = "tnum"
          ),
          fontWeight = FontWeight.Bold,
          color = valueColor
            ?: if (highlight) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
          text = unit,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
private fun FooterStat(label: String, value: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.SemiBold
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

private fun formatDuration(totalSec: Int): String {
  val min = totalSec / 60
  val sec = totalSec % 60
  return "%d:%02d".format(min, sec)
}

private fun formatShortDuration(totalSec: Int): String {
  val min = totalSec / 60
  val sec = totalSec % 60
  return if (min > 0) "${min}m" else "${sec}s"
}
