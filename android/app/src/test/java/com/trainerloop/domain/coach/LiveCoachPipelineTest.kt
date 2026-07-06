package com.trainerloop.domain.coach

import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.data.model.UserProfile
import com.trainerloop.data.model.WorkoutSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic end-to-end pipeline runs over synthetic telemetry streams
 * (§13.3): the full interpreter → state → expectation → analytics →
 * arbitration path exercised as a pure offline function.
 */
class LiveCoachPipelineTest {

  private val profile = UserProfile(ftp = 250, maxHr = 190, restingHr = 55)

  private fun step(
    id: String, durationSec: Int, watts: Int,
    phase: SegmentPhase = SegmentPhase.WORK
  ) = WorkoutSegment.Step(
    id = id, durationSec = durationSec, label = null, phase = phase,
    isWork = phase == SegmentPhase.WORK, targetRange = TargetRange(watts, watts)
  )

  private fun segments() = listOf(
    step("wu", 300, 130, SegmentPhase.WARMUP),
    step("work1", 600, 250),
    step("rec1", 300, 100, SegmentPhase.RECOVERY),
    step("work2", 600, 250),
    step("cd", 300, 100, SegmentPhase.COOLDOWN)
  )

  /** Drives the coach from [fromSec] to [toSec] with a fixed sample generator. */
  private fun run(
    coach: LiveCoach,
    fromSec: Int,
    toSec: Int,
    ergEnabled: Boolean = true,
    sampleAt: (Int) -> TelemetrySample
  ): List<FeedbackItem> {
    val items = mutableListOf<FeedbackItem>()
    val plan = coach.plan
    for (t in fromSec until toSec) {
      val ctx = WorkoutInterpreter.contextAt(plan, t)
      val target = ctx?.classified?.targetMidWatts ?: 0.0
      coach.onTick(
        LiveCoach.TickInput(
          elapsedSec = t, activeSec = t, isRunning = true,
          sample = sampleAt(t), targetMidWatts = target,
          ergEnabled = ergEnabled, modificationPending = false
        )
      )?.let { items += it }
    }
    return items
  }

  @Test
  fun `erg spiral precursor fires on sustained low cadence`() {
    val coach = LiveCoach(segments(), profile)
    val items = run(coach, 0, 500) { t ->
      val inWork = t >= 300
      TelemetrySample(
        timeSec = t, powerWatts = if (inWork) 250 else 130,
        cadenceRpm = if (t >= 320) 50 else 90, hrBpm = 140
      )
    }
    assertTrue(items.any { it.ruleId == "erg-spiral" })
  }

  @Test
  fun `safety rule fires on sustained ceiling HR and beats everything`() {
    val coach = LiveCoach(segments(), profile)
    val items = run(coach, 300, 400) { t ->
      TelemetrySample(timeSec = t, powerWatts = 250, cadenceRpm = 90, hrBpm = 188)
    }
    val safety = items.firstOrNull { it.category == FeedbackCategory.SAFETY }
    assertNotNull(safety)
    assertEquals("safety-hr-ceiling", safety!!.ruleId)
  }

  @Test
  fun `quiet ride with good execution produces only sparse feedback`() {
    val coach = LiveCoach(segments(), profile)
    val items = run(coach, 0, 2100) { t ->
      val target = WorkoutInterpreter.contextAt(coach.plan, t)?.classified?.targetMidWatts ?: 100.0
      TelemetrySample(
        timeSec = t, powerWatts = target.toInt(), cadenceRpm = 90,
        hrBpm = (100 + target / 3).toInt().coerceAtMost(165)
      )
    }
    // Well-executed ride: nothing but motivation slots and at most mild info.
    assertTrue("unexpected: ${items.filter { it.severity >= 2 }}", items.none { it.severity >= 2 })
    // Session budget: 35 min ride → ≤ 14 items.
    assertTrue("too chatty: ${items.size}", items.size <= 14)
  }

  @Test
  fun `no two non-P0 items are closer than the global gap`() {
    val coach = LiveCoach(segments(), profile)
    // Messy ride: low cadence, high HR drift — plenty of rule pressure.
    val items = run(coach, 0, 2100) { t ->
      TelemetrySample(
        timeSec = t, powerWatts = 250, cadenceRpm = 55,
        hrBpm = (120 + t / 20).coerceAtMost(182)
      )
    }
    val nonP0 = items.filter { !it.category.isP0 }
    nonP0.zipWithNext().forEach { (a, b) ->
      assertTrue(
        "items ${a.ruleId}@${a.timestampSec} and ${b.ruleId}@${b.timestampSec} too close",
        b.timestampSec - a.timestampSec >= FeedbackDecisionEngine.GLOBAL_GAP_SEC
      )
    }
  }

  @Test
  fun `interval start slot announces set position`() {
    val coach = LiveCoach(segments(), profile)
    val items = run(coach, 295, 360) { t ->
      TelemetrySample(timeSec = t, powerWatts = 250, cadenceRpm = 90, hrBpm = 140)
    }
    assertTrue(items.any { it.ruleId == "slot-interval-start" })
  }

  @Test
  fun `hr feedback suppressed when hr confidence low`() {
    val coach = LiveCoach(segments(), profile)
    // HR wildly implausible (above maxHr + 5) → samples rejected → low confidence.
    val items = run(coach, 300, 600) { t ->
      TelemetrySample(timeSec = t, powerWatts = 250, cadenceRpm = 90, hrBpm = 250)
    }
    assertTrue(items.none { it.category == FeedbackCategory.SAFETY })
    assertTrue(items.none { it.ruleId == "hr-above-expected" })
  }
}
