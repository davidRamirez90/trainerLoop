package com.trainerloop.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trainerloop.data.model.CoachSuggestion

@Composable
fun CoachSuggestionCard(
  suggestion: CoachSuggestion,
  onAccept: () -> Unit,
  onReject: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.tertiaryContainer
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp)
    ) {
      Text(
        text = "Coach Suggestion",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = suggestion.message,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onTertiaryContainer
      )
      if (suggestion.rationale.isNotBlank()) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = suggestion.rationale,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
        )
      }
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Button(
          onClick = onAccept,
          modifier = Modifier.weight(1f)
        ) {
          Text("Accept")
        }
        OutlinedButton(
          onClick = onReject,
          modifier = Modifier.weight(1f)
        ) {
          Text("Reject")
        }
      }
    }
  }
}
