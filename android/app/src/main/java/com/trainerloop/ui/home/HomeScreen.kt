package com.trainerloop.ui.home

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainerloop.data.model.Workout
import com.trainerloop.domain.WorkoutMath
import com.trainerloop.ui.components.SecondaryActionButton
import com.trainerloop.ui.components.WorkoutMiniChart
import com.trainerloop.ui.theme.Spacing
import com.trainerloop.ui.theme.trainerLoopColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@SuppressLint("MissingPermission")
@Composable
fun HomeScreen(
  onNavigateToDevices: () -> Unit,
  onNavigateToWorkouts: () -> Unit,
  onChooseWorkout: () -> Unit,
  onStartPlanned: (Workout) -> Unit,
  onNavigateToProfile: () -> Unit,
  onNavigateToHistory: () -> Unit,
  onSessionClick: (String) -> Unit,
  viewModel: HomeViewModel = viewModel()
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val plannedReady by viewModel.plannedWorkoutReady.collectAsStateWithLifecycle()
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner, viewModel) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshIntervals()
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  LaunchedEffect(plannedReady) {
    plannedReady?.let {
      viewModel.consumePlannedWorkout()
      onStartPlanned(it)
    }
  }

  HomeScreenContent(
    uiState = uiState,
    onNavigateToDevices = onNavigateToDevices,
    onNavigateToWorkouts = onNavigateToWorkouts,
    onChooseWorkout = onChooseWorkout,
    onNavigateToProfile = onNavigateToProfile,
    onStartPlanned = viewModel::startPlanned,
    onRetryPlan = viewModel::refreshIntervals,
    onNavigateToHistory = onNavigateToHistory,
    onSessionClick = onSessionClick
  )
}

/** Focus Home shared by the production route and deterministic visual fixtures. */
@Composable
internal fun HomeScreenContent(
  uiState: HomeUiState,
  onNavigateToDevices: () -> Unit,
  onNavigateToWorkouts: () -> Unit,
  onChooseWorkout: () -> Unit,
  onNavigateToProfile: () -> Unit,
  onStartPlanned: () -> Unit,
  onRetryPlan: () -> Unit = {},
  onNavigateToHistory: () -> Unit = {},
  onSessionClick: (String) -> Unit = {},
  today: LocalDate = LocalDate.now()
) {
  val hasPlan = uiState.plannedName != null || uiState.plannedLoading || uiState.plannedError != null
  val semantic = MaterialTheme.trainerLoopColors
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = Spacing.screenMargin, vertical = Spacing.xl),
    verticalArrangement = Arrangement.spacedBy(Spacing.xl)
  ) {
    item {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text("TRAINERLOOP", style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        IconButton(
          onClick = onNavigateToProfile,
          modifier = Modifier.size(48.dp)
        ) {
          Icon(Icons.Default.Settings, contentDescription = "Open settings")
        }
      }
      Spacer(Modifier.height(Spacing.xl))
      Text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
        .uppercase(Locale.getDefault()), style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary)
      Spacer(Modifier.height(Spacing.sm))
      Text("One good ride.", style = MaterialTheme.typography.displayLarge)
      Spacer(Modifier.height(Spacing.sm))
      Text("Your time. Your training.", style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    item {
      Card(colors = CardDefaults.cardColors(containerColor = semantic.heroSurface),
        shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
          Text(if (hasPlan) "TODAY’S PLAN · INTERVALS.ICU" else "YOUR NEXT SESSION",
            style = MaterialTheme.typography.labelMedium, color = semantic.heroSecondary)
          Text(if (hasPlan) uiState.plannedName ?: if (uiState.plannedLoading) "Finding today’s workout"
            else "Plan unavailable" else "Choose your session",
            style = MaterialTheme.typography.headlineLarge, color = semantic.onHeroSurface)
          uiState.plannedWorkout?.let { workout ->
            Text(WorkoutMath.totalDurationSec(workout.segments).div(60).toString() + " min · " + workout.segments.size + " intervals",
              style = MaterialTheme.typography.bodyMedium, color = semantic.heroSecondary)
            WorkoutMiniChart(workout = workout, ftp = uiState.ftp, chartHeight = 96.dp,
              maxPowerAxis = 300, lineColor = semantic.heroAction)
          }
          if (!hasPlan) {
            Text("Structured workouts and the FTP test, all in one place.",
              style = MaterialTheme.typography.bodyLarge, color = semantic.heroSecondary)
          }
          if (uiState.plannedLoading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
              CircularProgressIndicator(Modifier.size(24.dp), color = semantic.heroAction)
              Text("Loading planned workout…", color = semantic.heroSecondary,
                style = MaterialTheme.typography.bodyMedium)
            }
          }
          uiState.plannedError?.let { error ->
            Text(error, color = semantic.onHeroSurface, style = MaterialTheme.typography.bodyMedium)
          }
          Button(
            onClick = when {
              uiState.plannedError != null -> onRetryPlan
              hasPlan -> onStartPlanned
              else -> onChooseWorkout
            },
            enabled = !uiState.plannedLoading,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = semantic.heroAction,
              contentColor = semantic.onHeroAction, disabledContainerColor = semantic.heroSurface,
              disabledContentColor = semantic.heroSecondary)
          ) {
            Text(when {
              uiState.plannedError != null -> "Retry plan"
              hasPlan -> "Open workout →"
              else -> "Choose workout →"
            })
          }
          if (hasPlan && !uiState.plannedLoading && uiState.plannedError == null) {
            Text("Press Play in the player to begin.", color = semantic.heroSecondary,
              style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }
    item {
      Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
          Text(if (uiState.isTrainerConnected) "Equipment connected" else "Connect your equipment",
            style = MaterialTheme.typography.titleMedium)
          Text(buildList {
            if (uiState.isTrainerConnected) add(uiState.trainerModel ?: "Trainer")
            if (uiState.isHrConnected) add("Heart rate")
            if (!uiState.isTrainerConnected) add("Trainer control unavailable")
          }.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onNavigateToDevices, modifier = Modifier.heightIn(min = 48.dp)) {
          Text("Manage →")
        }
      }
    }
    if (hasPlan) {
      item {
        SecondaryActionButton(onClick = onNavigateToWorkouts, modifier = Modifier.fillMaxWidth()) {
          Text("Choose a different workout")
        }
      }
    }
    item {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Last ride", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        TextButton(onClick = onNavigateToHistory) { Text("View history →") }
      }
      val session = uiState.recentSession
      if (session == null) {
        Text("Your first saved ride will appear here.", style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant)
      } else {
        Card(onClick = { onSessionClick(session.id) },
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.padding(Spacing.cardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(session.workoutName, style = MaterialTheme.typography.titleMedium)
            Text(sessionDate(session.startedAt) + " · " + session.durationSec / 60 + " min · Review →",
              style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }
  }
}

private fun sessionDate(startedAt: String): String = runCatching {
  Instant.parse(startedAt).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
}.getOrDefault(startedAt)
