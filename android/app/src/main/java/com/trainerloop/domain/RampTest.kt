package com.trainerloop.domain

import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.data.model.Workout
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.data.model.WorkoutSource
import kotlin.math.roundToInt

/**
 * Built-in FTP ramp test: 5-min warmup ramp 50→100 W, then 1-min steps from
 * 100 W in +20 W increments. Steps are pre-generated up to an unreachable
 * ceiling; the test always ends via failure detection or the stop button.
 * New FTP = 75% of the best rolling 60-second average power.
 */
object RampTest {

  const val WORKOUT_ID = "ftp-ramp-test"

  const val WARMUP_SEC = 300
  const val STEP_SEC = 60
  const val START_POWER = 100
  const val STEP_POWER = 20

  fun isRampTest(workoutId: String): Boolean = workoutId == WORKOUT_ID

  fun generate(currentFtp: Int): Workout {
    val ceiling = maxOf((2.5 * currentFtp).roundToInt(), currentFtp + 200)
    val segments = mutableListOf<WorkoutSegment>(
      WorkoutSegment.Ramp(
        id = "warmup",
        durationSec = WARMUP_SEC,
        label = "Warm Up",
        phase = SegmentPhase.WARMUP,
        isWork = false,
        startPower = 50,
        endPower = 100
      )
    )
    var power = START_POWER
    var i = 1
    while (power <= ceiling) {
      segments += WorkoutSegment.Step(
        id = "step-$i",
        durationSec = STEP_SEC,
        label = "$power W",
        phase = SegmentPhase.WORK,
        isWork = true,
        targetRange = TargetRange(power, power)
      )
      power += STEP_POWER
      i++
    }
    return Workout(
      id = WORKOUT_ID,
      name = "FTP Ramp Test",
      description = "Ramp to exhaustion — new FTP is 75% of your best 1-minute power",
      source = WorkoutSource.MANUAL,
      segments = segments
    )
  }

  /**
   * Best rolling 60-second average power × 0.75, rounded.
   * Null when fewer than 60 seconds of samples exist.
   */
  fun computeFtp(samples: List<TelemetrySample>): Int? {
    if (samples.size < STEP_SEC) return null
    var windowSum = samples.take(STEP_SEC).sumOf { it.powerWatts.toLong() }
    var best = windowSum
    for (i in STEP_SEC until samples.size) {
      windowSum += samples[i].powerWatts - samples[i - STEP_SEC].powerWatts
      if (windowSum > best) best = windowSum
    }
    return (best.toDouble() / STEP_SEC * 0.75).roundToInt()
  }
}
