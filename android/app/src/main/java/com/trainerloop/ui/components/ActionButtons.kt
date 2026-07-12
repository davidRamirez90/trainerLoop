package com.trainerloop.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class SecondaryActionStyle { Tonal, Outlined }

@Composable
fun PrimaryActionButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  content: @Composable RowScope.() -> Unit
) {
  val interactionSource = remember { MutableInteractionSource() }
  Button(
    onClick = onClick,
    modifier = modifier.heightIn(min = 48.dp).pressable(interactionSource),
    enabled = enabled,
    interactionSource = interactionSource,
    colors = ButtonDefaults.buttonColors(
      containerColor = MaterialTheme.colorScheme.primary,
      contentColor = MaterialTheme.colorScheme.onPrimary
    ),
    content = content
  )
}

@Composable
fun SecondaryActionButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  style: SecondaryActionStyle = SecondaryActionStyle.Tonal,
  content: @Composable RowScope.() -> Unit
) {
  val interactionSource = remember { MutableInteractionSource() }
  val buttonModifier = modifier.heightIn(min = 48.dp).pressable(interactionSource)
  when (style) {
    SecondaryActionStyle.Tonal -> FilledTonalButton(
      onClick = onClick,
      modifier = buttonModifier,
      enabled = enabled,
      interactionSource = interactionSource,
      content = content
    )
    SecondaryActionStyle.Outlined -> OutlinedButton(
      onClick = onClick,
      modifier = buttonModifier,
      enabled = enabled,
      interactionSource = interactionSource,
      content = content
    )
  }
}
