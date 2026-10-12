package com.trainerloop.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainerloop.ui.components.PrimaryActionButton
import com.trainerloop.ui.components.SectionHeader
import com.trainerloop.ui.components.TrainerLoopCard
import com.trainerloop.ui.theme.MotionSpec
import com.trainerloop.ui.theme.Spacing
import com.trainerloop.ui.theme.reducedMotionAware
import com.trainerloop.ui.theme.trainerLoopColors
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
  onBack: () -> Unit = {},
  viewModel: SettingsViewModel = viewModel()
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var activeDialog by remember { mutableStateOf<SettingsDialog?>(null) }
  var apiKeyVisible by remember { mutableStateOf(false) }

  DisposableEffect(Unit) {
    onDispose { viewModel.save() }
  }

  LaunchedEffect(uiState.isSaved) {
    if (uiState.isSaved) {
      delay(2_000)
      viewModel.clearSavedStatus()
    }
  }

  when (activeDialog) {
    SettingsDialog.POWER_ZONES -> ZonesDialog(
      title = "Power Zones",
      zones = powerZones(uiState.ftp.toIntOrNull() ?: 0),
      subtitle = "Based on FTP ${uiState.ftp} W",
      onDismiss = { activeDialog = null }
    )
    SettingsDialog.HR_ZONES -> ZonesDialog(
      title = "Heart Rate Zones",
      zones = hrZones(uiState.maxHr.toIntOrNull() ?: 0),
      subtitle = "Based on Max HR ${uiState.maxHr} bpm",
      onDismiss = { activeDialog = null }
    )
    SettingsDialog.ABOUT -> AboutDialog(onDismiss = { activeDialog = null })
    null -> Unit
  }

  androidx.compose.material3.Scaffold(
    contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    topBar = { com.trainerloop.ui.components.TrainerLoopTopBar(
      title = "Settings", onBack = onBack, windowInsets = androidx.compose.foundation.layout.WindowInsets(0)) }
  ) { screenPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(screenPadding)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = Spacing.screenMargin),
      verticalArrangement = Arrangement.spacedBy(Spacing.sectionGap)
    ) {
      // Athlete: identity + the metrics every other section derives from.
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.controlGap)) {
        SectionHeader(title = "Athlete")
        RiderProfileHeader(
          name = uiState.name,
          onNameChange = viewModel::updateName
        )
        ProfileFieldCard(
          title = "Key Metrics",
          fields = listOf(
            ProfileField(
              label = "FTP",
              value = uiState.ftp,
              suffix = "W",
              onValueChange = viewModel::updateFtp
            ),
            ProfileField(
              label = "Weight",
              value = uiState.weightKg,
              suffix = "kg",
              keyboardType = KeyboardType.Decimal,
              onValueChange = viewModel::updateWeight
            )
          )
        )
        SettingsGroupCard {
          SettingsRow(
            icon = Icons.Default.Power,
            label = "Power Zones",
            onClick = { activeDialog = SettingsDialog.POWER_ZONES }
          )
        }
      }

      // Heart Rate: HR-derived metrics and the zone table they feed.
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.controlGap)) {
        SectionHeader(title = "Heart Rate")
        ProfileFieldCard(
          title = "Heart Rate Metrics",
          fields = listOf(
            ProfileField(
              label = "Max HR",
              value = uiState.maxHr,
              suffix = "bpm",
              onValueChange = viewModel::updateMaxHr
            ),
          )
        )
        SettingsGroupCard {
          SettingsRow(
            icon = Icons.Default.Favorite,
            label = "Heart Rate Zones",
            onClick = { activeDialog = SettingsDialog.HR_ZONES }
          )
        }
      }

      // Ride Preferences: how a normal ride behaves.
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.controlGap)) {
        SectionHeader(title = "Ride Preferences")
        SettingsGroupCard {
          SettingsRow(
            icon = Icons.Default.Power,
            label = "ERG Bias",
            trailing = {
              CompactNumberField(
                value = uiState.ergBias,
                suffix = "%",
                onValueChange = viewModel::updateErgBias
              )
            }
          )
          HorizontalDivider()
          SettingsRow(
            icon = Icons.Default.Notifications,
            label = "Completion sound",
            trailing = {
              androidx.compose.material3.Switch(
                checked = uiState.completionSoundEnabled,
                onCheckedChange = viewModel::updateCompletionSoundEnabled
              )
            }
          )
        }
      }

      // Simulation: virtual-ride terrain + advanced physics, collapsed by default.
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.controlGap)) {
        SectionHeader(title = "Simulation")
        SettingsGroupCard {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Virtual ride", style = MaterialTheme.typography.bodyMedium)
              Text(
                "See a virtual route and estimated outdoor speed during workouts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            androidx.compose.material3.Switch(
              checked = uiState.virtualRideEnabled,
              onCheckedChange = viewModel::updateVirtualRideEnabled
            )
          }

          var advancedExpanded by remember { mutableStateOf(false) }
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text("Advanced mode", style = MaterialTheme.typography.bodyMedium)
              Text("Fine-tune the exact values", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.Switch(checked = advancedExpanded,
              onCheckedChange = { advancedExpanded = it },
              modifier = Modifier.semantics { contentDescription = "Advanced mode" })
          }
          if (!advancedExpanded) {
            Text("Choose what your outdoor ride feels like. These settings affect estimated speed, not workout power.",
              style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SimulationPresetSlider("Bike type", uiState.bikeWeightKg, bikePresets, viewModel::updateBikeWeight)
            SimulationPresetSlider("Road surface", uiState.crr, surfacePresets, viewModel::updateCrr)
            SimulationPresetSlider("Riding position", uiState.cda, positionPresets, viewModel::updateCda)
          }
          AnimatedVisibility(
            visible = advancedExpanded,
            enter = expandVertically(
              animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
            ) + fadeIn(
              animationSpec = reducedMotionAware(MotionSpec.defaultSpring<Float>())
            ),
            exit = shrinkVertically(
              animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
            ) + fadeOut(
              animationSpec = reducedMotionAware(MotionSpec.defaultSpring<Float>())
            )
          ) {
            Column(
              modifier = Modifier.fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(Spacing.controlGap)
            ) {
              LabeledSlider(
                label = "Bike weight",
                valueText = "${uiState.bikeWeightKg} kg",
                hint = null,
                value = uiState.bikeWeightKg.toFloatOrNull() ?: 8.0f,
                valueRange = 5f..15f,
                steps = 19, // 0.5 kg increments
                onValueChange = { viewModel.updateBikeWeight(fmt(it, 1)) }
              )
              LabeledSlider(
                label = "Rolling resistance (Crr)",
                valueText = uiState.crr,
                hint = crrHint(uiState.crr.toDoubleOrNull() ?: 0.005),
                value = uiState.crr.toFloatOrNull() ?: 0.005f,
                valueRange = 0.002f..0.010f,
                steps = 15, // 0.0005 increments
                onValueChange = { viewModel.updateCrr(fmt(it, 4)) }
              )
              LabeledSlider(
                label = "Aero drag (CdA)",
                valueText = "${uiState.cda} m²",
                hint = cdaHint(uiState.cda.toDoubleOrNull() ?: 0.32),
                value = uiState.cda.toFloatOrNull() ?: 0.32f,
                valueRange = 0.15f..0.60f,
                steps = 44, // 0.01 increments
                onValueChange = { viewModel.updateCda(fmt(it, 2)) }
              )
              TextButton(onClick = { viewModel.resetPhysicsDefaults() }) {
                Text("Reset to defaults")
              }
            }
          }
        }
      }

      // Connections: third-party sync — API credentials stay masked with reveal.
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.controlGap)) {
        SectionHeader(title = "Connections")
        TrainerLoopCard(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = "intervals.icu",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
          )
          Spacer(modifier = Modifier.height(Spacing.controlGap))
          OutlinedTextField(
            value = uiState.intervalsAthleteId,
            onValueChange = viewModel::updateIntervalsAthleteId,
            label = { Text("Athlete ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          Spacer(modifier = Modifier.height(Spacing.sm))
          OutlinedTextField(
            value = uiState.intervalsApiKey,
            onValueChange = viewModel::updateIntervalsApiKey,
            label = { Text("API Key") },
            singleLine = true,
            visualTransformation = if (apiKeyVisible) {
              VisualTransformation.None
            } else {
              PasswordVisualTransformation()
            },
            trailingIcon = {
              androidx.compose.material3.IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                Icon(
                  imageVector = if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                  contentDescription = if (apiKeyVisible) "Hide API key" else "Show API key"
                )
              }
            },
            modifier = Modifier.fillMaxWidth()
          )
        }
      }

      // App: about / support.
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.controlGap)) {
        SectionHeader(title = "App")
        SettingsGroupCard {
          SettingsRow(
            icon = Icons.Default.Info,
            label = "About",
            onClick = { activeDialog = SettingsDialog.ABOUT }
          )
        }
      }

      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PrimaryActionButton(
          onClick = { viewModel.save() },
          modifier = Modifier.fillMaxWidth()
        ) {
          Text("Save")
        }
        AnimatedVisibility(
          visible = uiState.isSaved,
          enter = fadeIn(
            animationSpec = reducedMotionAware(MotionSpec.defaultSpring<Float>())
          ),
          exit = fadeOut(
            animationSpec = reducedMotionAware(MotionSpec.defaultSpring<Float>())
          )
        ) {
          Text(
            text = "Saved ✓",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.trainerLoopColors.connected,
            modifier = Modifier.padding(top = Spacing.sm)
          )
        }
      }
    }
  }
}
private enum class SettingsDialog { POWER_ZONES, HR_ZONES, ABOUT }

