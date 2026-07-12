package com.trainerloop.ui.devices

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainerloop.ble.BlePermissions
import com.trainerloop.ui.components.PrimaryActionButton
import com.trainerloop.ui.components.SecondaryActionButton
import com.trainerloop.ui.components.SecondaryActionStyle
import com.trainerloop.ui.components.SectionHeader
import com.trainerloop.ui.components.StatusPill
import com.trainerloop.ui.components.StatusPillState
import com.trainerloop.ui.components.TrainerLoopCard
import com.trainerloop.ui.components.pressable
import com.trainerloop.ui.theme.LocalReducedMotion
import com.trainerloop.ui.theme.MotionSpec
import com.trainerloop.ui.theme.Spacing
import com.trainerloop.ui.theme.reducedMotionAware

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
  onBack: () -> Unit = {},
  viewModel: DevicesViewModel = viewModel()
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var displayedError by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(uiState.error) {
    uiState.error?.let { displayedError = it }
  }
  val defaultMotionSpec = reducedMotionAware(MotionSpec.default)
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) {
    viewModel.refreshStatus()
    if (viewModel.uiState.value.hasPermissions) {
      viewModel.clearError()
    }
  }

  ModalBottomSheet(
    onDismissRequest = onBack,
    sheetState = sheetState
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight(0.9f)
        .navigationBarsPadding()
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = Spacing.screenMargin)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Devices",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
          )
          SecondaryActionButton(
            onClick = {
              if (uiState.isScanning) viewModel.stopScan() else viewModel.startScan()
            },
            style = SecondaryActionStyle.Tonal
          ) {
            Text(if (uiState.isScanning) "Stop" else "Scan")
          }
        }

        Spacer(modifier = Modifier.size(Spacing.sm))

        AnimatedVisibility(
          visible = !uiState.hasPermissions,
          enter = expandVertically(
            animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
          ) + fadeIn(animationSpec = reducedMotionAware(MotionSpec.default)),
          exit = shrinkVertically(
            animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
          ) + fadeOut(animationSpec = reducedMotionAware(MotionSpec.default))
        ) {
          Column {
            PermissionBanner(
              onGrant = { permissionLauncher.launch(BlePermissions.REQUIRED) }
            )
            Spacer(modifier = Modifier.size(Spacing.md))
          }
        }

        val pairedDevices = connectedDevices(uiState)
        val availableDevices = uiState.availableDevices.filter { available ->
          pairedDevices.none { paired ->
            paired.device.address.equals(available.device.address, ignoreCase = true)
          }
        }

        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          verticalArrangement = Arrangement.spacedBy(Spacing.controlGap)
        ) {
          item {
            SectionHeader(title = "Connected devices")
          }

          item {
            AnimatedVisibility(
              visible = pairedDevices.isEmpty(),
              enter = expandVertically(
                animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
              ) + fadeIn(animationSpec = reducedMotionAware(MotionSpec.default)),
              exit = shrinkVertically(
                animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
              ) + fadeOut(animationSpec = reducedMotionAware(MotionSpec.default))
            ) {
              Text(
                text = "No devices connected yet. Connect a trainer or HR sensor below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          items(pairedDevices, key = { it.device.address }) { aggregated ->
            PairedDeviceCard(
              name = pairedDeviceName(aggregated),
              capabilities = aggregated.capabilities,
              detail = pairedDeviceDetail(aggregated, uiState),
              onDisconnect = { viewModel.disconnectDevice(aggregated) }
            )
          }

          item {
            SectionHeader(
              title = "Available devices",
              modifier = Modifier.padding(top = Spacing.sectionGap - Spacing.controlGap)
            )
          }

          if (availableDevices.isNotEmpty()) {
            items(availableDevices, key = { it.device.address }) { device ->
              AvailableDeviceCard(
                device = device,
                isConnecting = uiState.isConnecting(device),
                onConnect = { viewModel.connectDevice(device) }
              )
            }
          }

          item {
            AnimatedVisibility(
              visible = availableDevices.isEmpty() && !uiState.isScanning,
              enter = expandVertically(
                animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
              ) + fadeIn(animationSpec = reducedMotionAware(MotionSpec.default)),
              exit = shrinkVertically(
                animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
              ) + fadeOut(animationSpec = reducedMotionAware(MotionSpec.default))
            ) {
              Text(
                text = "No devices found. Tap Scan to search.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          item {
            AnimatedVisibility(
              visible = uiState.isScanning,
              enter = expandVertically(
                animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
              ) + fadeIn(animationSpec = reducedMotionAware(MotionSpec.default)),
              exit = shrinkVertically(
                animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
              ) + fadeOut(animationSpec = reducedMotionAware(MotionSpec.default))
            ) { ScanningPlaceholder() }
          }
        }
      }

      androidx.compose.animation.AnimatedVisibility(
        visible = uiState.error != null,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = fadeIn(animationSpec = defaultMotionSpec),
        exit = fadeOut(animationSpec = defaultMotionSpec)
      ) {
        displayedError?.let { error ->
          Snackbar(
            modifier = Modifier.padding(Spacing.screenMargin),
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            actionContentColor = MaterialTheme.colorScheme.onErrorContainer,
            action = {
              TextButton(
                onClick = { viewModel.clearError() },
                modifier = Modifier.pressable()
              ) {
                Text("Dismiss")
              }
            }
          ) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(imageVector = Icons.Default.Error, contentDescription = null)
              Text("Connection failed: $error")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PermissionBanner(onGrant: () -> Unit) {
  androidx.compose.material3.Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.errorContainer
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(Spacing.cardPadding),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.controlGap)
    ) {
      Icon(
        imageVector = Icons.Default.BluetoothDisabled,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onErrorContainer
      )
      Text(
        text = "Bluetooth permissions needed",
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onErrorContainer
      )
      TextButton(
        onClick = onGrant,
        modifier = Modifier.pressable().heightIn(min = 48.dp)
      ) {
        Text("Grant")
      }
    }
  }
}

@Composable
private fun ScanningPlaceholder() {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
  ) {
    Icon(
      imageVector = Icons.Default.Search,
      contentDescription = null,
      modifier = Modifier.alpha(scanningIconAlpha()),
      tint = MaterialTheme.colorScheme.primary
    )
    Text(
      text = "Scanning for devices…",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun scanningIconAlpha(): Float {
  if (LocalReducedMotion.current) return 0.6f

  val transition = rememberInfiniteTransition(label = "device-scan-pulse")
  val alpha by transition.animateFloat(
    initialValue = 0.4f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1_200),
      repeatMode = RepeatMode.Reverse
    ),
    label = "device-scan-icon-alpha"
  )
  return alpha
}

@Composable
private fun PairedDeviceCard(
  name: String,
  capabilities: Set<DeviceCapability>,
  detail: String,
  onDisconnect: () -> Unit
) {
  TrainerLoopCard(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(Spacing.controlGap),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = name,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.size(Spacing.xs))
        Row(
          horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
          verticalAlignment = Alignment.CenterVertically
        ) {
          StatusPill(
            state = StatusPillState.Connected,
            label = "Connected",
            icon = Icons.Default.BluetoothConnected
          )
        }
        CapabilityBadges(capabilities)
        Text(
          text = detail,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      SecondaryActionButton(
        onClick = onDisconnect,
        style = SecondaryActionStyle.Outlined
      ) {
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
  val fastMotionSpec = reducedMotionAware(MotionSpec.fast)
  TrainerLoopCard(modifier = Modifier.fillMaxWidth(), emphasized = true) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(Spacing.controlGap),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = Icons.Default.Bluetooth,
        contentDescription = "Bluetooth device",
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = device.device.name ?: "Unknown",
          style = MaterialTheme.typography.titleMedium
        )
        CapabilityBadges(device.capabilities)
        Text(
          text = "${device.device.address} · RSSI ${device.device.rssi}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      PrimaryActionButton(
        onClick = onConnect,
        enabled = !isConnecting
      ) {
        AnimatedContent(
          targetState = isConnecting,
          transitionSpec = {
            fadeIn(animationSpec = fastMotionSpec) togetherWith
              fadeOut(animationSpec = fastMotionSpec)
          },
          label = "device-connect-content"
        ) { connecting ->
          if (connecting) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
              verticalAlignment = Alignment.CenterVertically
            ) {
              CircularProgressIndicator(
                modifier = Modifier.size(Spacing.lg),
                strokeWidth = Spacing.xs / 2,
                color = MaterialTheme.colorScheme.onPrimary
              )
              Text("Connecting…")
            }
          } else {
            Text("Connect")
          }
        }
      }
    }
  }
}

@Composable
private fun CapabilityBadges(capabilities: Set<DeviceCapability>) {
  Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
    capabilities
      .sortedBy { it.ordinal }
      .forEach { capability ->
        AssistChip(
          onClick = {},
          enabled = false,
          modifier = Modifier,
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
    add(
      if (device.device.rssi == 0) "Signal connected" else "Signal ${device.device.rssi} dBm"
    )
  }
  return details.joinToString(" · ")
}
