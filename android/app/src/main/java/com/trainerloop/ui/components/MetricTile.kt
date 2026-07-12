package com.trainerloop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.trainerloop.ui.theme.Spacing
import com.trainerloop.ui.theme.trainerLoopColors

enum class MetricTileState { Available, Stale, Unavailable }

@Composable
fun MetricTile(
  label: String,
  value: String,
  unit: String,
  modifier: Modifier = Modifier,
  state: MetricTileState = MetricTileState.Available
) {
  val semantic = MaterialTheme.trainerLoopColors
  val displayedValue = if (state == MetricTileState.Unavailable) "—" else value
  val valueColor = when (state) {
    MetricTileState.Available -> MaterialTheme.colorScheme.onSurface
    MetricTileState.Stale, MetricTileState.Unavailable -> semantic.stale
  }
  val stateModifier = when (state) {
    MetricTileState.Available -> Modifier
    MetricTileState.Stale -> Modifier.semantics { stateDescription = "Stale" }
    MetricTileState.Unavailable -> Modifier.semantics { stateDescription = "Unavailable" }
  }

  TrainerLoopCard(modifier = modifier.then(stateModifier), emphasized = true) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
      Text(
        text = label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall
      )
      Text(
        text = displayedValue,
        color = valueColor,
        style = MaterialTheme.typography.titleLarge.copy(
          fontWeight = FontWeight.Bold,
          fontFeatureSettings = "tnum"
        )
      )
      Text(
        text = unit,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall
      )
    }
  }
}
