package com.trainerloop.ui.history

import com.trainerloop.data.model.SessionSummary
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryViewModelTest {

  @Test
  fun `weekly loads aggregate sessions across week boundaries and retain empty weeks`() {
    val loads = weeklyLoads(
      sessions = listOf(
        summary(id = "week-26", startedAt = "2026-06-28T12:00:00Z", durationSec = 600),
        summary(id = "week-27-a", startedAt = "2026-06-29T12:00:00Z", durationSec = 1200),
        summary(id = "week-27-b", startedAt = "2026-07-05T12:00:00Z", durationSec = 1800),
        summary(id = "current-week", startedAt = "2026-07-06T12:00:00Z", durationSec = 2400)
      ),
      today = LocalDate.of(2026, 7, 9)
    )

    assertEquals(
      listOf(
        WeekLoad("W23", 0),
        WeekLoad("W24", 0),
        WeekLoad("W25", 0),
        WeekLoad("W26", 600),
        WeekLoad("W27", 3000),
        WeekLoad("W28", 2400)
      ),
      loads
    )
  }

  private fun summary(id: String, startedAt: String, durationSec: Int) = SessionSummary(
    id = id,
    workoutId = "workout-$id",
    workoutName = "Test ride",
    startedAt = startedAt,
    endedAt = null,
    durationSec = durationSec,
    completed = true,
    avgPower = 200,
    maxPower = 250,
    avgCadence = 90,
    avgHr = 140
  )
}
