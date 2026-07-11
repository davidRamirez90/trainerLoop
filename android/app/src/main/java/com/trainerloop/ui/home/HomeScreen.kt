package com.trainerloop.ui.home

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.trainerloop.data.model.Workout
import com.trainerloop.data.model.SessionSummary
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.ui.components.pressable
import com.trainerloop.ui.components.WorkoutMiniChart
import com.trainerloop.domain.WorkoutResolver
import com.trainerloop.ui.library.ImportedWorkoutStore
import com.trainerloop.ui.theme.Green20
import com.trainerloop.ui.theme.Green40
import com.trainerloop.ui.theme.NumericSmall
import com.trainerloop.ui.theme.Spacing
import com.trainerloop.ui.theme.MotionSpec
import com.trainerloop.ui.theme.reducedMotionAware
import com.trainerloop.ui.theme.zoneColorSet
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

@Suppress("UNUSED_PARAMETER")
@SuppressLint("MissingPermission")
@Composable
fun HomeScreen(
  onNavigateToDevices: () -> Unit,
  onNavigateToWorkouts: () -> Unit,
  onNavigateToBuilder: () -> Unit,
  onStartFreeRide: () -> Unit,
  onStartPlanned: (com.trainerloop.data.model.Workout) -> Unit,
  onGpxRoutes: () -> Unit,
  onNavigateToProfile: () -> Unit,
  viewModel: HomeViewModel = viewModel()
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val defaultMotionSpec = reducedMotionAware(MotionSpec.default)
  val plannedReady by viewModel.plannedWorkoutReady.collectAsStateWithLifecycle()
  val context = LocalContext.current

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

    val hasPlan = uiState.plannedName != null || uiState.plannedLoading || uiState.plannedError != null
    item(key = "planned-workout") {
      AnimatedVisibility(
        visible = hasPlan,
        enter = expandVertically(
          animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
        ) + fadeIn(animationSpec = defaultMotionSpec),
        exit = shrinkVertically(
          animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
        ) + fadeOut(animationSpec = defaultMotionSpec)
      ) {
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
      ActionRows(onWorkoutBuilder = onNavigateToBuilder, onGpxRoutes = onGpxRoutes)
    }

    // Today's plan takes over this slot when present — recent history only
    // shows on rest days / when no workout is scheduled.
    item(key = "recent-workouts") {
      AnimatedVisibility(
        visible = !hasPlan,
        enter = expandVertically(
          animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
        ) + fadeIn(animationSpec = defaultMotionSpec),
        exit = shrinkVertically(
          animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
        ) + fadeOut(animationSpec = defaultMotionSpec)
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
          Text(
            text = "Recent Workouts",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
          )

          val recentSession = uiState.recentSession
          AnimatedContent(
            targetState = recentSession != null,
            transitionSpec = {
              fadeIn(animationSpec = defaultMotionSpec) togetherWith
                fadeOut(animationSpec = defaultMotionSpec)
            },
            label = "recent-workout-empty-state"
          ) { hasRecent ->
            if (!hasRecent) {
              Text(
                text = "No saved workouts yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            } else {
              val session = requireNotNull(recentSession)
              val recentWorkout = remember(session.workoutId, uiState.ftp) {
                WorkoutResolver.resolve(
                  session.workoutId,
                  uiState.ftp,
                  ImportedWorkoutStore.load(context)
                )
              }
              RecentSessionCard(
                session = session,
                workout = recentWorkout,
                ftp = uiState.ftp
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun RiderHeader(
  name: String,
  ftp: Int,
  weightKg: Double,
  onClick: () -> Unit
) {
  val interactionSource = remember { MutableInteractionSource() }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = Spacing.sm)
      .pressable(interactionSource)
      .clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick
      ),
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
        text = name.take(1).uppercase().takeIf { it.isNotBlank() } ?: "?",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onPrimaryContainer
      )
    }

    Spacer(modifier = Modifier.width(Spacing.md))

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = name,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold
      )
      Text(
        text = "FTP $ftp W · ${"%.1f".format(Locale.US, weightKg)} kg",
        style = NumericSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
      )
    }
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
  val interactionSource = remember { MutableInteractionSource() }

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .background(Brush.linearGradient(listOf(Green20, Green40)))
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
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

      ConnectionStrip(
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
private fun PlannedWorkoutCard(
  name: String?,
  workout: com.trainerloop.data.model.Workout?,
  ftp: Int,
  loading: Boolean,
  error: String?,
  onStart: () -> Unit
) {
  val fastMotionSpec = reducedMotionAware(MotionSpec.fast)
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
      AnimatedContent(
        targetState = name ?: error ?: "Loading planned workout…",
        transitionSpec = {
          fadeIn(animationSpec = fastMotionSpec) togetherWith
            fadeOut(animationSpec = fastMotionSpec)
        },
        label = "planned-workout-state"
      ) { title ->
        Text(
          text = title,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = onColor,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
      }

      AnimatedVisibility(
        visible = workout != null,
        enter = expandVertically(
          animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
        ) + fadeIn(animationSpec = reducedMotionAware(MotionSpec.default)),
        exit = shrinkVertically(
          animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
        ) + fadeOut(animationSpec = reducedMotionAware(MotionSpec.default))
      ) {
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
      }

      AnimatedVisibility(
        visible = name != null,
        enter = expandVertically(
          animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
        ) + fadeIn(animationSpec = reducedMotionAware(MotionSpec.default)),
        exit = shrinkVertically(
          animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
        ) + fadeOut(animationSpec = reducedMotionAware(MotionSpec.default))
      ) {
        Spacer(modifier = Modifier.height(Spacing.lg))
        Button(
          onClick = onStart,
          enabled = !loading,
          modifier = Modifier.fillMaxWidth()
        ) {
          AnimatedContent(
            targetState = loading,
            transitionSpec = {
              fadeIn(animationSpec = fastMotionSpec) togetherWith
                fadeOut(animationSpec = fastMotionSpec)
            },
            label = "planned-workout-action"
          ) { isLoading ->
            if (isLoading) {
              CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
              )
            } else {
              Row(verticalAlignment = Alignment.CenterVertically) {
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
  }
}

@Composable
private fun ConnectionStrip(
  trainerName: String?,
  trainerConnected: Boolean,
  trainerBattery: Int?,
  hrConnected: Boolean,
  latestHrBpm: Int?,
  onManageDevices: () -> Unit
) {
  val interactionSource = remember { MutableInteractionSource() }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(Color.Black.copy(alpha = 0.12f))
      .pressable(interactionSource)
      .clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onManageDevices
      )
      .padding(horizontal = Spacing.xl, vertical = Spacing.md),
    verticalAlignment = Alignment.CenterVertically
  ) {
    ConnectionStatus(
      modifier = Modifier.weight(1f),
      icon = if (trainerConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
      connected = trainerConnected,
      label = trainerName ?: "Trainer",
      value = if (trainerConnected) trainerBattery?.let { "$it%" } ?: "—" else "—"
    )
    Spacer(modifier = Modifier.width(Spacing.md))
    ConnectionStatus(
      modifier = Modifier.weight(1f),
      icon = if (hrConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
      connected = hrConnected,
      label = "HR",
      value = if (hrConnected) latestHrBpm?.let { "$it bpm" } ?: "—" else "—"
    )
  }
}

@Composable
private fun ConnectionStatus(
  modifier: Modifier = Modifier,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  connected: Boolean,
  label: String,
  value: String
) {
  val fastMotionSpec = reducedMotionAware(MotionSpec.fast)
  val iconTint by animateColorAsState(
    targetValue = Color.White.copy(alpha = if (connected) 1f else 0.65f),
    animationSpec = reducedMotionAware(MotionSpec.defaultSpring<Color>()),
    label = "connection-icon-color"
  )
  val textColor by animateColorAsState(
    targetValue = Color.White.copy(alpha = if (connected) 1f else 0.75f),
    animationSpec = reducedMotionAware(MotionSpec.defaultSpring<Color>()),
    label = "connection-text-color"
  )
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically
  ) {
    AnimatedContent(
      targetState = connected,
      transitionSpec = {
        fadeIn(animationSpec = fastMotionSpec) togetherWith
          fadeOut(animationSpec = fastMotionSpec)
      },
      label = "connection-icon"
    ) { isConnected ->
      Icon(
        imageVector = icon,
        contentDescription = if (isConnected) "Device connected" else "Device disconnected",
        tint = iconTint,
        modifier = Modifier.size(18.dp)
      )
    }
    Spacer(modifier = Modifier.width(Spacing.sm))
    Text(
      text = "$label · $value",
      style = MaterialTheme.typography.labelMedium,
      color = textColor,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
private fun ActionRows(
  onWorkoutBuilder: () -> Unit,
  onGpxRoutes: () -> Unit
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column {
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
  val interactionSource = remember { MutableInteractionSource() }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(Spacing.lg)
      .pressable(interactionSource)
      .clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick
      ),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
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
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun RecentSessionCard(
  session: SessionSummary,
  workout: Workout?,
  ftp: Int
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(Spacing.lg),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = session.workoutName,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
          text = formatSessionMeta(session),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      if (workout != null) {
        Spacer(modifier = Modifier.width(Spacing.md))
        val chartColors = zoneColorSet(representativePower(workout), ftp)
        Box(
          modifier = Modifier
            .size(width = 112.dp, height = 72.dp)
            .clip(RoundedCornerShape(Spacing.md))
            .background(chartColors.fill.copy(alpha = 0.14f))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
        ) {
          WorkoutMiniChart(
            workout = workout,
            ftp = ftp,
            chartHeight = 60.dp,
            lineColor = chartColors.line
          )
        }
      }
    }
  }
}

private fun formatSessionDate(startedAt: String): String {
  return try {
    val instant = Instant.parse(startedAt)
    val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    val daysAgo = ChronoUnit.DAYS.between(date, LocalDate.now())
    if (daysAgo in 0..6) {
      DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()).format(date)
    } else {
      DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).format(date)
    }
  } catch (_: Exception) {
    startedAt
  }
}

private fun formatSessionMeta(session: SessionSummary): String {
  val parts = mutableListOf(
    formatSessionDate(session.startedAt),
    "${session.durationSec / 60} min"
  )
  if (session.avgPower > 0) parts += "${session.avgPower} W"
  return parts.joinToString(" · ")
}

private fun representativePower(workout: Workout): Int =
  workout.segments.firstNotNullOfOrNull { segment ->
    when (segment) {
      is WorkoutSegment.Step -> (segment.targetRange.low + segment.targetRange.high) / 2
      is WorkoutSegment.Ramp -> (segment.startPower + segment.endPower) / 2
      is WorkoutSegment.FreeRide -> null
    }
  } ?: 0

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
