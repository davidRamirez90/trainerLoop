package com.trainerloop.ui.home

import android.annotation.SuppressLint
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainerloop.data.model.SessionSummary
import com.trainerloop.data.model.Workout
import com.trainerloop.domain.WorkoutResolver
import com.trainerloop.ui.components.EmptyState
import com.trainerloop.ui.components.PrimaryActionButton
import com.trainerloop.ui.components.SectionHeader
import com.trainerloop.ui.components.TrainerLoopCard
import com.trainerloop.ui.components.WorkoutMiniChart
import com.trainerloop.ui.components.pressable
import com.trainerloop.ui.library.ImportedWorkoutStore
import com.trainerloop.ui.theme.NumericSmall
import com.trainerloop.ui.theme.Spacing
import com.trainerloop.ui.theme.trainerLoopColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@SuppressLint("MissingPermission")
@Composable
fun HomeScreen(
  onNavigateToDevices: () -> Unit,
  onNavigateToWorkouts: () -> Unit,
  onNavigateToBuilder: () -> Unit,
  onStartFreeRide: () -> Unit,
  onStartPlanned: (Workout) -> Unit,
  onGpxRoutes: () -> Unit,
  onNavigateToProfile: () -> Unit,
  viewModel: HomeViewModel = viewModel()
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val plannedReady by viewModel.plannedWorkoutReady.collectAsStateWithLifecycle()
  val context = LocalContext.current

  LaunchedEffect(plannedReady) {
    plannedReady?.let {
      viewModel.consumePlannedWorkout()
      onStartPlanned(it)
    }
  }

  val hasPlan = uiState.plannedName != null || uiState.plannedLoading || uiState.plannedError != null
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = Spacing.screenMargin),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.lg),
    verticalArrangement = Arrangement.spacedBy(Spacing.sectionGap)
  ) {
    item {
      RiderHeader(
        name = uiState.riderName,
        ftp = uiState.ftp,
        weightKg = uiState.weightKg,
        onClick = onNavigateToProfile
      )
    }

    item {
      StartRideHero(
        onStartFreeRide = onStartFreeRide,
        trainerName = uiState.connectedTrainer?.name ?: uiState.trainerModel,
        trainerConnected = uiState.isTrainerConnected,
        trainerBattery = uiState.trainerBattery,
        hrConnected = uiState.isHrConnected,
        latestHrBpm = uiState.latestHrBpm,
        onManageDevices = onNavigateToDevices
      )
    }

    if (hasPlan) {
      item(key = "planned-workout") {
        PlannedWorkoutCard(
          name = uiState.plannedName,
          workout = uiState.plannedWorkout,
          ftp = uiState.ftp,
          loading = uiState.plannedLoading,
          error = uiState.plannedError,
          onStart = viewModel::startPlanned
        )
      }
    }

    item {
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionHeader(title = "Explore")
        UtilityCards(onWorkoutBuilder = onNavigateToBuilder, onGpxRoutes = onGpxRoutes)
      }
    }

    if (!hasPlan) {
      item(key = "recent-workouts") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
          SectionHeader(
            title = "Recent Workouts",
            trailingAction = {
              TextButton(onClick = onNavigateToWorkouts, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("View all")
              }
            }
          )
          val session = uiState.recentSession
          if (session == null) {
            TrainerLoopCard(modifier = Modifier.fillMaxWidth()) {
              EmptyState(
                icon = Icons.AutoMirrored.Filled.DirectionsBike,
                title = "Your first ride starts here",
                body = "Complete a ride and its highlights will appear here."
              )
            }
          } else {
            val workout = remember(session.workoutId, uiState.ftp) {
              WorkoutResolver.resolve(
                session.workoutId,
                uiState.ftp,
                ImportedWorkoutStore.load(context)
              )
            }
            RecentSessionCard(session = session, workout = workout, ftp = uiState.ftp)
          }
        }
      }
    }
  }
}

