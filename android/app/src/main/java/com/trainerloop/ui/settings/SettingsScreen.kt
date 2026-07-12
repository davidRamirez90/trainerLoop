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
import androidx.compose.material.icons.filled.Person
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
    SettingsDialog.COACH_PROFILE -> CoachPickerDialog(
      selectedId = uiState.selectedCoach,
      onSelect = { id ->
        viewModel.updateSelectedCoach(id)
        activeDialog = null
      },
      onDismiss = { activeDialog = null }
    )
    null -> Unit
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = Spacing.screenMargin),
    verticalArrangement = Arrangement.spacedBy(Spacing.sectionGap)
  ) {
    Text(
      text = "Profile",
      style = MaterialTheme.typography.headlineLarge,
      modifier = Modifier.padding(top = Spacing.controlGap)
    )

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
          ProfileField(
            label = "Resting HR",
            value = uiState.restingHr,
            suffix = "bpm",
            onValueChange = viewModel::updateRestingHr
          ),
          ProfileField(
            label = "LTHR (optional)",
            value = uiState.lthr,
            suffix = "bpm",
            onValueChange = viewModel::updateLthr
          )
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

    // Coaching: the coral-accented section — coral never appears on a toggle.
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.controlGap)) {
      SectionHeader(title = "Coaching")
      SettingsGroupCard {
        SettingsRow(
          icon = Icons.Default.Person,
          label = "Coach",
          iconTint = MaterialTheme.trainerLoopColors.coach,
          trailing = {
            androidx.compose.material3.Switch(
              checked = uiState.coachEnabled,
              onCheckedChange = viewModel::updateCoachEnabled
            )
          }
        )
        HorizontalDivider()
        SettingsRow(
          icon = Icons.Default.Person,
          label = "Coach Profile",
          iconTint = MaterialTheme.trainerLoopColors.coach,
          trailing = {
            Text(
              text = uiState.selectedCoach,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          },
          onClick = { activeDialog = SettingsDialog.COACH_PROFILE }
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
            Text("Simulated route + speed", style = MaterialTheme.typography.bodyMedium)
            Text(
              "Terrain overlay and physics-based speed during workouts",
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
        TextButton(onClick = { advancedExpanded = !advancedExpanded }) {
          Text(if (advancedExpanded) "Hide advanced" else "Advanced")
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
            LabeledSlider(
              label = "Trainer difficulty",
              valueText = "${uiState.trainerDifficultyPct} %",
              hint = "How much of a GPX route's gradient you feel on free rides",
              value = uiState.trainerDifficultyPct.toFloat(),
              valueRange = 0f..100f,
              steps = 19, // 5 % increments
              onValueChange = { viewModel.updateTrainerDifficulty(it.toInt()) }
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

private enum class SettingsDialog { POWER_ZONES, HR_ZONES, ABOUT, COACH_PROFILE }

@Composable
private fun CoachPickerDialog(
  selectedId: String,
  onSelect: (String) -> Unit,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val profiles = remember {
    com.trainerloop.data.source.local.CoachProfileLoader.listProfiles(context)
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Coach Profile") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        profiles.forEach { profile ->
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(
                if (profile.id == selectedId) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
              )
              .clickable { onSelect(profile.id) }
              .padding(Spacing.lg)
          ) {
            Text(text = profile.name, fontWeight = FontWeight.SemiBold)
            Text(
              text = profile.description,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("Close") }
    }
  )
}

private data class Zone(val name: String, val range: String)

// ponytail: standard Coggan 7-zone / 5-zone HR models, display only
private fun powerZones(ftp: Int): List<Zone> {
  if (ftp <= 0) return emptyList()
  fun pct(p: Int) = ftp * p / 100
  return listOf(
    Zone("Z1 Recovery", "0–${pct(55)} W"),
    Zone("Z2 Endurance", "${pct(56)}–${pct(75)} W"),
    Zone("Z3 Tempo", "${pct(76)}–${pct(90)} W"),
    Zone("Z4 Threshold", "${pct(91)}–${pct(105)} W"),
    Zone("Z5 VO2 Max", "${pct(106)}–${pct(120)} W"),
    Zone("Z6 Anaerobic", "${pct(121)}–${pct(150)} W"),
    Zone("Z7 Neuromuscular", "${pct(150)}+ W")
  )
}

private fun hrZones(maxHr: Int): List<Zone> {
  if (maxHr <= 0) return emptyList()
  fun pct(p: Int) = maxHr * p / 100
  return listOf(
    Zone("Z1 Recovery", "0–${pct(60)} bpm"),
    Zone("Z2 Endurance", "${pct(60)}–${pct(70)} bpm"),
    Zone("Z3 Tempo", "${pct(70)}–${pct(80)} bpm"),
    Zone("Z4 Threshold", "${pct(80)}–${pct(90)} bpm"),
    Zone("Z5 Max", "${pct(90)}–$maxHr bpm")
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
    title = { Text(title) },
    text = {
      Column {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (zones.isEmpty()) {
          Text("Set your FTP / Max HR above to see zones.")
        }
        zones.forEach { zone ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(zone.name, style = MaterialTheme.typography.bodyMedium)
            Text(
              zone.range,
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("Close") }
    }
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
