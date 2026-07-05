package com.trainerloop.ui.settings

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SettingsScreen(
  viewModel: SettingsViewModel = viewModel()
) {
  val uiState by viewModel.uiState.collectAsState()
  var activeDialog by remember { mutableStateOf<SettingsDialog?>(null) }

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

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Text(
      text = "Profile",
      style = MaterialTheme.typography.headlineLarge
    )

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

    ProfileFieldCard(
      title = "Heart Rate",
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
        )
      )
    )

    SettingsGroupCard(title = "Preferences") {
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
        icon = Icons.Default.Person,
        label = "Coach Profile",
        trailing = {
          CompactNumberField(
            value = uiState.selectedCoach,
            keyboardType = KeyboardType.Text,
            onValueChange = viewModel::updateSelectedCoach
          )
        }
      )
    }

    SettingsGroupCard(title = "Zones") {
      SettingsRow(
        icon = Icons.Default.Power,
        label = "Power Zones",
        onClick = { activeDialog = SettingsDialog.POWER_ZONES }
      )
      HorizontalDivider()
      SettingsRow(
        icon = Icons.Default.Favorite,
        label = "Heart Rate Zones",
        onClick = { activeDialog = SettingsDialog.HR_ZONES }
      )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(
          text = "intervals.icu",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
          value = uiState.intervalsAthleteId,
          onValueChange = viewModel::updateIntervalsAthleteId,
          label = { Text("Athlete ID") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
          value = uiState.intervalsApiKey,
          onValueChange = viewModel::updateIntervalsApiKey,
          label = { Text("API Key") },
          singleLine = true,
          visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
          modifier = Modifier.fillMaxWidth()
        )
      }
    }

    SettingsGroupCard(title = "Support") {
      SettingsRow(
        icon = Icons.Default.Info,
        label = "About",
        onClick = { activeDialog = SettingsDialog.ABOUT }
      )
    }

    Button(
      onClick = { viewModel.save() },
      modifier = Modifier.fillMaxWidth()
    ) {
      Text("Save")
    }

    if (uiState.isSaved) {
      Text(
        text = "Profile saved",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.align(Alignment.CenterHorizontally)
      )
    }
  }
}

private enum class SettingsDialog { POWER_ZONES, HR_ZONES, ABOUT }

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
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
      )
      Spacer(modifier = Modifier.height(12.dp))
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
          Spacer(modifier = Modifier.height(8.dp))
        }
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
    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
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
  title: String,
  content: @Composable () -> Unit
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
      )
      Spacer(modifier = Modifier.height(8.dp))
      content()
    }
  }
}

@Composable
private fun SettingsRow(
  icon: ImageVector,
  label: String,
  onClick: (() -> Unit)? = null,
  trailing: @Composable (() -> Unit)? = null
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      .padding(vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
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
        contentDescription = "Open $label",
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
  }
}
