package com.trainerloop.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import android.annotation.SuppressLint
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainerloop.data.model.SessionSummary
import com.trainerloop.ui.components.MetricBadge
import com.trainerloop.ui.components.pressable
import com.trainerloop.ui.theme.Green20
import com.trainerloop.ui.theme.Green40
import com.trainerloop.ui.theme.Spacing

@SuppressLint("MissingPermission")
@Composable
fun HomeScreen(
  onNavigateToDevices: () -> Unit,
  onNavigateToWorkouts: () -> Unit,
  onNavigateToBuilder: () -> Unit,
  onStartFreeRide: () -> Unit,
  onStartPlanned: (com.trainerloop.data.model.Workout) -> Unit,
  onGpxRoutes: () -> Unit,
  viewModel: HomeViewModel = viewModel()
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val plannedReady by viewModel.plannedWorkoutReady.collectAsStateWithLifecycle()

  LaunchedEffect(plannedReady) {
    plannedReady?.let {
      viewModel.consumePlannedWorkout()
      onStartPlanned(it)
    }
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(Spacing.lg),
    verticalArrangement = Arrangement.spacedBy(Spacing.lg)
  ) {
    item {
      RiderHeader(
        name = uiState.riderName,
        ftp = uiState.ftp,
        weightKg = uiState.weightKg
      )
    }

    item {
      StartRideHero(onStartFreeRide = onStartFreeRide)
    }

    val hasPlan = uiState.plannedName != null || uiState.plannedLoading || uiState.plannedError != null
    if (hasPlan) {
      item {
        PlannedWorkoutCard(
          name = uiState.plannedName,
          workout = uiState.plannedWorkout,
          ftp = uiState.ftp,
          loading = uiState.plannedLoading,
          error = uiState.plannedError,
          onStart = { viewModel.startPlanned() }
        )
      }
    }

    item {
      DeviceStatusRow(
        trainerName = uiState.connectedTrainer?.name,
        trainerConnected = uiState.isTrainerConnected,
        trainerBattery = uiState.trainerBattery,
        hrConnected = uiState.isHrConnected,
        latestHrBpm = uiState.latestHrBpm,
        onManageDevices = onNavigateToDevices
      )
    }

    item {
      ActionRows(
        onWorkoutLibrary = onNavigateToWorkouts,
        onWorkoutBuilder = onNavigateToBuilder,
        onGpxRoutes = onGpxRoutes
      )
    }

    // Today's plan takes over this slot when present — recent history only
    // shows on rest days / when no workout is scheduled.
    if (!hasPlan) {
      item {
        Text(
          text = "Recent Workouts",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.SemiBold
        )
      }

      val recentSession = uiState.recentSession
      if (recentSession == null) {
        item {
          Text(
            text = "No saved workouts yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      } else {
        item {
          RecentSessionCard(session = recentSession)
        }
      }
    }
  }
}

@Composable
private fun RiderHeader(
  name: String,
  ftp: Int,
  weightKg: Double
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(56.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.primaryContainer),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = name.take(1).uppercase().takeIf { it.isNotBlank() } ?: "?",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer
      )
    }

    Spacer(modifier = Modifier.width(16.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = name,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
      )
      Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        MetricBadge(label = "FTP", value = "$ftp W")
        MetricBadge(label = "Weight", value = "${"%.1f".format(weightKg)} kg")
      }
    }
  }
}

@Composable
private fun StartRideHero(onStartFreeRide: () -> Unit) {
  val interactionSource = remember { MutableInteractionSource() }

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .background(Brush.linearGradient(listOf(Green20, Green40)))
        .padding(Spacing.xl)
    ) {
      Text(
        text = "Ready to ride?",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = Color.White
      )
      Text(
        text = "Jump on the trainer and go",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.8f)
      )
      Spacer(modifier = Modifier.height(Spacing.lg))
      Button(
        onClick = onStartFreeRide,
        modifier = Modifier
          .pressable(interactionSource)
          .fillMaxWidth(),
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
          containerColor = Color.White,
          contentColor = Green20
        )
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
          contentDescription = null,
          modifier = Modifier.padding(end = 8.dp)
        )
        Text("Start Free Ride", fontWeight = FontWeight.SemiBold)
      }
    }
  }
}

