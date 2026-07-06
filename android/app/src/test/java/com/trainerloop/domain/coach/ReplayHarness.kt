package com.trainerloop.domain.coach

import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.data.model.UserProfile
import com.trainerloop.data.model.WorkoutSegment
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Replay harness (§13.2): drives the full layer-3→6 pipeline offline over a
 * recorded or synthetic sample stream and returns the emitted feedback
 * timeline. Deterministic — same inputs, same timeline.
 */
object ReplayHarness {

  fun replay(
    segments: List<WorkoutSegment>,
    profile: UserProfile,
    samples: List<TelemetrySample>,
    ergEnabled: Boolean = true,
    coachProfile: com.trainerloop.data.model.CoachProfile? = null
  ): List<FeedbackItem> {
    val coach = LiveCoach(segments, profile, coachProfile)
    val items = mutableListOf<FeedbackItem>()
    for (sample in samples) {
      val t = sample.timeSec
      val target = WorkoutInterpreter.contextAt(coach.plan, t)?.classified?.targetMidWatts ?: 0.0
      coach.onTick(
        LiveCoach.TickInput(
          elapsedSec = t, activeSec = t, isRunning = true,
          sample = sample, targetMidWatts = target,
          ergEnabled = ergEnabled, modificationPending = false
        )
      )?.let { items += it }
    }
    return items
  }

  /** Replays a persisted Room session: samplesJson deserializes straight in. */
  fun replayFromSamplesJson(
    segments: List<WorkoutSegment>,
    profile: UserProfile,
    samplesJson: String,
    ergEnabled: Boolean = true
  ): List<FeedbackItem> = replay(
    segments, profile,
    Json.decodeFromString(ListSerializer(TelemetrySample.serializer()), samplesJson),
    ergEnabled
  )

  /** Replays a recorded FIT activity file (§13.2 — the docs/rides fixtures). */
  fun replayFromFit(
    segments: List<WorkoutSegment>,
    profile: UserProfile,
    fitBytes: ByteArray,
    ergEnabled: Boolean = true,
    coachProfile: com.trainerloop.data.model.CoachProfile? = null
  ): List<FeedbackItem> = replay(
    segments, profile,
    com.trainerloop.domain.fit.FitDecoder.decode(fitBytes).samples,
    ergEnabled, coachProfile
  )

  /** One line per emitted item — the golden-file format. */
  fun timeline(items: List<FeedbackItem>): String =
    items.joinToString("\n") { "t=${it.timestampSec} ${it.category} sev=${it.severity} ${it.ruleId}" }
}
