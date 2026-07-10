package com.trainerloop.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trainerloop.data.model.SessionSummary
import com.trainerloop.data.repository.SessionRepository
import com.trainerloop.data.source.local.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields

data class WeekLoad(
  val weekLabel: String,
  val totalSec: Int
)

/** Returns the [weeks] ISO weeks ending with the week containing [today]. */
fun weeklyLoads(
  sessions: List<SessionSummary>,
  today: LocalDate,
  weeks: Int = 6
): List<WeekLoad> {
  if (weeks <= 0) return emptyList()

  val currentWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
  val weekStarts = (weeks - 1 downTo 0).map { offset ->
    currentWeekStart.minusWeeks(offset.toLong())
  }
  val totals = weekStarts.associateWith { 0 }.toMutableMap()
  val localZone = ZoneId.systemDefault()

  sessions.forEach { session ->
    val sessionDate = runCatching {
      Instant.parse(session.startedAt).atZone(localZone).toLocalDate()
    }.getOrNull() ?: return@forEach
    val sessionWeekStart = sessionDate.with(
      TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
    )
    if (sessionWeekStart in totals) {
      totals[sessionWeekStart] =
        (totals[sessionWeekStart] ?: 0) + session.durationSec.coerceAtLeast(0)
    }
  }

  val isoWeeks = WeekFields.ISO
  return weekStarts.map { weekStart ->
    WeekLoad(
      weekLabel = "W${weekStart.get(isoWeeks.weekOfWeekBasedYear())}",
      totalSec = totals.getValue(weekStart)
    )
  }
}

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

  private val sessionRepository: SessionRepository =
    SessionRepository.create(AppDatabase.getInstance(application))

  private val _sessions = MutableStateFlow<List<SessionSummary>>(emptyList())
  val sessions: StateFlow<List<SessionSummary>> = _sessions.asStateFlow()

  init {
    viewModelScope.launch {
      sessionRepository.summaries().collect { _sessions.value = it }
    }
  }
}
