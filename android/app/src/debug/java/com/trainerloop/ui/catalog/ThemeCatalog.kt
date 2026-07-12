package com.trainerloop.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.trainerloop.ui.theme.TrainerLoopTheme
import com.trainerloop.ui.theme.ZoneColors
import com.trainerloop.ui.theme.trainerLoopColors

private data class CatalogColor(
  val name: String,
  val color: Color
)

@Composable
fun ThemeCatalogScreen() {
  val scheme = MaterialTheme.colorScheme
  val semantic = MaterialTheme.trainerLoopColors
  val materialColors = listOf(
    CatalogColor("primary", scheme.primary),
    CatalogColor("onPrimary", scheme.onPrimary),
    CatalogColor("primaryContainer", scheme.primaryContainer),
    CatalogColor("onPrimaryContainer", scheme.onPrimaryContainer),
    CatalogColor("secondary", scheme.secondary),
    CatalogColor("onSecondary", scheme.onSecondary),
    CatalogColor("secondaryContainer", scheme.secondaryContainer),
    CatalogColor("onSecondaryContainer", scheme.onSecondaryContainer),
    CatalogColor("tertiary", scheme.tertiary),
    CatalogColor("onTertiary", scheme.onTertiary),
    CatalogColor("tertiaryContainer", scheme.tertiaryContainer),
    CatalogColor("onTertiaryContainer", scheme.onTertiaryContainer),
    CatalogColor("background", scheme.background),
    CatalogColor("onBackground", scheme.onBackground),
    CatalogColor("surface", scheme.surface),
    CatalogColor("onSurface", scheme.onSurface),
    CatalogColor("surfaceVariant", scheme.surfaceVariant),
    CatalogColor("onSurfaceVariant", scheme.onSurfaceVariant),
    CatalogColor("surfaceContainer", scheme.surfaceContainer),
    CatalogColor("surfaceContainerHigh", scheme.surfaceContainerHigh),
    CatalogColor("surfaceContainerHighest", scheme.surfaceContainerHighest),
    CatalogColor("error", scheme.error),
    CatalogColor("onError", scheme.onError),
    CatalogColor("errorContainer", scheme.errorContainer),
    CatalogColor("onErrorContainer", scheme.onErrorContainer),
    CatalogColor("outline", scheme.outline)
  )
  val semanticColors = listOf(
    CatalogColor("ready", semantic.ready),
    CatalogColor("onReady", semantic.onReady),
    CatalogColor("coach", semantic.coach),
    CatalogColor("onCoach", semantic.onCoach),
    CatalogColor("connected", semantic.connected),
    CatalogColor("onConnected", semantic.onConnected),
    CatalogColor("warning", semantic.warning),
    CatalogColor("onWarning", semantic.onWarning),
    CatalogColor("stale", semantic.stale),
    CatalogColor("onStale", semantic.onStale),
    CatalogColor("heroAction", semantic.heroAction),
    CatalogColor("onHeroAction", semantic.onHeroAction),
    CatalogColor("chartPower", semantic.chartPower),
    CatalogColor("chartHeartRate", semantic.chartHeartRate),
    CatalogColor("chartCadence", semantic.chartCadence),
    CatalogColor("chartElevation", semantic.chartElevation),
    CatalogColor("chartGrid", semantic.chartGrid),
    CatalogColor("chartCursor", semantic.chartCursor)
  )

  Surface(modifier = Modifier.fillMaxSize(), color = scheme.background) {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
      verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
      item { CatalogTitle() }
      item { ColorSection("Material roles", materialColors) }
      item { ColorSection("Semantic roles", semanticColors) }
      item { ComponentSection() }
      item { StatusSection() }
      item { MetricSection() }
      item { ZoneSection() }
    }
  }
}

@Composable
private fun CatalogTitle() {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text("Theme catalog", style = MaterialTheme.typography.headlineLarge)
    Text(
      "Debug-only palette and component proof",
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      style = MaterialTheme.typography.bodyMedium
    )
  }
}

@Composable
private fun ColorSection(title: String, colors: List<CatalogColor>) {
  CatalogSection(title) {
    colors.forEach { entry ->
      SwatchRow(entry)
    }
  }
}

