package com.trainerloop.ui.devices

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainerloop.ui.theme.Green40

@Composable
fun DevicesScreen(
  onBack: () -> Unit = {},
  viewModel: DevicesViewModel = viewModel()
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Devices",
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold
      )
      TextButton(onClick = onBack) {
        Text("Done")
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    StatusRow(label = "Bluetooth", ok = uiState.isBluetoothOn)
    StatusRow(label = "Location", ok = uiState.isLocationOn)
    StatusRow(label = "Permissions", ok = uiState.hasPermissions)

    Spacer(modifier = Modifier.height(16.dp))

    val pairedDevices = connectedDevices(uiState)
    val availableDevices = uiState.availableDevices.filter {
      pairedDevices.none { paired ->
        paired.device.address.equals(it.device.address, ignoreCase = true)
      }
    }

    LazyColumn(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Paired Devices
      item {
        Text(
          text = "Paired Devices",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold
        )
      }

      if (pairedDevices.isEmpty()) {
        item {
          Text(
            text = "No devices paired yet. Connect a trainer or HR sensor below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      items(pairedDevices) { aggregated ->
        PairedDeviceCard(
          name = pairedDeviceName(aggregated),
          capabilities = aggregated.capabilities,
          connected = true,
          detail = pairedDeviceDetail(aggregated, uiState),
          onDisconnect = { viewModel.disconnectDevice(aggregated) }
        )
      }

      item { Spacer(modifier = Modifier.height(8.dp)) }

      // Available Devices
      item {
        Text(
          text = "Available Devices",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold
        )
      }

      if (availableDevices.isNotEmpty()) {
        items(availableDevices) { device ->
          AvailableDeviceCard(
            device = device,
            isConnecting = uiState.isConnecting(device),
            onConnect = { viewModel.connectDevice(device) }
          )
        }
      }

      if (availableDevices.isEmpty() && !uiState.isScanning) {
        item {
          Text(
            text = "No devices found. Tap Scan to search.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      if (uiState.isScanning) {
        item {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(
              text = "Scanning...",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Button(
      onClick = { viewModel.startScan() },
      enabled = !uiState.isScanning,
      modifier = Modifier.fillMaxWidth()
    ) {
      Icon(
        imageVector = Icons.Default.Search,
        contentDescription = null,
        modifier = Modifier.padding(end = 8.dp)
      )
      Text(if (uiState.isScanning) "Scanning..." else "Scan for Devices")
    }

    if (uiState.isScanning) {
      Spacer(modifier = Modifier.height(8.dp))
      OutlinedButton(
        onClick = { viewModel.stopScan() },
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("Stop Scan")
      }
    }
  }

  uiState.error?.let { error ->
    Snackbar(
      modifier = Modifier.padding(16.dp),
      action = {
        TextButton(onClick = { viewModel.clearError() }) {
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
        color = if (ok) Green40 else MaterialTheme.colorScheme.error
      )
    }
  }
}

@Composable
private fun PairedDeviceCard(
  name: String,
  capabilities: Set<DeviceCapability>,
  connected: Boolean,
  detail: String,
  onDisconnect: () -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.primaryContainer
    )
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = if (connected) Icons.Default.BluetoothConnected else Icons.Default.BluetoothDisabled,
          contentDescription = if (connected) "Connected" else "Disconnected",
          tint = if (connected) Green40 else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.padding(horizontal = 12.dp))
        Column {
          Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
          )
          CapabilityBadges(capabilities)
          Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      OutlinedButton(onClick = onDisconnect) {
        Text("Disconnect")
      }
    }
  }
}

@Composable
private fun AvailableDeviceCard(
  device: AggregatedDevice,
  isConnecting: Boolean,
  onConnect: () -> Unit
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
        .padding(12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.Bluetooth,
          contentDescription = "Bluetooth device",
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.padding(horizontal = 12.dp))
        Column {
          Text(
            text = device.device.name ?: "Unknown",
            style = MaterialTheme.typography.titleMedium
          )
          CapabilityBadges(device.capabilities)
          Text(
            text = "${device.device.address} · RSSI ${device.device.rssi}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      Button(
        onClick = onConnect,
        enabled = !isConnecting
      ) {
        if (isConnecting) {
          CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary
          )
        } else {
          Text("Connect")
        }
      }
    }
  }
}

@Composable
private fun CapabilityBadges(capabilities: Set<DeviceCapability>) {
  Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    capabilities
      .sortedBy { it.ordinal }
      .forEach { capability ->
        AssistChip(
          onClick = {},
          enabled = false,
          modifier = Modifier.height(28.dp),
          label = {
            Text(
              text = capability.label,
              style = MaterialTheme.typography.labelSmall
            )
          }
        )
      }
  }
}

private fun pairedDeviceName(device: AggregatedDevice): String {
  return device.device.name ?: device.capabilities.first().label
}

private fun pairedDeviceDetail(
  device: AggregatedDevice,
  state: DevicesUiState
): String {
  val details = buildList {
    if (DeviceCapability.TRAINER in device.capabilities) {
      state.trainerBattery?.let { add("Battery $it%") }
    }
    if (DeviceCapability.HEART_RATE in device.capabilities) {
      state.latestHrBpm?.let { add("HR $it bpm") }
    }
    if (DeviceCapability.CONTROLLER in device.capabilities) {
      state.clickBattery?.let { add("Battery $it%") }
    }
  }
  return details.takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: "Connected"
}
