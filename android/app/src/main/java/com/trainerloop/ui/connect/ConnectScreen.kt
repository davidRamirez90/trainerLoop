package com.trainerloop.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainerloop.ble.model.BleDevice

@Composable
fun ConnectScreen(
  onNavigateToLibrary: () -> Unit,
  viewModel: ConnectViewModel = viewModel()
) {
  val uiState by viewModel.uiState.collectAsState()

  LaunchedEffect(Unit) {
    if (uiState.trainerDevices.isEmpty() && uiState.hrDevices.isEmpty() && !uiState.isScanning) {
      viewModel.startScan()
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp)
  ) {
    Text(
      text = "Connect Devices",
      style = MaterialTheme.typography.headlineLarge
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Status indicators
    StatusRow(label = "Bluetooth", ok = uiState.isBluetoothOn)
    StatusRow(label = "Location", ok = uiState.isLocationOn)
    StatusRow(label = "Permissions", ok = uiState.hasPermissions)

    Spacer(modifier = Modifier.height(8.dp))

    // Scan controls
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Button(
        onClick = { viewModel.startScan() },
        enabled = !uiState.isScanning
      ) {
        Text(if (uiState.isScanning) "Scanning..." else "Scan")
      }

      if (uiState.isScanning) {
        CircularProgressIndicator(
          modifier = Modifier.height(24.dp),
          strokeWidth = 2.dp
        )
      }

      if (uiState.isScanning) {
        OutlinedButton(onClick = { viewModel.stopScan() }) {
          Text("Stop")
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Trainer devices
    Text(
      text = "Trainers",
      style = MaterialTheme.typography.titleLarge
    )
    Spacer(modifier = Modifier.height(8.dp))

    if (uiState.trainerDevices.isEmpty() && !uiState.isScanning) {
      Text(
        text = "No trainers found. Tap Scan to search.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    LazyColumn(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(uiState.trainerDevices) { device ->
        DeviceCard(
          device = device,
          isConnected = uiState.connectedTrainer?.address == device.address,
          onConnect = { viewModel.connectTrainer(device) },
          onDisconnect = { viewModel.disconnectTrainer() }
        )
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // HR devices
    Text(
      text = "Heart Rate Sensors",
      style = MaterialTheme.typography.titleLarge
    )
    Spacer(modifier = Modifier.height(8.dp))

    if (uiState.hrDevices.isEmpty() && !uiState.isScanning) {
      Text(
        text = "No HR sensors found.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    LazyColumn(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(uiState.hrDevices) { device ->
        DeviceCard(
          device = device,
          isConnected = uiState.connectedHr?.address == device.address,
          onConnect = { viewModel.connectHr(device) },
          onDisconnect = { viewModel.disconnectHr() }
        )
      }
    }
  }

  // Error snackbar
  uiState.error?.let { error ->
    Snackbar(
      modifier = Modifier.padding(16.dp),
      action = {
        androidx.compose.material3.TextButton(onClick = { viewModel.clearError() }) {
          Text("Dismiss")
        }
      }
    ) {
      Text(error)
    }
  }
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall
    )
    Box(
      modifier = Modifier
        .padding(start = 8.dp)
        .height(12.dp)
    ) {
      Text(
        text = if (ok) "✓" else "✗",
        style = MaterialTheme.typography.bodySmall,
        color = if (ok) Color(0xFF4CAF78) else Color(0xFFE53935)
      )
    }
  }
}

@Composable
private fun DeviceCard(
  device: BleDevice,
  isConnected: Boolean,
  onConnect: () -> Unit,
  onDisconnect: () -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = if (isConnected) {
        MaterialTheme.colorScheme.primaryContainer
      } else {
        MaterialTheme.colorScheme.surfaceVariant
      }
    )
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text(
          text = device.name ?: "Unknown",
          style = MaterialTheme.typography.titleMedium
        )
        Text(
          text = device.address,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = "RSSI: ${device.rssi}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Button(
        onClick = if (isConnected) onDisconnect else onConnect
      ) {
        Text(if (isConnected) "Disconnect" else "Connect")
      }
    }
  }
}
