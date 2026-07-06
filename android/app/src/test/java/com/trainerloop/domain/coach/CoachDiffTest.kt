package com.trainerloop.domain.coach

import com.trainerloop.data.model.CoachProfile
import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.data.model.UserProfile
import com.trainerloop.data.model.WorkoutSegment
import java.io.File
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coach-diff replay test (§18): the same recorded ride through every bundled
 * profile must produce feedback streams that differ meaningfully in count
 * and content — differentiation is a tested property, not a hope.
 */
class CoachDiffTest {

  private val profile = UserProfile(ftp = 250, maxHr = 190, restingHr = 55)
  private val json = Json { ignoreUnknownKeys = true }

  private fun loadProfiles(): List<CoachProfile> =
    File("src/main/assets/coach_profiles").listFiles { f -> f.extension == "json" }!!
      .map { json.decodeFromString(CoachProfile.serializer(), it.readText()) }

  private fun step(id: String, durationSec: Int, watts: Int, phase: SegmentPhase) =
    WorkoutSegment.Step(
      id = id, durationSec = durationSec, label = null, phase = phase,
      isWork = phase == SegmentPhase.WORK, targetRange = TargetRange(watts, watts)
    )

  private fun segments() = listOf(
    step("wu", 600, 130, SegmentPhase.WARMUP),
    step("work1", 600, 245, SegmentPhase.WORK),
    step("rec1", 300, 100, SegmentPhase.RECOVERY),
    step("work2", 600, 245, SegmentPhase.WORK),
    step("rec2", 300, 100, SegmentPhase.RECOVERY),
    step("work3", 600, 245, SegmentPhase.WORK),
    step("cd", 300, 100, SegmentPhase.COOLDOWN)
  )

  private fun simulate(segs: List<WorkoutSegment>): List<TelemetrySample> {
    val plan = WorkoutInterpreter.interpret(segs, profile.ftp)
    var hr = 70.0
    val samples = mutableListOf<TelemetrySample>()
    for (t in 0 until plan.totalDurationSec) {
      val ctx = WorkoutInterpreter.contextAt(plan, t) ?: break
      val target = ctx.classified.targetMidWatts
      val power = target + 3.0 * sin(t / 7.0)
      val hrSs = 55 + 95 * (target / profile.ftp) + t * 0.004
      hr += (hrSs - hr) * (1 - exp(-1.0 / 40))
      val cadence = 92 - (t / 600)
      samples += TelemetrySample(
        timeSec = t, powerWatts = power.roundToInt(),
        cadenceRpm = cadence, hrBpm = hr.roundToInt()
      )
    }
    return samples
  }

  @Test
  fun `bundled archetypes are present`() {
    val ids = loadProfiles().map { it.id }.toSet()
    assertTrue(
      ids.containsAll(setOf("drill-sergeant", "mentor", "silent-scientist", "base-builder"))
    )
  }

  @Test
  fun `same ride through each profile yields meaningfully different feedback`() {
    val segs = segments()
    val samples = simulate(segs)
    val streams = loadProfiles()
      .filter { it.id != "default" }
      .associate { p -> p.id to ReplayHarness.replay(segs, profile, samples, coachProfile = p) }

    // Content differs: no two profiles emit identical message sequences.
    val messageSeqs = streams.mapValues { (_, items) -> items.map { it.message } }
    val distinct = messageSeqs.values.toSet()
    assertTrue(
      "expected distinct message streams per profile, got ${distinct.size} of ${messageSeqs.size}",
      distinct.size == messageSeqs.size
    )

    // Count differs: the silent scientist says less than the mentor.
    val silent = streams.getValue("silent-scientist")
    val mentor = streams.getValue("mentor")
    assertTrue(
      "silent-scientist (${silent.size}) should emit fewer items than mentor (${mentor.size})",
      silent.size < mentor.size
    )

    // The silent scientist never cheers.
    assertTrue(silent.none { it.category == FeedbackCategory.MOTIVATION })
  }
}