private data class Zone(val name: String, val range: String, val effort: String)

// Power-zone boundaries match live charts and saved-session distributions.
private fun powerZones(ftp: Int): List<Zone> {
  if (ftp <= 0) return emptyList()
  val bounds = com.trainerloop.domain.PowerZoneMath.lowerBoundsWatts(ftp)
  val names = listOf("Recovery", "Endurance", "Tempo", "Threshold", "VO2 Max", "Anaerobic")
  return names.mapIndexed { index, name ->
    val upper = bounds.getOrNull(index + 1)?.minus(1)
    val range = when {
      upper == null -> "${bounds[index]}+ W"
      upper < bounds[index] -> "—"
      else -> "${bounds[index]}–$upper W"
    }
    Zone("Z${index + 1} $name", range,
      listOf("Easy spinning", "Comfortable, steady effort", "Brisk, controlled effort",
        "Hard, sustained effort", "Very hard intervals", "Short, intense bursts")[index])
  }
}

private fun hrZones(maxHr: Int): List<Zone> {
  if (maxHr <= 0) return emptyList()
  fun pct(p: Int) = maxHr * p / 100
  return listOf(
    Zone("Z1 Recovery", "0–${pct(60)} bpm", "Easy, relaxed effort"),
    Zone("Z2 Endurance", "${pct(60)}–${pct(70)} bpm", "Comfortable, steady effort"),
    Zone("Z3 Tempo", "${pct(70)}–${pct(80)} bpm", "Brisk, controlled effort"),
    Zone("Z4 Threshold", "${pct(80)}–${pct(90)} bpm", "Hard, sustained effort"),
    Zone("Z5 Max", "${pct(90)}–$maxHr bpm", "Very hard effort")
  )
}