@Composable
private fun RiderHeader(name: String, ftp: Int, weightKg: Double, onClick: () -> Unit) {
  val interactionSource = remember { MutableInteractionSource() }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 48.dp)
      .pressable(interactionSource)
      .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(44.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.primaryContainer),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = name.take(1).uppercase().takeIf(String::isNotBlank) ?: "?",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onPrimaryContainer
      )
    }
    Spacer(modifier = Modifier.width(Spacing.md))
    Column(modifier = Modifier.weight(1f)) {
      Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Text(
        text = "FTP $ftp · ${formatWeight(weightKg)} kg",
        style = NumericSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
      )
    }
    Icon(
      Icons.AutoMirrored.Filled.KeyboardArrowRight,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun StartRideHero(
  onStartFreeRide: () -> Unit,
  trainerName: String?,
  trainerConnected: Boolean,
  trainerBattery: Int?,
  hrConnected: Boolean,
  latestHrBpm: Int?,
  onManageDevices: () -> Unit
) {
  val semantic = MaterialTheme.trainerLoopColors
  val container = if (trainerConnected) semantic.ready else MaterialTheme.colorScheme.surfaceVariant
  val content = if (trainerConnected) semantic.onReady else MaterialTheme.colorScheme.onSurfaceVariant
  val interactionSource = remember { MutableInteractionSource() }
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(26.dp),
    colors = CardDefaults.cardColors(containerColor = container)
  ) {
    Column(modifier = Modifier.padding(top = Spacing.xl)) {
      Column(modifier = Modifier.padding(horizontal = Spacing.xl)) {
        Text(
          text = if (trainerConnected) "Ready to ride?" else "Trainer not connected",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = content
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
          text = if (trainerConnected) {
            "Your trainer is connected and ready."
          } else {
            "You can still free ride, but trainer control and metrics will be unavailable."
          },
          style = MaterialTheme.typography.bodyMedium,
          color = content
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
        Button(
          onClick = onStartFreeRide,
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .pressable(interactionSource),
          interactionSource = interactionSource,
          colors = ButtonDefaults.buttonColors(
            containerColor = semantic.heroAction,
            contentColor = semantic.onHeroAction
          )
        ) {
          Icon(Icons.AutoMirrored.Filled.DirectionsBike, contentDescription = null)
          Spacer(modifier = Modifier.width(Spacing.controlGap))
          Text("Start Free Ride", fontWeight = FontWeight.SemiBold)
        }
      }
      Spacer(modifier = Modifier.height(Spacing.lg))
      ConnectionGroup(
        trainerName = trainerName,
        trainerConnected = trainerConnected,
        trainerBattery = trainerBattery,
        hrConnected = hrConnected,
        latestHrBpm = latestHrBpm,
        onManageDevices = onManageDevices
      )
    }
  }
}

@Composable
private fun ConnectionGroup(
  trainerName: String?,
  trainerConnected: Boolean,
  trainerBattery: Int?,
  hrConnected: Boolean,
  latestHrBpm: Int?,
  onManageDevices: () -> Unit
) {
  val interactionSource = remember { MutableInteractionSource() }
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 48.dp)
      .pressable(interactionSource)
      .clickable(
        interactionSource = interactionSource,
        indication = null,
        onClickLabel = "Manage devices",
        onClick = onManageDevices
      )
      .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
      .padding(Spacing.lg),
    verticalArrangement = Arrangement.spacedBy(Spacing.md)
  ) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = "Connections",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.weight(1f)
      )
      Text("Manage", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp)
      )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.controlGap)) {
      ConnectionTile(
        modifier = Modifier.weight(1f),
        label = "Trainer",
        value = if (trainerConnected) {
          trainerBattery?.let { "${trainerName ?: "Connected"} · $it%" } ?: (trainerName ?: "Connected")
        } else {
          "Not connected"
        },
        connected = trainerConnected
      )
      ConnectionTile(
        modifier = Modifier.weight(1f),
        label = "Heart Rate",
        value = if (hrConnected) latestHrBpm?.let { "$it bpm" } ?: "Connected" else "Not connected",
        connected = hrConnected
      )
    }
  }
}

