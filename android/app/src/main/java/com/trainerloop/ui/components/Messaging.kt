package com.trainerloop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trainerloop.ui.theme.Spacing
import com.trainerloop.ui.theme.trainerLoopColors

enum class MessageSeverity { Info, Warning, Error }

@Composable
fun EmptyState(
  icon: ImageVector,
  title: String,
  body: String,
  modifier: Modifier = Modifier,
  action: (@Composable ColumnScope.() -> Unit)? = null
) {
  Column(
    modifier = modifier.fillMaxWidth().padding(Spacing.cardPadding),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(Spacing.controlGap)
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      modifier = Modifier.size(40.dp),
      tint = MaterialTheme.colorScheme.primary
    )
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text(
      body,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    action?.invoke(this)
  }
}

@Composable
fun InlineMessage(
  severity: MessageSeverity,
  text: String,
  modifier: Modifier = Modifier
) {
  val semantic = MaterialTheme.trainerLoopColors
  val (containerColor, contentColor) = when (severity) {
    MessageSeverity.Info -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    MessageSeverity.Warning -> semantic.warning to semantic.onWarning
    MessageSeverity.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
  }
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(containerColor)
      .padding(Spacing.cardPadding),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = contentColor)
  }
}
