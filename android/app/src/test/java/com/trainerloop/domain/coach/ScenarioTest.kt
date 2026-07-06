package com.trainerloop.domain.coach

import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.UserProfile
import com.trainerloop.data.model.WorkoutSegment
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scenario tests (§13.3): failure modes real rides rarely produce, driven by
 * [AthleteSimulator] through the full pipeline.
 */
class ScenarioTest {

  private val profile = UserProfile(ftp = 250, maxHr = 190, restingHr = 55)

  private fun step(id: String, durationSec: Int, watts: Int, phase: SegmentPhase) =
    WorkoutSegment.Step(
      id = id, durationSec = durationSec, label = null, phase = phase,
      isWork = phase == SegmentPhase.WORK, targetRange = TargetRange(watts, watts)
    )

  private fun vo2Set() = listOf(
    step("wu", 600, 130, SegmentPhase.WARMUP),
    step("w1", 240, 290, SegmentPhase.WORK),
    step("r1", 240, 100, SegmentPhase.RECOVERY),
    step("w2", 240, 290, SegmentPhase.WORK),
    step("r2", 240, 100, SegmentPhase.RECOVERY),
    step("w3", 240, 290, SegmentPhase.WORK),
    step("cd", 300, 100, SegmentPhase.COOLDOWN)
  )

  private fun ride(
    segs: List<WorkoutSegment>,
    sim: AthleteSimulator,
    ergEnabled: Boolean = true
  ): List<FeedbackItem> {
    val plan = WorkoutInterpreter.interpret(segs, profile.ftp)
    return ReplayHarness.replay(segs, profile, sim.ride(plan), ergEnabled)
  }

  @Test
  fun `hr strap dying mid-set triggers data-quality once and silences hr feedback`() {
    val items = ride(vo2Set(), AthleteSimulator(ftp = profile.ftp, hrStrapDiesAtSec = 900))
    assertTrue(
      "expected exactly one data-quality warning, got ${items.count { it.category == FeedbackCategory.DATA_QUALITY }}",
      items.count { it.category == FeedbackCategory.DATA_QUALITY } == 1
    )
    // No HR-derived feedback after the strap died (+ grace for confidence decay).
    val hrRules = setOf("safety-hr-ceiling", "hr-above-expected", "recovery-incomplete")
    assertFalse(items.any { it.ruleId in hrRules && it.timestampSec > 960 })
  }

  @Test
  fun `erg spiral - collapsing cadence draws a technique warning`() {
    val items = ride(vo2Set(), AthleteSimulator(ftp = profile.ftp, blowUpAtSec = 1500))
    assertTrue(
      "expected erg-spiral warning, got: ${items.map { it.ruleId }}",
      items.any { it.ruleId == "erg-spiral" && it.timestampSec >= 1500 }
    )
  }

  @Test
  fun `sandbagging a non-erg ride draws pacing-under`() {
    val items = ride(
      vo2Set(),
      AthleteSimulator(ftp = profile.ftp, powerAdherence = 0.85),
      ergEnabled = false
    )
    assertTrue(
      "expected pacing-under, got: ${items.map { it.ruleId }}",
      items.any { it.ruleId == "pacing-under" }
    )
  }

  @Test
  fun `sensor dropouts do not crash the pipeline or spam feedback`() {
    val items = ride(
      vo2Set(),
      AthleteSimulator(
        ftp = profile.ftp,
        dropouts = listOf(700..760, 1100..1130, 1600..1640)
      )
    )
    // The ≥45 s non-P0 gap property must survive dropout churn.
    val nonP0 = items.filter { !it.category.isP0 }
    assertTrue(nonP0.zipWithNext().all { (a, b) -> b.timestampSec - a.timestampSec >= 45 })
  }

  @Test
  fun `seek storm - jumping the clock invalidates windows instead of crashing`() {
    val segs = vo2Set()
    val plan = WorkoutInterpreter.interpret(segs, profile.ftp)
    val samples = AthleteSimulator(ftp = profile.ftp).ride(plan)
    val coach = LiveCoach(segs, profile)
    // Ride normally, then seek forward/backward repeatedly.
    val timePlan = (0 until 600).toList() + (1200 until 1300).toList() +
      (300 until 400).toList() + (1800 until plan.totalDurationSec).toList()
    for (t in timePlan) {
      val ctx = WorkoutInterpreter.contextAt(coach.plan, t) ?: continue
      coach.onTick(
        LiveCoach.TickInput(
          elapsedSec = t, activeSec = t, isRunning = true,
          sample = samples[t], targetMidWatts = ctx.classified.targetMidWatts,
          ergEnabled = true, modificationPending = false
        )
      )
    }
    // Reaching here without exceptions is the main assertion; sanity: log is bounded.
    assertTrue(coach.feedbackLog.value.size < 40)
  }

  @Test
  fun `emitted items carry a debug snapshot`() {
    val items = ride(vo2Set(), AthleteSimulator(ftp = profile.ftp))
    assertTrue(items.isNotEmpty())
    assertTrue(items.all { it.snapshot.containsKey("power30s") && it.snapshot.containsKey("segment") })
  }
}
