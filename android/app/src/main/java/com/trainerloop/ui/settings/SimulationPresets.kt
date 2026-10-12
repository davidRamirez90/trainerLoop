package com.trainerloop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class SimulationPreset(
  val name: String,
  val value: String,
  val description: String,
  val isDefault: Boolean = false
)

internal val bikePresets = listOf(
  SimulationPreset("Light road", "6.0", "A lightweight race bike"),
  SimulationPreset("Race road", "7.0", "A race bike with everyday components"),
  SimulationPreset("Road", "8.0", "An everyday road bike", isDefault = true),
  SimulationPreset("Endurance", "9.0", "A road bike built for long, comfortable rides"),
  SimulationPreset("Gravel", "10.0", "A sturdy bike for mixed surfaces"),
  SimulationPreset("City / MTB", "12.0", "A heavier everyday or mountain bike"),
  SimulationPreset("Loaded touring", "15.0", "A touring bike carrying bags and supplies")
)
internal val surfacePresets = listOf(
  SimulationPreset("Track", "0.002", "A very smooth track with fast-rolling tyres"),
  SimulationPreset("Smooth", "0.003", "Fresh asphalt with fast-rolling tyres"),
  SimulationPreset("Good pavement", "0.004", "A well-maintained paved road"),
  SimulationPreset("Regular road", "0.005", "Typical paved roads", isDefault = true),
  SimulationPreset("Worn pavement", "0.006", "Older asphalt with a little more texture"),
  SimulationPreset("Rough pavement", "0.0075", "Uneven roads with a noticeably slower roll"),
  SimulationPreset("Firm gravel", "0.009", "Packed gravel with more rolling resistance")
)
internal val positionPresets = listOf(
  SimulationPreset("Race tuck", "0.18", "A low racing position with elbows tucked in"),
  SimulationPreset("Tucked", "0.22", "Low over the bars, out of the wind"),
  SimulationPreset("In the drops", "0.27", "Leaning forward with hands on the lower bars"),
  SimulationPreset("On the hoods", "0.32", "A relaxed road riding position", isDefault = true),
  SimulationPreset("Relaxed hoods", "0.37", "A little taller over the bars for comfort"),
  SimulationPreset("Mostly upright", "0.42", "An easy, open position with a slight forward lean"),
  SimulationPreset("Upright", "0.48", "Sitting tall for comfort")
)

internal fun nearestPreset(value: String, presets: List<SimulationPreset>): Int {
  val default = presets.firstOrNull { it.isDefault } ?: presets.first()
  val numericValue = value.toDoubleOrNull() ?: default.value.toDouble()
  return presets.indices.minBy { abs(numericValue - presets[it].value.toDouble()) }
}

@Composable
internal fun SimulationPresetSlider(label: String, value: String, presets: List<SimulationPreset>, onChange: (String) -> Unit) {
  val index = nearestPreset(value, presets)
  val preset = presets[index]
  val custom = value.toDoubleOrNull()?.let { abs(it - preset.value.toDouble()) > 0.00001 } ?: false
  Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(if (custom) "Custom · closest to ${preset.name}" else preset.name,
        modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary)
      Text("${index + 1} / ${presets.size}", style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Slider(value = index.toFloat(), onValueChange = { onChange(presets[it.roundToInt().coerceIn(presets.indices)].value) },
      valueRange = 0f..presets.lastIndex.toFloat(), steps = presets.size - 2,
      modifier = Modifier.semantics {
        contentDescription = label
        stateDescription = if (custom) "Custom, closest to ${preset.name}" else preset.name
      })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(presets.first().name, style = MaterialTheme.typography.labelSmall)
      Text(presets.last().name, style = MaterialTheme.typography.labelSmall)
    }
    Text(preset.description, style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
