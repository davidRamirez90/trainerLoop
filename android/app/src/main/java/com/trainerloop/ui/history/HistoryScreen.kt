package com.trainerloop.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainerloop.data.model.SessionSummary
import com.trainerloop.ui.components.pressable
import com.trainerloop.ui.theme.MotionSpec
import com.trainerloop.ui.theme.NumericSmall
import com.trainerloop.ui.theme.Spacing
import com.trainerloop.ui.theme.reducedMotionAware
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun HistoryScreen(
  onSessionClick: (String) -> Unit,
  viewModel: HistoryViewModel = viewModel()
) {
  val sessions by viewModel.sessions.collectAsStateWithLifecycle()

  if (sessions.isEmpty()) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = Spacing.lg),
      contentAlignment = Alignment.Center
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
          imageVector = Icons.Default.History,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = "No rides yet",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Finished workouts will show up here.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
    return
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = Spacing.lg),
    verticalArrangement = Arrangement.spacedBy(Spacing.xl)
  ) {
    item {
      Text(
        text = "History",
        style = MaterialTheme.typography.headlineLarge
      )
    }
    item { WeeklyLoadChart(sessions) }
    items(sessions, key = { it.id }) { session ->
      SessionCard(session, onClick = { onSessionClick(session.id) })
    }
  }
}

@Composable
private fun SessionCard(session: SessionSummary, onClick: () -> Unit) {
  val interactionSource = remember { MutableInteractionSource() }

  Card(
    onClick = onClick,
    modifier = Modifier
      .fillMaxWidth()
      .pressable(interactionSource),
    interactionSource = interactionSource,
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
  ) {
    Column(modifier = Modifier.padding(Spacing.lg)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = session.workoutName,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier
            .weight(1f)
            .padding(end = 8.dp),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        if (session.icuSyncedAt != null) {
          Icon(
            imageVector = Icons.Filled.CloudDone,
            contentDescription = "Synced to intervals.icu",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
        }
      }
      Spacer(modifier = Modifier.height(Spacing.xs))
      Text(
        text = formatSessionMeta(session),
        style = NumericSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
private fun WeeklyLoadChart(sessions: List<SessionSummary>) {
  val today = remember { LocalDate.now() }
  val loads = remember(sessions, today) { weeklyLoads(sessions, today) }
  val maxSeconds = loads.maxOfOrNull { it.totalSec } ?: 0
  var animateBars by remember { mutableStateOf(false) }

  LaunchedEffect(loads) {
    if (maxSeconds > 0) animateBars = true
  }

  Column {
    Text(
      text = "Last 6 weeks",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(Spacing.sm))
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(Spacing.xxl * 4)
        .clearAndSetSemantics { contentDescription = weeklyLoadSummary(loads) },
      horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
      verticalAlignment = Alignment.Bottom
    ) {
      loads.forEachIndexed { index, load ->
        val fraction = if (maxSeconds == 0) 0f else load.totalSec.toFloat() / maxSeconds
        val animatedFraction by animateFloatAsState(
          targetValue = if (animateBars) fraction else 0f,
          animationSpec = reducedMotionAware(MotionSpec.defaultSpring<Float>()),
          label = "Weekly load bar ${load.weekLabel}"
        )
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Bottom,
          modifier = Modifier.weight(1f)
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(Spacing.xxl * 3)
              .clip(RoundedCornerShape(topStart = Spacing.sm, topEnd = Spacing.sm)),
            contentAlignment = Alignment.BottomCenter
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(animatedFraction)
                .clip(RoundedCornerShape(topStart = Spacing.sm, topEnd = Spacing.sm))
                .background(
                  if (index == loads.lastIndex) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
          }
          Spacer(modifier = Modifier.height(Spacing.xs))
          Text(
            text = load.weekLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}

private fun weeklyLoadSummary(loads: List<WeekLoad>): String {
  val values = loads.joinToString(", ") { load ->
    val hours = load.totalSec / 3600.0
    val formattedHours = if (hours % 1.0 == 0.0) {
      "${hours.toInt()} hours"
    } else {
      String.format(Locale.US, "%.1f hours", hours)
    }
    "${load.weekLabel} $formattedHours"
  }
  return "Weekly riding time: $values."
}

private fun formatSessionMeta(session: SessionSummary): String {
  val parts = mutableListOf(formatSessionDate(session.startedAt), "${session.durationSec / 60} min")
  if (session.avgPower > 0) parts += "${session.avgPower} W"
  return parts.joinToString(" · ")
}

private fun formatSessionDate(startedAt: String): String {
  return try {
    val instant = Instant.parse(startedAt)
    val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    val daysAgo = ChronoUnit.DAYS.between(date, LocalDate.now())
    if (daysAgo in 0..6) {
      DateTimeFormatter.ofPattern("EEE", Locale.getDefault()).format(date)
    } else {
      DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).format(date)
    }
  } catch (_: Exception) {
    startedAt
  }
}
