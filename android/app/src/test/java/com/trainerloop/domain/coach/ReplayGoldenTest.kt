package com.trainerloop.domain.coach

import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.data.model.UserProfile
import com.trainerloop.data.model.WorkoutSegment
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Golden-file replay test (§13.2): a deterministic simulated 3×10 min
 * threshold ride through the full pipeline; the emitted feedback timeline
 * must match the checked-in golden file exactly. On intentional rule/tuning
 * changes, regenerate with:
 *   REGENERATE_GOLDEN=1 ./gradlew :app:testDebugUnitTest --tests '*ReplayGoldenTest*'
 */
class ReplayGoldenTest {

  private val profile = UserProfile(ftp = 250, maxHr = 190, restingHr = 55)

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

  /**
   * Simulated athlete: first-order HR response toward a per-power steady
   * state with per-interval drift, mild deterministic power wobble around
   * the ERG target, cadence sagging late in the ride.
   */
  private fun simulate(segs: List<WorkoutSegment>): List<TelemetrySample> {
    val plan = WorkoutInterpreter.interpret(segs, profile.ftp)
    var hr = 70.0
    val samples = mutableListOf<TelemetrySample>()
    for (t in 0 until plan.totalDurationSec) {
      val ctx = WorkoutInterpreter.contextAt(plan, t) ?: break
      val target = ctx.classified.targetMidWatts
      val power = target + 3.0 * sin(t / 7.0)
      // steady-state HR rises with %FTP; drifts up over the ride
      val hrSs = 55 + 95 * (target / profile.ftp) + t * 0.004
      hr += (hrSs - hr) * (1 - exp(-1.0 / 40))
      val cadence = 92 - (t / 600) // slow fade, ~5 rpm over the ride
      samples += TelemetrySample(
        timeSec = t,
        powerWatts = power.roundToInt(),
        cadenceRpm = cadence,
        hrBpm = hr.roundToInt()
      )
    }
    return samples
  }

  @Test
  fun `threshold ride timeline matches golden file`() {
    val timeline = ReplayHarness.timeline(
      ReplayHarness.replay(segments(), profile, simulate(segments()))
    )
    val goldenPath = "src/test/resources/replay/threshold-3x10.golden"
    val golden = java.io.File(goldenPath)
    if (System.getenv("REGENERATE_GOLDEN") == "1") {
      golden.parentFile.mkdirs()
      golden.writeText(timeline + "\n")
      return
    }
    assertEquals(golden.readText().trim(), timeline.trim())
  }
}