@Composable
private fun SwatchRow(entry: CatalogColor) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(40.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(entry.color)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
    )
    Spacer(Modifier.width(12.dp))
    Text(entry.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    Text(
      entry.color.toCatalogHex(),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
    )
  }
}

private fun Color.toCatalogHex(): String =
  "#" + toArgb().toUInt().toString(16).padStart(8, '0').uppercase()

@Composable
private fun CatalogSection(title: String, content: @Composable ColumnScope.() -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    content()
  }
}

@Composable
private fun ComponentSection() {
  val semantic = MaterialTheme.trainerLoopColors
  CatalogSection("Components") {
    Button(
      onClick = {},
      colors = ButtonDefaults.buttonColors(
        containerColor = semantic.heroAction,
        contentColor = semantic.onHeroAction
      )
    ) {
      Text("Primary action")
    }
    OutlinedButton(onClick = {}) { Text("Outlined action") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FilterChip(selected = true, onClick = {}, label = { Text("Selected") })
      FilterChip(selected = false, onClick = {}, label = { Text("Unselected") })
    }
    OutlinedTextField(
      value = "Endurance",
      onValueChange = {},
      modifier = Modifier.fillMaxWidth(),
      label = { Text("Workout name") }
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = {}, enabled = false) { Text("Disabled") }
      OutlinedButton(onClick = {}, enabled = false) { Text("Disabled") }
      FilterChip(selected = false, onClick = {}, enabled = false, label = { Text("Disabled") })
    }
    OutlinedTextField(
      value = "Unavailable",
      onValueChange = {},
      enabled = false,
      modifier = Modifier.fillMaxWidth(),
      label = { Text("Disabled field") }
    )
  }
}

@Composable
private fun StatusSection() {
  val colors = MaterialTheme.trainerLoopColors
  CatalogSection("Status pills") {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      StatusPill("Connected", Icons.Default.CheckCircle, colors.connected, colors.onConnected)
      StatusPill("Scanning", Icons.Default.Search, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      StatusPill("Reconnecting", Icons.Default.Sync, colors.warning, colors.onWarning)
      StatusPill("Failed", Icons.Default.Error, MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError)
    }
    StatusPill("Unavailable", Icons.Default.Block, colors.stale, colors.onStale)
  }
}

@Composable
private fun StatusPill(label: String, icon: ImageVector, container: Color, content: Color) {
  Row(
    modifier = Modifier
      .clip(CircleShape)
      .background(container)
      .padding(horizontal = 10.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = content)
    Text(label, color = content, style = MaterialTheme.typography.labelMedium)
  }
}

@Composable
private fun MetricSection() {
  val colors = MaterialTheme.trainerLoopColors
  CatalogSection("Metric states") {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      MetricTile("Power", "248", "W", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
      MetricTile("Heart rate", "147", "bpm", colors.stale, Modifier.weight(1f))
      MetricTile("Cadence", "—", "rpm", colors.stale, Modifier.weight(1f))
    }
  }
}

@Composable
private fun MetricTile(label: String, value: String, unit: String, valueColor: Color, modifier: Modifier) {
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
      Text(
        value,
        color = valueColor,
        style = MaterialTheme.typography.titleLarge.copy(
          fontWeight = FontWeight.Bold,
          fontFeatureSettings = "tnum"
        )
      )
      Text(unit, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
  }
}

@Composable
private fun ZoneSection() {
  val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  CatalogSection("Zones") {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      (1..6).forEach { zone ->
        val colors = ZoneColors.forZone(zone, dark)
        Column(
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.fill),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            "Z$zone",
            modifier = Modifier.padding(vertical = 14.dp),
            color = colors.onFill,
            fontWeight = FontWeight.Bold
          )
          Box(Modifier.fillMaxWidth().height(5.dp).background(colors.line))
        }
      }
    }
  }
}

@Preview(name = "Theme catalog — Light", showBackground = true, widthDp = 430, heightDp = 900)
@Composable
private fun ThemeCatalogLightPreview() {
  TrainerLoopTheme(darkTheme = false) { ThemeCatalogScreen() }
}

@Preview(name = "Theme catalog — Dark", showBackground = true, widthDp = 430, heightDp = 900)
@Composable
private fun ThemeCatalogDarkPreview() {
  TrainerLoopTheme(darkTheme = true) { ThemeCatalogScreen() }
}
