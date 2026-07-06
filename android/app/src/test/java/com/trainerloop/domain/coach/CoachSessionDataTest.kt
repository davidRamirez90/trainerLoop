package com.trainerloop.domain.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoachSessionDataTest {

  @Test
  fun `round-trips through json`() {
    val data = CoachSessionData(
      feedback = listOf(
        FeedbackItem("f1", 120, FeedbackCategory.PACING, 1, "Settle in", "pacing_under")
      ),
      intervals = listOf(
        IntervalRecord(
          setId = "set-1", blockNumber = 2, segmentClass = SegmentClass.VO2MAX,
          targetMidWatts = 300.0, avgPower = 296.0, adherencePct = 98.7,
          timeInTargetPct = 91.0, powerCv = 3.2, avgHr = 168.0, endHr = 176.0,
          hrDriftPct = 4.1, avgCadence = 92.0
        )
      ),
      recoveries = listOf(RecoveryRecord(startHr = 175.0, hrr60 = 22.0, endHr = 120.0)),
      fatigueCurve = listOf(10.0, 24.5, 41.0),
      finalFatigueScore = 41.0
    )
    assertEquals(data, CoachSessionData.fromJson(data.toJson()))
  }

  @Test
  fun `blank and garbage json decode to null`() {
    assertNull(CoachSessionData.fromJson(""))
    assertNull(CoachSessionData.fromJson("not json"))
  }
}
