package com.trainerloop.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Button
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import android.annotation.SuppressLint
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainerloop.data.model.SessionSummary
import com.trainerloop.ui.theme.Green40

@SuppressLint("MissingPermission")
@Composable
fun HomeScreen(
  onNavigateToDevices: () -> Unit,
  onNavigateToWorkouts: () -> Unit,
  onStartFreeRide: () -> Unit,
  viewModel: HomeViewModel = viewModel()
) {
  val uiState by viewModel.uiState.collectAsState()

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      RiderHeader(
        name = uiState.riderName,
        ftp = uiState.ftp,
        weightKg = uiState.weightKg
      )
    }

    item {
      ConnectedDevicesSection(
        trainerName = uiState.connectedTrainer?.name,
        trainerConnected = uiState.isTrainerConnected,
        trainerBattery = uiState.trainerBattery,
        trainerModel = uiState.trainerModel,
        hrName = uiState.connectedHr?.name,
        hrConnected = uiState.isHrConnected,
        latestHrBpm = uiState.latestHrBpm,
        onManageDevices = onNavigateToDevices
      )
    }

    item {
      QuickStartCard(onStartFreeRide = onStartFreeRide)
    }

    item {
      ActionRows(
        onWorkoutLibrary = onNavigateToWorkouts,
        onWorkoutBuilder = { /* TODO: builder not in scope */ }
      )
    }

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
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricBadge(label = "FTP", value = "$ftp W")
        MetricBadge(label = "Weight", value = "${"%.1f".format(weightKg)} kg")
      }
    }
  }
}

@Composable
private fun MetricBadge(label: String, value: String) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceVariant
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "$label: ",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Text(
        text = value,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold
      )
    }
  }
}

@Composable
private fun ConnectedDevicesSection(
  trainerName: String?,
  trainerConnected: Boolean,
  trainerBattery: Int?,
  trainerModel: String?,
  hrName: String?,
  hrConnected: Boolean,
  latestHrBpm: Int?,
  onManageDevices: () -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Connected Devices",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold
        )
        OutlinedButton(onClick = onManageDevices) {
          Text("Manage")
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      DeviceRow(
        name = trainerName ?: "No trainer connected",
        connected = trainerConnected,
        detail = buildString {
          trainerModel?.let { append(it) }
          trainerBattery?.let {
            if (isNotBlank()) append(" · ")
            append("Battery $it%")
          }
          if (isBlank()) append("Tap Manage to pair")
        }
      )

      Spacer(modifier = Modifier.height(8.dp))

      DeviceRow(
        name = hrName ?: "No HR sensor connected",
        connected = hrConnected,
        detail = latestHrBpm?.let { "HR $it bpm" }
          ?: if (hrName != null) "Connecting..." else "Tap Manage to pair"
      )
    }
  }
}

@Composable
private fun DeviceRow(
  name: String,
  connected: Boolean,
  detail: String
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = if (connected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
      contentDescription = if (connected) "Device connected" else "Device disconnected",
      tint = if (connected) Green40 else MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(24.dp)
    )

    Spacer(modifier = Modifier.width(12.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = name,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium
      )
      Text(
        text = detail,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    if (connected) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = Green40.copy(alpha = 0.2f)
      ) {
        Text(
          text = "Connected",
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          style = MaterialTheme.typography.labelSmall,
          color = Green40
        )
      }
    }
  }
}

@Composable
private fun QuickStartCard(onStartFreeRide: () -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.primaryContainer
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        text = "Ready to ride?",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onPrimaryContainer
      )
      Spacer(modifier = Modifier.height(8.dp))
      Button(
        onClick = onStartFreeRide,
        modifier = Modifier.fillMaxWidth()
      ) {
        Icon(
          imageVector = Icons.Default.FitnessCenter,
          contentDescription = "Start free ride",
          modifier = Modifier.padding(end = 8.dp)
        )
        Text("Start Free Ride")
      }
    }
  }
}

@Composable
private fun ActionRows(
  onWorkoutLibrary: () -> Unit,
  onWorkoutBuilder: () -> Unit
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column {
      ActionRow(
        icon = Icons.Default.FitnessCenter,
        label = "Workout Library",
        onClick = onWorkoutLibrary
      )
      HorizontalDivider()
      ActionRow(
        icon = Icons.Default.Build,
        label = "Workout Builder",
        onClick = onWorkoutBuilder
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
      .padding(horizontal = 16.dp, vertical = 12.dp),
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
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = session.workoutName,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
      )
      Spacer(modifier = Modifier.height(4.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
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
