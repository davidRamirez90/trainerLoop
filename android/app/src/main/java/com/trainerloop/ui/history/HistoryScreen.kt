package com.trainerloop.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainerloop.app.trainerLoopApp
import com.trainerloop.data.model.SessionSummary
import com.trainerloop.ui.components.pressable
import com.trainerloop.ui.theme.ZoneColors
import com.trainerloop.ui.theme.NumericSmall
import com.trainerloop.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    item { WeekStripes(sessions) }
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
        horizontalArrangement = Arrangement.SpaceBetween,
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
        Text(
          text = formatSessionDate(session.startedAt),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      Spacer(modifier = Modifier.height(Spacing.md))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xl)
      ) {
        SessionStat(value = formatDuration(session.durationSec), label = "Time")
        if (session.avgPower > 0) {
          SessionStat(value = "${session.avgPower} W", label = "Avg Power")
        }
        if (session.avgHr > 0) {
          SessionStat(value = "${session.avgHr} bpm", label = "Avg HR")
        }
      }
    }
  }
}

private const val STRIPE_DAYS = 42

/** Horizontal strip of the last [STRIPE_DAYS] days; each ride day is a vertical
 *  bar sized by duration and colored by its average-power zone. */
@Composable
private fun WeekStripes(sessions: List<SessionSummary>) {
  val context = LocalContext.current
  val resolvedFtp = remember { context.trainerLoopApp.profileRepository.getProfileSync().ftp }
  val darkTheme = isSystemInDarkTheme()

  // Aggregate sessions per local day.
  data class DayLoad(val durationSec: Int, val avgPower: Int)
  val byDay = remember(sessions) {
    val map = HashMap<LocalDate, DayLoad>()
    sessions.forEach { s ->
      val day = try {
        Instant.parse(s.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
      } catch (_: Exception) { return@forEach }
      val prev = map[day]
      map[day] = if (prev == null) DayLoad(s.durationSec, s.avgPower)
      else DayLoad(prev.durationSec + s.durationSec, maxOf(prev.avgPower, s.avgPower))
    }
    map
  }
  val today = LocalDate.now()
  val days = remember(byDay) { (STRIPE_DAYS - 1 downTo 0).map { today.minusDays(it.toLong()) } }
  val maxDuration = remember(byDay) { byDay.values.maxOfOrNull { it.durationSec } ?: 1 }

  Column {
    Text(
      text = "Last 6 weeks",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(6.dp))
    val scrollState = rememberScrollState()
    // Open scrolled to the most recent day (today), not the 6-weeks-ago start.
    LaunchedEffect(scrollState.maxValue) { scrollState.scrollTo(scrollState.maxValue) }
    Row(modifier = Modifier.horizontalScroll(scrollState)) {
      days.forEach { day ->
        val load = byDay[day]
        val frac = if (load != null) (load.durationSec.toFloat() / maxDuration).coerceIn(0.15f, 1f) else 0f
        val barColor = if (load != null) ZoneColors.forTarget(load.avgPower, resolvedFtp, darkTheme).line
        else Color.Transparent
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.width(14.dp)
        ) {
          Box(
            modifier = Modifier
              .width(8.dp)
              .height(48.dp)
              .clip(RoundedCornerShape(4.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.BottomCenter
          ) {
            if (load != null) {
              Box(
                modifier = Modifier
                  .width(8.dp)
                  .height((48 * frac).dp)
                  .clip(RoundedCornerShape(4.dp))
                  .background(barColor)
              )
            }
          }
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = "MTWTFSS"[day.dayOfWeek.value - 1].toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}

@Composable
private fun SessionStat(value: String, label: String) {
  Column {
    Text(
      text = value,
      style = NumericSmall.copy(fontWeight = FontWeight.Bold)
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

private fun formatSessionDate(startedAt: String): String {
  return try {
    val instant = Instant.parse(startedAt)
    DateTimeFormatter.ofPattern("MMM d, yyyy")
      .withZone(ZoneId.systemDefault())
      .format(instant)
  } catch (_: Exception) {
    startedAt
  }
}

private fun formatDuration(seconds: Int): String {
  val hours = seconds / 3600
  val minutes = (seconds % 3600) / 60
  val secs = seconds % 60
  return if (hours > 0) {
    "$hours:${minutes.pad2()}:${secs.pad2()}"
  } else {
    "$minutes:${secs.pad2()}"
  }
}

private fun Int.pad2(): String = if (this < 10) "0$this" else "$this"
