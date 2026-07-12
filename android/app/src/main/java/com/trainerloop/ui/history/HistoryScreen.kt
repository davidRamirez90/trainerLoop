package com.trainerloop.ui.history

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.trainerloop.ui.components.EmptyState
import com.trainerloop.ui.components.SectionHeader
import com.trainerloop.ui.components.TrainerLoopCard
import com.trainerloop.ui.components.TrainerLoopTopBar
import com.trainerloop.ui.theme.MotionSpec
import com.trainerloop.ui.theme.NumericSmall
import com.trainerloop.ui.theme.Spacing
import com.trainerloop.ui.theme.reducedMotionAware
import com.trainerloop.ui.theme.trainerLoopColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val TrailingStatusWidth = 116.dp

@Composable
fun HistoryScreen(
  onSessionClick: (String) -> Unit,
  viewModel: HistoryViewModel = viewModel()
) {
  val sessions by viewModel.sessions.collectAsStateWithLifecycle()
  val defaultMotionSpec = reducedMotionAware(MotionSpec.default)

  Scaffold(
    contentWindowInsets = WindowInsets(0),
    topBar = { TrainerLoopTopBar(title = "History", windowInsets = WindowInsets(0)) }
  ) { padding ->
    AnimatedContent(
      targetState = sessions.isEmpty(),
      modifier = Modifier.padding(padding),
      transitionSpec = {
        fadeIn(animationSpec = defaultMotionSpec) togetherWith
          fadeOut(animationSpec = defaultMotionSpec)
      },
      label = "history-empty-state"
    ) { empty ->
      if (empty) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screenMargin),
          contentAlignment = Alignment.Center
        ) {
          EmptyState(
            icon = Icons.Default.History,
            title = "No rides yet",
            body = "Finished workouts will show up here."
          )
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screenMargin),
          verticalArrangement = Arrangement.spacedBy(Spacing.sectionGap)
        ) {
          item { SectionHeader(title = "Last 6 weeks") }
          item { WeeklyLoadChart(sessions) }
          items(sessions, key = { it.id }) { session ->
            SessionCard(session, onClick = { onSessionClick(session.id) })
          }
        }
      }
    }
  }
}

@Composable
private fun SessionCard(session: SessionSummary, onClick: () -> Unit) {
  val semantic = MaterialTheme.trainerLoopColors
  val uploaded = session.icuSyncedAt != null

  TrainerLoopCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = session.workoutName,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
          text = formatSessionMeta(session),
          style = NumericSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      Row(
        modifier = Modifier.width(TrailingStatusWidth),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (uploaded) {
          Icon(
            imageVector = Icons.Filled.CloudDone,
            contentDescription = null,
            tint = semantic.connected,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(Spacing.xs))
          Text(
            text = "Uploaded",
            style = MaterialTheme.typography.labelSmall,
            color = semantic.connected,
            maxLines = 1
          )
        } else {
          Icon(
            imageVector = Icons.Filled.CloudQueue,
            contentDescription = null,
            tint = semantic.stale,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(Spacing.xs))
          Text(
            text = "Not uploaded",
            style = MaterialTheme.typography.labelSmall,
            color = semantic.stale,
            maxLines = 1
          )
        }
        Spacer(modifier = Modifier.width(Spacing.xs))
        Icon(
          imageVector = Icons.Filled.ChevronRight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(18.dp)
        )
      }
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

  TrainerLoopCard {
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
