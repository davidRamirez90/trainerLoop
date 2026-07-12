package com.trainerloop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.trainerloop.ui.theme.Spacing
import com.trainerloop.ui.theme.trainerLoopColors

enum class StatusPillState { Connected, Scanning, Warning, Reconnecting, Unavailable, Success }

@Composable
fun StatusPill(
  state: StatusPillState,
  label: String,
  icon: ImageVector,
  modifier: Modifier = Modifier
) {
  val semantic = MaterialTheme.trainerLoopColors
  val (containerColor, contentColor) = when (state) {
    StatusPillState.Connected, StatusPillState.Success -> semantic.connected to semantic.onConnected
    StatusPillState.Scanning -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    StatusPillState.Warning, StatusPillState.Reconnecting -> semantic.warning to semantic.onWarning
    StatusPillState.Unavailable -> semantic.stale to semantic.onStale
  }

  Row(
    modifier = modifier
      .clip(CircleShape)
      .background(containerColor)
      .padding(horizontal = 10.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(Spacing.controlGap),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = contentColor)
    Text(label, color = contentColor, style = MaterialTheme.typography.labelMedium)
  }
}
