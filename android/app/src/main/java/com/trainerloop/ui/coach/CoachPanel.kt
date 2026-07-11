package com.trainerloop.ui.coach

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trainerloop.data.model.CoachEvent
import com.trainerloop.data.model.CoachSuggestion
import com.trainerloop.ui.theme.MotionSpec
import com.trainerloop.ui.theme.reducedMotionAware
import androidx.compose.ui.unit.IntSize

@Composable
fun CoachPanel(
  pendingSuggestion: CoachSuggestion?,
  events: List<CoachEvent>,
  onAccept: () -> Unit,
  onReject: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(modifier = modifier) {
    Text(
      text = "Coach",
      style = MaterialTheme.typography.titleLarge
    )

    Spacer(modifier = Modifier.height(8.dp))

    AnimatedVisibility(
      visible = pendingSuggestion != null,
      enter = expandVertically(
        animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
      ) + fadeIn(animationSpec = reducedMotionAware(MotionSpec.default)),
      exit = shrinkVertically(
        animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
      ) + fadeOut(animationSpec = reducedMotionAware(MotionSpec.default))
    ) {
      pendingSuggestion?.let { suggestion ->
        CoachSuggestionCard(
          suggestion = suggestion,
          onAccept = onAccept,
          onReject = onReject
        )
        Spacer(modifier = Modifier.height(8.dp))
      }
    }

    if (events.isNotEmpty()) {
      Text(
        text = "Event Log",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(modifier = Modifier.height(4.dp))

      LazyColumn(
        modifier = Modifier.fillMaxWidth()
      ) {
        items(events.takeLast(20)) { event ->
          EventRow(event)
          HorizontalDivider()
        }
      }
    }
  }
}

@Composable
private fun EventRow(event: CoachEvent) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp)
  ) {
    Text(
      text = event.message,
      style = MaterialTheme.typography.bodySmall,
      color = when (event.type) {
        com.trainerloop.data.model.CoachEventType.SUGGESTION ->
          MaterialTheme.colorScheme.primary
        com.trainerloop.data.model.CoachEventType.COMPLETION ->
          MaterialTheme.colorScheme.secondary
        com.trainerloop.data.model.CoachEventType.ENCOURAGEMENT ->
          MaterialTheme.colorScheme.tertiary
      }
    )
    Text(
      text = "t=${event.timestamp}s",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}
