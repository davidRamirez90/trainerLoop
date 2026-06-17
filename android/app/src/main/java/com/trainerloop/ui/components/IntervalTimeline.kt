package com.trainerloop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.WorkoutSegment

@Composable
fun IntervalTimeline(
  segments: List<WorkoutSegment>,
  currentIndex: Int,
  modifier: Modifier = Modifier
) {
  Column(modifier = modifier.fillMaxWidth()) {
    Text(
      text = "Segments",
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(bottom = 4.dp)
    )
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      itemsIndexed(segments.take(24)) { index, segment ->
        val isCurrent = index == currentIndex
        val isPast = index < currentIndex
        TimelineSegment(
          segment = segment,
          isCurrent = isCurrent,
          isPast = isPast
        )
      }
    }
  }
}

@Composable
private fun TimelineSegment(
  segment: WorkoutSegment,
  isCurrent: Boolean,
  isPast: Boolean
) {
  val bgColor = when {
    isCurrent -> segment.color().copy(alpha = 0.8f)
    isPast -> segment.color().copy(alpha = 0.3f)
    else -> segment.color().copy(alpha = 0.6f)
  }

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.width(36.dp)
  ) {
    Box(
      modifier = Modifier
        .width(32.dp)
        .height(if (isCurrent) 48.dp else 40.dp)
        .clip(RoundedCornerShape(4.dp))
        .background(bgColor)
    )
    val label = when (segment.phase) {
      SegmentPhase.WARMUP -> "W"
      SegmentPhase.WORK -> "W"
      SegmentPhase.RECOVERY -> "R"
      SegmentPhase.COOLDOWN -> "C"
    }
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

private fun WorkoutSegment.color(): Color = when (phase) {
  SegmentPhase.WARMUP -> Color(0xFF8BC34A)
  SegmentPhase.WORK -> Color(0xFF4CAF78)
  SegmentPhase.RECOVERY -> Color(0xFFFFB300)
  SegmentPhase.COOLDOWN -> Color(0xFF5C8EED)
}
