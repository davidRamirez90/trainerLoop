package com.trainerloop.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trainerloop.ui.theme.Spacing

@Composable
fun TrainerLoopCard(
  modifier: Modifier = Modifier,
  emphasized: Boolean = false,
  onClick: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit
) {
  val colors = CardDefaults.cardColors(
    containerColor = if (emphasized) {
      MaterialTheme.colorScheme.surfaceVariant
    } else {
      MaterialTheme.colorScheme.surface
    }
  )
  val shape = RoundedCornerShape(16.dp)

  if (onClick == null) {
    Card(modifier = modifier, shape = shape, colors = colors) {
      Column(modifier = Modifier.padding(Spacing.cardPadding), content = content)
    }
  } else {
    val interactionSource = remember { MutableInteractionSource() }
    Card(
      onClick = onClick,
      modifier = modifier.pressable(interactionSource),
      shape = shape,
      colors = colors,
      interactionSource = interactionSource
    ) {
      Column(modifier = Modifier.padding(Spacing.cardPadding), content = content)
    }
  }
}
