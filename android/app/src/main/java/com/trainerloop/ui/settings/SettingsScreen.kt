package com.trainerloop.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
      .padding(16.dp)
  ) {
    Text(
      text = "Settings",
      style = MaterialTheme.typography.headlineLarge
    )
    Spacer(modifier = Modifier.height(16.dp))

    SettingsField(
      label = "FTP (Watts)",
      value = uiState.ftp,
      onValueChange = { viewModel.updateFtp(it) }
    )
    Spacer(modifier = Modifier.height(8.dp))

    SettingsField(
      label = "Weight (kg)",
      value = uiState.weightKg,
      onValueChange = { viewModel.updateWeight(it) },
      keyboardType = KeyboardType.Decimal
    )
    Spacer(modifier = Modifier.height(8.dp))

    SettingsField(
      label = "Max HR (BPM)",
      value = uiState.maxHr,
      onValueChange = { viewModel.updateMaxHr(it) }
    )
    Spacer(modifier = Modifier.height(8.dp))

    SettingsField(
      label = "ERG Bias (%)",
      value = uiState.ergBias,
      onValueChange = { viewModel.updateErgBias(it) }
    )
    Spacer(modifier = Modifier.height(16.dp))

    Button(
      onClick = { viewModel.save() },
      modifier = Modifier.fillMaxWidth()
    ) {
      Text("Save")
    }

    if (uiState.isSaved) {
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = "Settings saved",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary
      )
    }
  }
}

@Composable
private fun SettingsField(
  label: String,
  value: String,
  onValueChange: (String) -> Unit,
  keyboardType: KeyboardType = KeyboardType.Number
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.weight(1f)
    )
    OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
      singleLine = true,
      modifier = Modifier.width(120.dp)
    )
  }
}
