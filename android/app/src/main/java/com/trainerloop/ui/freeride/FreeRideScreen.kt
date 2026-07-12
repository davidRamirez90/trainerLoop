package com.trainerloop.ui.freeride

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.unit.IntSize
import com.trainerloop.app.trainerLoopApp
import com.trainerloop.app.WorkoutForegroundService
import com.trainerloop.ui.components.RouteProfileChart
import com.trainerloop.ui.components.MetricTile
import com.trainerloop.ui.components.MetricTileState
import com.trainerloop.ui.workout.WorkoutFinishData
import com.trainerloop.ui.theme.MotionSpec
import com.trainerloop.ui.theme.reducedMotionAware

@Composable
fun FreeRideScreen(
  viewModel: FreeRideViewModel,
  onSessionFinished: (WorkoutFinishData) -> Unit,
  onExit: () -> Unit
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val fastMotionSpec = reducedMotionAware(MotionSpec.fast)
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
        TextButton(
          onClick = {
            showStopConfirm = false
            val hadSamples = uiState.samples.isNotEmpty()
            viewModel.stop()
            if (!hadSamples) onExit()
          },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) { Text("End ride") }
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
      .padding(16.dp)
      .navigationBarsPadding(),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text(
      viewModel.route.name ?: "GPX Ride",
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.SemiBold
    )

    AnimatedVisibility(
      visible = uiState.routeComplete,
      enter = expandVertically(
        animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
      ) +
        fadeIn(animationSpec = reducedMotionAware(MotionSpec.default)),
      exit = shrinkVertically(
        animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
      ) +
        fadeOut(animationSpec = reducedMotionAware(MotionSpec.default))
    ) {
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
          style = MaterialTheme.typography.displayMedium.copy(
            fontWeight = FontWeight.Bold,
            fontFeatureSettings = "tnum"
          )
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
      MetricTile(
        label = "Speed",
        value = "%.1f".format(uiState.speedKph),
        unit = "km/h",
        modifier = Modifier.weight(1f)
      )
      MetricTile(
        label = "Grade",
        value = "%.1f".format(uiState.gradePercent),
        unit = "%",
        modifier = Modifier.weight(1f)
      )
      MetricTile(
        label = "To go",
        value = "%.1f".format(uiState.remainingM / 1000.0),
        unit = "km",
        modifier = Modifier.weight(1f)
      )
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      MetricTile(
        label = "Power",
        value = uiState.currentPowerWatts.toString(),
        unit = "W",
        modifier = Modifier.weight(1f)
      )
      MetricTile(
        label = "Target",
        value = uiState.targetPowerWatts.toString(),
        unit = "W",
        state = if (uiState.targetPowerWatts <= 0) MetricTileState.Unavailable else MetricTileState.Available,
        modifier = Modifier.weight(1f)
      )
      MetricTile(
        label = "HR",
        value = uiState.currentHrBpm.toString(),
        unit = "bpm",
        state = if (uiState.currentHrBpm <= 0) MetricTileState.Unavailable else MetricTileState.Available,
        modifier = Modifier.weight(1f)
      )
    }
    MetricTile(
      label = "Time",
      value = formatTime(uiState.elapsedSec),
      unit = "",
      modifier = Modifier.fillMaxWidth()
    )

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      val resumable = uiState.elapsedSec > 0
      Button(
        onClick = {
          if (uiState.isRunning) viewModel.pause()
          else if (resumable) viewModel.resume() else viewModel.start()
        },
        modifier = Modifier.heightIn(min = 56.dp).weight(1f)
      ) {
        AnimatedContent(
          targetState = uiState.isRunning to resumable,
          transitionSpec = {
            fadeIn(animationSpec = fastMotionSpec) togetherWith
              fadeOut(animationSpec = fastMotionSpec)
          },
          label = "free-ride-transport"
        ) { state ->
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              if (state.first) Icons.Default.Pause else Icons.Default.PlayArrow,
              contentDescription = if (state.first) "Pause" else "Start"
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (state.first) "Pause" else if (state.second) "Resume" else "Start")
          }
        }
      }
      // Destructive red is reserved for the confirm step; the always-visible
      // transport Stop stays tonal, matching the structured player.
      FilledTonalButton(
        onClick = { if (uiState.elapsedSec == 0) onExit() else showStopConfirm = true },
        modifier = Modifier.heightIn(min = 48.dp)
      ) {
        Icon(Icons.Default.Stop, contentDescription = null)
        Spacer(modifier = Modifier.width(4.dp))
        Text("Stop")
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
