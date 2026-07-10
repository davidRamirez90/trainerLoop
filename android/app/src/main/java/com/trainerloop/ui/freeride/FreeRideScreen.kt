package com.trainerloop.ui.freeride

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.trainerloop.app.trainerLoopApp
import com.trainerloop.app.WorkoutForegroundService
import com.trainerloop.ui.components.RouteProfileChart
import com.trainerloop.ui.workout.WorkoutFinishData

@Composable
fun FreeRideScreen(
  viewModel: FreeRideViewModel,
  onSessionFinished: (WorkoutFinishData) -> Unit,
  onExit: () -> Unit
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val finishEvent by viewModel.finishEvent.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val view = LocalView.current
  var showStopConfirm by remember { mutableStateOf(false) }

  BackHandler(enabled = uiState.elapsedSec > 0) { showStopConfirm = true }

  LaunchedEffect(finishEvent) {
    finishEvent?.let {
      viewModel.consumeFinishEvent()
      onSessionFinished(it)
    }
  }

  DisposableEffect(Unit) {
    context.trainerLoopApp.volumeShiftHandler = { up ->
      if (up) viewModel.shiftUp() else viewModel.shiftDown()
    }
    onDispose { context.trainerLoopApp.volumeShiftHandler = null }
  }

  DisposableEffect(uiState.isRunning) {
    view.keepScreenOn = uiState.isRunning
    onDispose { view.keepScreenOn = false }
  }

  // Service lives while a session exists (running or paused); paused rides keep
  // process protection but the service holds no wake lock (isRunning = false).
  val sessionActive = uiState.elapsedSec > 0 || uiState.isRunning
  LaunchedEffect(sessionActive, uiState.isRunning) {
    if (sessionActive) {
      WorkoutForegroundService.start(
        context,
        uiState.currentPowerWatts,
        formatTime(uiState.elapsedSec)
      )
      if (!uiState.isRunning) {
        WorkoutForegroundService.update(
          context,
          uiState.currentPowerWatts,
          formatTime(uiState.elapsedSec),
          false
        )
      }
    } else {
      WorkoutForegroundService.stop(context)
    }
  }

  DisposableEffect(Unit) {
    onDispose { WorkoutForegroundService.stop(context) }
  }

  LaunchedEffect(Unit) {
    context.trainerLoopApp.stopRequests.collect { viewModel.stop() }
  }

  LaunchedEffect(uiState.currentPowerWatts, uiState.elapsedSec / 3) {
    if (uiState.isRunning) {
      WorkoutForegroundService.update(
        context,
        uiState.currentPowerWatts,
        formatTime(uiState.elapsedSec),
        true
      )
    }
  }

  if (showStopConfirm) {
    AlertDialog(
      onDismissRequest = { showStopConfirm = false },
      title = { Text("End ride?") },
      text = { Text("The ride so far will be saved.") },
      confirmButton = {
        TextButton(onClick = {
          showStopConfirm = false
          val hadSamples = uiState.samples.isNotEmpty()
          viewModel.stop()
          if (!hadSamples) onExit()
        }) { Text("End ride") }
      },
      dismissButton = {
        TextButton(onClick = { showStopConfirm = false }) { Text("Keep riding") }
      }
    )
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text(
      viewModel.route.name ?: "GPX Ride",
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.SemiBold
    )

    if (uiState.routeComplete) {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
      ) {
        Text(
          "Route complete — keep riding or stop to save.",
          modifier = Modifier.padding(12.dp),
          color = MaterialTheme.colorScheme.onPrimaryContainer
        )
      }
    }

    RouteProfileChart(points = viewModel.route.points, positionM = uiState.distanceM)

    // Gear + shift controls — large tap targets near the screen edges.
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      FilledTonalButton(
        onClick = { viewModel.shiftDown() },
        modifier = Modifier.size(width = 96.dp, height = 72.dp)
      ) {
        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Shift down")
      }
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("GEAR", style = MaterialTheme.typography.labelSmall)
        Text(
          "${uiState.gear}",
          style = MaterialTheme.typography.displayMedium,
          fontWeight = FontWeight.Bold
        )
      }
      FilledTonalButton(
        onClick = { viewModel.shiftUp() },
        modifier = Modifier.size(width = 96.dp, height = 72.dp)
      ) {
        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Shift up")
      }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      RideMetric("Speed", "%.1f".format(uiState.speedKph), "km/h", Modifier.weight(1f))
      RideMetric("Grade", "%.1f".format(uiState.gradePercent), "%", Modifier.weight(1f))
      RideMetric("To go", "%.1f".format(uiState.remainingM / 1000.0), "km", Modifier.weight(1f))
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      RideMetric("Power", "${uiState.currentPowerWatts}", "W", Modifier.weight(1f))
      RideMetric("Target", "${uiState.targetPowerWatts}", "W", Modifier.weight(1f))
      RideMetric(
        "HR",
        if (uiState.currentHrBpm > 0) "${uiState.currentHrBpm}" else "--",
        "bpm",
        Modifier.weight(1f)
      )
    }
    RideMetric("Time", formatTime(uiState.elapsedSec), "", Modifier.fillMaxWidth())

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      val resumable = uiState.elapsedSec > 0
      if (uiState.isRunning) {
        Button(onClick = { viewModel.pause() }, modifier = Modifier.weight(1f)) {
          Icon(Icons.Default.Pause, contentDescription = null)
          Spacer(modifier = Modifier.width(4.dp))
          Text("Pause")
        }
      } else {
        Button(
          onClick = { if (resumable) viewModel.resume() else viewModel.start() },
          modifier = Modifier.weight(1f)
        ) {
          Icon(Icons.Default.PlayArrow, contentDescription = null)
          Spacer(modifier = Modifier.width(4.dp))
          Text(if (resumable) "Resume" else "Start")
        }
      }
      Button(
        onClick = { if (uiState.elapsedSec == 0) onExit() else showStopConfirm = true },
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
      ) {
        Icon(Icons.Default.Stop, contentDescription = null)
        Spacer(modifier = Modifier.width(4.dp))
        Text("Stop")
      }
    }
  }
}

@Composable
private fun RideMetric(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
  Card(modifier = modifier) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(label, style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
      Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (unit.isNotEmpty()) {
          Spacer(modifier = Modifier.width(2.dp))
          Text(unit, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }
}

private fun formatTime(seconds: Int): String {
  val h = seconds / 3600
  val m = (seconds % 3600) / 60
  val s = seconds % 60
  return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