@Composable
private fun ZonesDialog(
  title: String,
  subtitle: String,
  zones: List<Zone>,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.background,
    tonalElevation = 0.dp,
    titleContentColor = MaterialTheme.colorScheme.onBackground,
    textContentColor = MaterialTheme.colorScheme.onSurface,
    title = { Text(title, style = MaterialTheme.typography.headlineMedium) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(subtitle, style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.lg))
        if (zones.isEmpty()) {
          Text("Set your FTP / Max HR above to see zones.")
        } else {
          androidx.compose.material3.Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs)) {
              zones.forEachIndexed { index, zone ->
                Row(Modifier.fillMaxWidth().padding(vertical = Spacing.md),
                  horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                  verticalAlignment = Alignment.Top) {
                  androidx.compose.material3.Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                  ) {
                    Text(zone.name.substringBefore(" "), style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm))
                  }
                  Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(zone.name.substringAfter(" "), style = MaterialTheme.typography.titleMedium,
                      color = MaterialTheme.colorScheme.onSurface)
                    Text(zone.range, style = MaterialTheme.typography.bodyMedium,
                      fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Text(zone.effort, style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
                if (index < zones.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
              }
            }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
  )
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
  val context = LocalContext.current
  val version = remember {
    try {
      context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (_: Exception) {
      "?"
    }
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("TrainerLoop") },
    text = { Text("Version $version\n\nIndoor cycling trainer control, workouts and telemetry.") },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("Close") }
    }
  )
}

