package com.trainerloop.ui.coach

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trainerloop.domain.coach.FeedbackCategory
import com.trainerloop.domain.coach.FeedbackItem

/** Live Feedback Coach Mode card: the single currently-visible feedback item. */
@Composable
fun LiveFeedbackCard(item: FeedbackItem, modifier: Modifier = Modifier) {
  val container = when (item.category) {
    FeedbackCategory.SAFETY -> MaterialTheme.colorScheme.errorContainer
    FeedbackCategory.DATA_QUALITY, FeedbackCategory.FATIGUE_MANAGEMENT ->
      MaterialTheme.colorScheme.tertiaryContainer
    FeedbackCategory.MOTIVATION -> MaterialTheme.colorScheme.secondaryContainer
    else -> MaterialTheme.colorScheme.primaryContainer
  }
  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = container)
  ) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
      Text(
        text = categoryLabel(item.category),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Text(
        text = item.message,
        style = MaterialTheme.typography.bodyMedium
      )
    }
  }
}

private fun categoryLabel(category: FeedbackCategory): String = when (category) {
  FeedbackCategory.SAFETY -> "SAFETY"
  FeedbackCategory.DATA_QUALITY -> "SENSOR"
  FeedbackCategory.WORKOUT_MODIFICATION -> "COACH"
  FeedbackCategory.FATIGUE_MANAGEMENT -> "FATIGUE"
  FeedbackCategory.PACING -> "PACING"
  FeedbackCategory.RECOVERY -> "RECOVERY"
  FeedbackCategory.TECHNIQUE -> "TECHNIQUE"
  FeedbackCategory.MOTIVATION -> "COACH"
}