@Composable
private fun PlannedWorkoutCard(
  name: String?,
  workout: com.trainerloop.data.model.Workout?,
  ftp: Int,
  loading: Boolean,
  error: String?,
  onStart: () -> Unit
) {
  val onColor = MaterialTheme.colorScheme.onSecondaryContainer
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.secondaryContainer
    )
  ) {
    Column(modifier = Modifier.padding(Spacing.lg)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(Green40)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "TODAY'S PLAN · INTERVALS.ICU",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = onColor.copy(alpha = 0.7f)
        )
      }
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = name ?: error ?: "Loading planned workout…",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = onColor,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )

      if (workout != null) {
        val totalSec = remember(workout) {
          com.trainerloop.domain.WorkoutMath.totalDurationSec(workout.segments)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
          Text(
            text = formatDuration(totalSec),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = onColor.copy(alpha = 0.85f)
          )
          Text(
            text = "${workout.segments.size} intervals",
            style = MaterialTheme.typography.bodyMedium,
            color = onColor.copy(alpha = 0.85f)
          )
        }
        Spacer(modifier = Modifier.height(Spacing.lg))
        com.trainerloop.ui.components.WorkoutMiniChart(
          workout = workout,
          ftp = ftp,
          chartHeight = 72.dp,
          lineColor = onColor.copy(alpha = 0.9f)
        )
      }

      if (name != null) {
        Spacer(modifier = Modifier.height(Spacing.lg))
        Button(
          onClick = onStart,
          enabled = !loading,
          modifier = Modifier.fillMaxWidth()
        ) {
          if (loading) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.onPrimary
            )
          } else {
            Icon(
              imageVector = Icons.Default.PlayArrow,
              contentDescription = null,
              modifier = Modifier.padding(end = 8.dp)
            )
            Text("Quick Start", fontWeight = FontWeight.SemiBold)
          }
        }
      }
    }
  }
}

@Composable
private fun DeviceStatusRow(
  trainerName: String?,
  trainerConnected: Boolean,
  trainerBattery: Int?,
  hrConnected: Boolean,
  latestHrBpm: Int?,
  onManageDevices: () -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    DeviceChip(
      modifier = Modifier.weight(1f),
      connected = trainerConnected,
      label = if (trainerConnected) trainerName ?: "Trainer" else "No trainer",
      detail = if (trainerConnected) {
        trainerBattery?.let { "Battery $it%" } ?: "Connected"
      } else "Tap to pair",
      onClick = onManageDevices
    )
    DeviceChip(
      modifier = Modifier.weight(1f),
      connected = hrConnected,
      label = if (hrConnected) latestHrBpm?.let { "$it bpm" } ?: "HR sensor" else "No HR",
      detail = if (hrConnected) "Connected" else "Tap to pair",
      onClick = onManageDevices
    )
  }
}

@Composable
private fun DeviceChip(
  modifier: Modifier = Modifier,
  connected: Boolean,
  label: String,
  detail: String,
  onClick: () -> Unit
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    onClick = onClick
  ) {
    Row(
      modifier = Modifier.padding(Spacing.lg),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = if (connected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
        contentDescription = if (connected) "Device connected" else "Device disconnected",
        tint = if (connected) Green40 else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp)
      )
      Spacer(modifier = Modifier.width(8.dp))
      Column {
        Text(
          text = label,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = detail,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
private fun ActionRows(
  onWorkoutLibrary: () -> Unit,
  onWorkoutBuilder: () -> Unit,
  onGpxRoutes: () -> Unit
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column {
      ActionRow(
        icon = Icons.AutoMirrored.Filled.DirectionsBike,
        label = "Workout Library",
        onClick = onWorkoutLibrary
      )
      HorizontalDivider()
      ActionRow(
        icon = Icons.Default.Build,
        label = "Workout Builder",
        onClick = onWorkoutBuilder
      )
      HorizontalDivider()
      ActionRow(
        icon = Icons.AutoMirrored.Filled.DirectionsBike,
        label = "GPX Routes",
        onClick = onGpxRoutes
      )
    }
  }
}

@Composable
private fun ActionRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(Spacing.lg),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.width(16.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.bodyLarge,
      fontWeight = FontWeight.Medium,
      modifier = Modifier.weight(1f)
    )
    Icon(
      imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
      contentDescription = "Open $label",
      tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun RecentSessionCard(session: SessionSummary) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
  ) {
    Column(modifier = Modifier.padding(Spacing.lg)) {
      Text(
        text = session.workoutName,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
      )
      Spacer(modifier = Modifier.height(4.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
      ) {
        Text(
          text = formatSessionDate(session.startedAt),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = formatDuration(session.durationSec),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      if (session.avgPower > 0) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Avg Power: ${session.avgPower} W",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

private fun formatSessionDate(startedAt: String): String {
  return try {
    val instant = Instant.parse(startedAt)
    DateTimeFormatter.ofPattern("MMM d, yyyy")
      .withZone(ZoneId.systemDefault())
      .format(instant)
  } catch (_: Exception) {
    startedAt
  }
}

private fun formatDuration(seconds: Int): String {
  val hours = seconds / 3600
  val minutes = (seconds % 3600) / 60
  val secs = seconds % 60
  return if (hours > 0) {
    "$hours:${minutes.pad2()}:${secs.pad2()}"
  } else {
    "$minutes:${secs.pad2()}"
  }
}

private fun Int.pad2(): String = if (this < 10) "0$this" else "$this"