@Composable
private fun RiderProfileHeader(
  name: String,
  onNameChange: (String) -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(64.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.primaryContainer),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = name.take(1).uppercase().takeIf { it.isNotBlank() } ?: "?",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        fontWeight = FontWeight.Bold
      )
    }

    Spacer(modifier = Modifier.width(16.dp))

    OutlinedTextField(
      value = name,
      onValueChange = onNameChange,
      label = { Text("Rider Name") },
      singleLine = true,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.weight(1f)
    )
  }
}

private data class ProfileField(
  val label: String,
  val value: String,
  val suffix: String,
  val keyboardType: KeyboardType = KeyboardType.Number,
  val onValueChange: (String) -> Unit
)

@Composable
private fun ProfileFieldCard(
  title: String,
  fields: List<ProfileField>
) {
  TrainerLoopCard(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(Spacing.controlGap))
    fields.forEachIndexed { index, field ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = field.label,
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.weight(1f)
        )
        CompactNumberField(
          value = field.value,
          suffix = field.suffix,
          keyboardType = field.keyboardType,
          onValueChange = field.onValueChange
        )
      }
      if (index < fields.lastIndex) {
        Spacer(modifier = Modifier.height(Spacing.sm))
      }
    }
  }
}

@Composable
private fun CompactNumberField(
  value: String,
  suffix: String = "",
  keyboardType: KeyboardType = KeyboardType.Number,
  onValueChange: (String) -> Unit
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    singleLine = true,
    shape = RoundedCornerShape(12.dp),
    suffix = if (suffix.isNotBlank()) {
      { Text(suffix, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    } else null,
    modifier = Modifier.width(120.dp)
  )
}

@Composable
private fun SettingsGroupCard(
  title: String? = null,
  content: @Composable () -> Unit
) {
  TrainerLoopCard(modifier = Modifier.fillMaxWidth()) {
    if (title != null) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
      )
      Spacer(modifier = Modifier.height(Spacing.sm))
    }
    content()
  }
}

@Composable
internal fun LabeledSlider(
  label: String,
  valueText: String,
  hint: String?,
  value: Float,
  valueRange: ClosedFloatingPointRange<Float>,
  steps: Int,
  onValueChange: (Float) -> Unit
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(label, style = MaterialTheme.typography.bodyMedium)
      Text(valueText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
    androidx.compose.material3.Slider(
      value = value,
      onValueChange = onValueChange,
      valueRange = valueRange,
      steps = steps
    )
    if (hint != null) {
      Text(
        hint,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

/** Locale-safe decimal formatting — the ViewModel parses with toDoubleOrNull(). */
private fun fmt(v: Float, decimals: Int): String =
  String.format(java.util.Locale.US, "%.${decimals}f", v)

private fun crrHint(v: Double): String = when {
  v <= 0.0035 -> "Very fast surface (track, new asphalt)"
  v <= 0.0055 -> "Smooth asphalt road"
  v <= 0.0080 -> "Rough or worn road"
  else -> "Gravel / poor surface"
}

private fun cdaHint(v: Double): String = when {
  v <= 0.25 -> "Aggressive aero position (drops / TT)"
  v <= 0.35 -> "Road position on the hoods"
  v <= 0.45 -> "Upright endurance position"
  else -> "Very upright (MTB / city bike)"
}

@Composable
private fun SettingsRow(
  icon: ImageVector,
  label: String,
  onClick: (() -> Unit)? = null,
  iconTint: androidx.compose.ui.graphics.Color? = null,
  trailing: @Composable (() -> Unit)? = null
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      .heightIn(min = 48.dp)
      .padding(vertical = Spacing.sm),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(modifier = Modifier.width(16.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge
      )
    }
    trailing?.invoke()
      ?: Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
  }
}