@Composable
private fun ConnectionTile(modifier: Modifier, label: String, value: String, connected: Boolean) {
  val semantic = MaterialTheme.trainerLoopColors
  val container: Color = if (connected) semantic.connected else semantic.stale
  val content: Color = if (connected) semantic.onConnected else semantic.onStale
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(14.dp))
      .background(container)
      .padding(Spacing.md),
    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
  ) {
    Icon(
      if (connected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
      contentDescription = null,
      tint = content,
      modifier = Modifier.size(18.dp)
    )
    Text(label, style = MaterialTheme.typography.labelSmall, color = content)
    Text(value, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

@Composable
private fun PlannedWorkoutCard(
  name: String?,
  workout: Workout?,
  ftp: Int,
  loading: Boolean,
  error: String?,
  onStart: () -> Unit
) {
  val content = MaterialTheme.colorScheme.onSecondaryContainer
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
  ) {
    Column(modifier = Modifier.padding(Spacing.cardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
      Text("TODAY'S PLAN · INTERVALS.ICU", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = content)
      Text(
        text = name ?: error ?: "Loading planned workout…",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = content,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
      if (workout != null) {
        val duration = remember(workout) { com.trainerloop.domain.WorkoutMath.totalDurationSec(workout.segments) }
        Text(
          text = "${formatDuration(duration)} · ${workout.segments.size} intervals",
          style = NumericSmall.copy(color = content)
        )
        WorkoutMiniChart(workout = workout, ftp = ftp, chartHeight = 72.dp, lineColor = content)
      }
      if (name != null) {
        PrimaryActionButton(onClick = onStart, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
          if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
          } else {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(Spacing.controlGap))
            Text("Quick Start")
          }
        }
      }
    }
  }
}

@Composable
private fun UtilityCards(onWorkoutBuilder: () -> Unit, onGpxRoutes: () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.controlGap)) {
    UtilityCard(
      icon = Icons.Default.Build,
      title = "Workout Builder",
      description = "Create a structured session",
      onClick = onWorkoutBuilder
    )
    UtilityCard(
      icon = Icons.AutoMirrored.Filled.DirectionsBike,
      title = "GPX Routes",
      description = "Ride a route from your files",
      onClick = onGpxRoutes
    )
  }
}

@Composable
private fun UtilityCard(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
  TrainerLoopCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
    Row(modifier = Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
      Spacer(modifier = Modifier.width(Spacing.md))
      Column(modifier = Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
          description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun RecentSessionCard(session: SessionSummary, workout: Workout?, ftp: Int) {
  TrainerLoopCard(modifier = Modifier.fillMaxWidth()) {
    if (workout != null) {
      WorkoutMiniChart(workout = workout, ftp = ftp, modifier = Modifier.fillMaxWidth(), chartHeight = 72.dp)
      Spacer(modifier = Modifier.height(Spacing.md))
    }
    Text(
      text = session.workoutName,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
    Spacer(modifier = Modifier.height(Spacing.xs))
    Text(
      text = formatSessionMeta(session),
      style = NumericSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

private fun formatWeight(weightKg: Double): String =
  if (weightKg % 1.0 == 0.0) weightKg.toInt().toString() else String.format(Locale.US, "%.1f", weightKg)

private fun formatSessionMeta(session: SessionSummary): String {
  val parts = mutableListOf(formatSessionDate(session.startedAt), "${session.durationSec / 60} min")
  if (session.avgPower > 0) parts += "${session.avgPower} W"
  return parts.joinToString(" · ")
}

private fun formatSessionDate(startedAt: String): String = try {
  val date = Instant.parse(startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
  val daysAgo = ChronoUnit.DAYS.between(date, LocalDate.now())
  val pattern = if (daysAgo in 0..6) "EEEE" else "d MMM"
  DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(date)
} catch (_: Exception) {
  startedAt
}

private fun formatDuration(seconds: Int): String {
  val hours = seconds / 3600
  val minutes = (seconds % 3600) / 60
  return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
