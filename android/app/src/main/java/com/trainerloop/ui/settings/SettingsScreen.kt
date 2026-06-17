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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
      ftp = uiState.ftp.toIntOrNull() ?: 0,
      weightKg = uiState.weightKg.toDoubleOrNull() ?: 0.0,
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
            onValueChange = viewModel::updateSelectedCoach
          )
        }
      )
    }

    SettingsGroupCard(title = "Settings") {
      SettingsRow(
        icon = Icons.Default.FitnessCenter,
        label = "FTP Settings",
        onClick = { /* Detail screen not in scope */ }
      )
      HorizontalDivider()
      SettingsRow(
        icon = Icons.Default.Power,
        label = "Power Zones",
        onClick = { /* Detail screen not in scope */ }
      )
      HorizontalDivider()
      SettingsRow(
        icon = Icons.Default.Favorite,
        label = "Heart Rate Zones",
        onClick = { /* Detail screen not in scope */ }
      )
      HorizontalDivider()
      SettingsRow(
        icon = Icons.Default.Bluetooth,
        label = "Connected Apps",
        onClick = { /* Detail screen not in scope */ }
      )
      HorizontalDivider()
      SettingsRow(
        icon = Icons.Default.Build,
        label = "Trainer Settings",
        onClick = { /* Detail screen not in scope */ }
      )
      HorizontalDivider()
      SettingsRow(
        icon = Icons.Default.Straighten,
        label = "Units",
        onClick = { /* Detail screen not in scope */ }
      )
      HorizontalDivider()
      SettingsRow(
        icon = Icons.Default.Brightness6,
        label = "Theme",
        trailing = {
          Switch(
            checked = false,
            onCheckedChange = { /* Theme toggle not in scope */ }
          )
        }
      )
    }

    SettingsGroupCard(title = "Support") {
      SettingsRow(
        icon = Icons.AutoMirrored.Filled.Help,
        label = "Help & Support",
        onClick = { /* Not in scope */ }
      )
      HorizontalDivider()
      SettingsRow(
        icon = Icons.Default.Info,
        label = "About",
        onClick = { /* Not in scope */ }
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

@Composable
private fun RiderProfileHeader(
  name: String,
  ftp: Int,
  weightKg: Double,
  onNameChange: (String) -> Unit
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
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(72.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = name.take(1).uppercase().takeIf { it.isNotBlank() } ?: "?",
          style = MaterialTheme.typography.headlineLarge,
          color = MaterialTheme.colorScheme.onPrimary,
          fontWeight = FontWeight.Bold
        )
      }

      Spacer(modifier = Modifier.width(16.dp))

      Column(modifier = Modifier.weight(1f)) {
        OutlinedTextField(
          value = name,
          onValueChange = onNameChange,
          label = { Text("Rider Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          MetricBadge(label = "FTP", value = "$ftp W")
          MetricBadge(label = "Weight", value = "${"%.1f".format(weightKg)} kg")
        }
      }
    }
  }
}

@Composable
private fun MetricBadge(label: String, value: String) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface
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
  Row(verticalAlignment = Alignment.CenterVertically) {
    OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
      singleLine = true,
      modifier = Modifier.width(100.dp)
    )
    if (suffix.isNotBlank()) {
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = suffix,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
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
