package com.trainerloop.ui.coach

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import com.trainerloop.data.model.CoachSuggestion
import com.trainerloop.domain.coach.FeedbackItem
import com.trainerloop.ui.theme.MotionSpec
import com.trainerloop.ui.theme.Spacing
import com.trainerloop.ui.theme.reducedMotionAware

@Composable
fun CoachFeedbackArea(
  pendingSuggestion: CoachSuggestion?,
  liveFeedback: FeedbackItem?,
  onAcceptSuggestion: (String) -> Unit,
  onRejectSuggestion: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  var displayedLiveFeedback by remember { mutableStateOf<FeedbackItem?>(null) }
  LaunchedEffect(liveFeedback) {
    liveFeedback?.let { displayedLiveFeedback = it }
  }

  Column(modifier = modifier) {
    pendingSuggestion?.let { suggestion ->
      CoachSuggestionCard(
        suggestion = suggestion,
        onAccept = { onAcceptSuggestion(suggestion.id) },
        onReject = { onRejectSuggestion(suggestion.id) }
      )
      if (liveFeedback != null) {
        Spacer(modifier = Modifier.height(Spacing.sm))
      }
    }

    AnimatedVisibility(
      visible = liveFeedback != null,
      enter = expandVertically(
        animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
      ) + fadeIn(animationSpec = reducedMotionAware(MotionSpec.default)),
      exit = shrinkVertically(
        animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
      ) + fadeOut(animationSpec = reducedMotionAware(MotionSpec.default))
    ) {
      displayedLiveFeedback?.let { feedback ->
        Column {
          LiveFeedbackCard(item = feedback)
          Spacer(modifier = Modifier.height(Spacing.sm))
        }
      }
    }
  }
}
