package com.trainerloop.domain.coach

import com.trainerloop.data.model.UserProfile
import com.trainerloop.domain.coach.AthleteStateModel.AthleteState

/**
 * Real-time analytics: runs the MVP rule set against the current state +
 * envelope and emits [AnalysisEvent] candidates (arbitration decides what,
 * if anything, the athlete actually hears). Stateful only for sustain timers
 * and once-per-X latches.
 */
class AnalyticsEngine(private val profile: UserProfile) {

  private val sustain = mutableMapOf<String, Int>()
  private var hrQualityWarned = false
  private var lastSegIndex = -1
  private var halfwayFired = false
  private var final60Fired = false
  private var startSlotFired = false
  private var lastFatigueBand = 0
  private var ergSpiralFiresThisSession = 0
  private var cadenceDeclineFires = 0
  private var recoveryEvaluatedForSegment = -1

  fun analyze(
    state: AthleteState,
    envelope: ExpectationEnvelope,
    ctx: IntervalContext,
    activeSec: Int,
    ergEnabled: Boolean
  ): List<AnalysisEvent> {
    val events = mutableListOf<AnalysisEvent>()

    if (ctx.classified.index != lastSegIndex) {
      lastSegIndex = ctx.classified.index
      halfwayFired = false
      final60Fired = false
      startSlotFired = false
      sustain.keys.filter { it.startsWith("interval:") }.forEach { sustain.remove(it) }
    }

    val hr = state.hr10s
    val hrConfident = state.hrConfidence >= 0.7

    // ---- SAFETY: HR at ceiling sustained 30 s (P0) ----
    val ceiling = profile.maxHr * HR_CEILING_PCT
    if (hr != null && hrConfident && state.hrConfidence >= 0.8 && hr >= ceiling) {
      if (sustained("safety-hr", 30)) {
        events += AnalysisEvent(
          ruleId = "safety-hr-ceiling", category = FeedbackCategory.SAFETY, severity = 3,
          message = "Heart rate is very high (${hr.toInt()} bpm). Back off the effort now.",
          signalConfidence = state.hrConfidence, expiresAtSec = activeSec + 10
        )
      }
    } else sustain.remove("safety-hr")

    // ---- DATA_QUALITY: flaky HR (once per session) ----
    if (!hrQualityWarned && state.hrConfidence in 0.01..0.69 && activeSec > 120) {
      hrQualityWarned = true
      events += AnalysisEvent(
        ruleId = "data-quality-hr", category = FeedbackCategory.DATA_QUALITY, severity = 1,
        message = "Heart-rate signal looks unreliable — HR-based coaching is paused.",
        expiresAtSec = activeSec + 30
      )
    }

    // ---- TECHNIQUE: ERG-spiral precursor — low cadence at high force ----
    val cadence = state.cadence10s
    if (ergEnabled && ctx.isWork && cadence != null && state.cadenceConfidence >= 0.7 &&
      cadence < ERG_SPIRAL_CADENCE_RPM && ergSpiralFiresThisSession < 3
    ) {
      if (sustained("erg-spiral", 10)) {
        ergSpiralFiresThisSession++
        events += AnalysisEvent(
          ruleId = "erg-spiral", category = FeedbackCategory.TECHNIQUE, severity = 2,
          message = "Cadence is dropping — spin up now before the trainer bogs down.",
          signalConfidence = state.cadenceConfidence, expiresAtSec = activeSec + 10
        )
      }
    } else sustain.remove("erg-spiral")

    // ---- PACING: adherence outside band (non-ERG), respecting quiet zones ----
    val inQuietZone = ctx.elapsedInSegmentSec < 30 || ctx.remainingInSegmentSec < 15
    if (!ergEnabled && ctx.isWork && !inQuietZone && ctx.segmentClass != SegmentClass.FREE_RIDE) {
      val p = state.power30s
      if (p < envelope.powerBand.start && sustained("pacing-under", 20)) {
        events += AnalysisEvent(
          ruleId = "pacing-under", category = FeedbackCategory.PACING, severity = 1,
          message = "Power is below target — lift it back to ${ctx.classified.targetMidWatts.toInt()} W.",
          expiresAtSec = activeSec + 15
        )
      } else if (p > envelope.powerBand.endInclusive && sustained("pacing-over", 20)) {
        events += AnalysisEvent(
          ruleId = "pacing-over", category = FeedbackCategory.PACING, severity = 1,
          message = "You're riding over target — ease back to ${ctx.classified.targetMidWatts.toInt()} W.",
          expiresAtSec = activeSec + 15
        )
      }
      if (p in envelope.powerBand) { sustain.remove("pacing-under"); sustain.remove("pacing-over") }
    }

    // ---- PACING: over-riding a recovery-intent ride ----
    if (ctx.intent == WorkoutIntent.RECOVERY && ctx.classified.targetMidWatts > 0 &&
      state.power30s > envelope.powerBand.endInclusive
    ) {
      if (sustained("recovery-intent-over", 30)) {
        events += AnalysisEvent(
          ruleId = "recovery-intent-over", category = FeedbackCategory.PACING, severity = 2,
          message = "Today is a recovery ride — this is supposed to be easy. Back it off.",
          expiresAtSec = activeSec + 15
        )
      }
    } else sustain.remove("recovery-intent-over")

    // ---- FATIGUE_MANAGEMENT: score crossing 60 / 80 ----
    val fatigueBand = when {
      state.fatigueScore >= 80 -> 2
      state.fatigueScore >= 60 -> 1
      else -> 0
    }
    if (fatigueBand > lastFatigueBand && !ctx.isFinalWorkInterval) {
      events += AnalysisEvent(
        ruleId = "fatigue-band-$fatigueBand", category = FeedbackCategory.FATIGUE_MANAGEMENT,
        severity = fatigueBand,
        message = if (fatigueBand == 1)
          "Fatigue is building — focus on smooth pedaling and keep recoveries genuinely easy."
        else
          "Fatigue looks high. Consider easing the next interval or extending recovery.",
        signalConfidence = state.hrConfidence, expiresAtSec = activeSec + 60
      )
    }
    if (fatigueBand != lastFatigueBand) lastFatigueBand = fatigueBand

    // ---- FATIGUE: HR above expectation envelope, sustained ----
    val allowVo2Drift = ctx.intent in setOf(WorkoutIntent.VO2_DEV, WorkoutIntent.ANAEROBIC_CAP)
    if (hr != null && hrConfident && envelope.hrBand != null && ctx.isWork &&
      hr > envelope.hrBand.endInclusive && !allowVo2Drift && !ctx.isFinalWorkInterval
    ) {
      if (sustained("interval:hr-high", 45)) {
        events += AnalysisEvent(
          ruleId = "hr-above-expected", category = FeedbackCategory.FATIGUE_MANAGEMENT, severity = 1,
          message = "Heart rate is running higher than expected for this power — keep an eye on it.",
          signalConfidence = state.hrConfidence, expiresAtSec = activeSec + 30
        )
      }
    } else sustain.remove("interval:hr-high")

    // ---- RECOVERY quality: once per recovery, at 60 s+ ----
    if (ctx.segmentClass == SegmentClass.RECOVERY &&
      ctx.elapsedInSegmentSec >= 60 && recoveryEvaluatedForSegment != ctx.classified.index
    ) {
      recoveryEvaluatedForSegment = ctx.classified.index
      val prevWork = ctx.classified.index > 0
      if (prevWork && hrConfident) {
        // HRR60 handled by the state model; a small drop after hard work = incomplete recovery
        val startHr = state.segmentStartHr
        if (startHr != null && hr != null && startHr - hr < 15 && startHr > profile.maxHr * 0.8) {
          events += AnalysisEvent(
            ruleId = "recovery-incomplete", category = FeedbackCategory.RECOVERY, severity = 1,
            message = "Heart rate isn't coming down much — soft-pedal and breathe deep.",
            signalConfidence = state.hrConfidence, expiresAtSec = activeSec + 30
          )
        }
      }
    }

    // ---- TECHNIQUE: cadence out of band 30 s / cross-interval decline ----
    if (cadence != null && envelope.cadenceBand != null && ctx.isWork &&
      ctx.segmentClass != SegmentClass.SPRINT && state.cadenceConfidence >= 0.7 &&
      cadence < envelope.cadenceBand.start - 3
    ) {
      if (sustained("cadence-low", 30) && cadenceDeclineFires < 2) {
        cadenceDeclineFires++
        events += AnalysisEvent(
          ruleId = "cadence-low", category = FeedbackCategory.TECHNIQUE, severity = 1,
          message = "Your cadence has dropped below your usual rhythm — try to spin it back up.",
          signalConfidence = state.cadenceConfidence, expiresAtSec = activeSec + 20
        )
      }
    } else sustain.remove("cadence-low")

    // ---- MOTIVATION slots ----
    if (ctx.isWork && !startSlotFired && ctx.elapsedInSegmentSec in 2..8) {
      startSlotFired = true
      val setInfo = ctx.set?.let { " — interval ${ctx.blockNumber} of ${it.blockCount}" } ?: ""
      events += AnalysisEvent(
        ruleId = "slot-interval-start", category = FeedbackCategory.MOTIVATION, severity = 0,
        message = "${formatDuration(ctx.classified.segment.durationSec)} at " +
          "${ctx.classified.targetMidWatts.toInt()} W$setInfo.",
        expiresAtSec = activeSec + 8
      )
    }
    val hardClasses = setOf(
      SegmentClass.THRESHOLD, SegmentClass.VO2MAX, SegmentClass.ANAEROBIC, SegmentClass.SWEET_SPOT
    )
    if (ctx.isWork && ctx.segmentClass in hardClasses && !final60Fired &&
      ctx.remainingInSegmentSec in 45..60 && ctx.classified.segment.durationSec >= 180
    ) {
      final60Fired = true
      val tail = if (ctx.isFinalWorkInterval) "Last interval — empty the tank." else "Final minute — hold it right here."
      events += AnalysisEvent(
        ruleId = "slot-final-60", category = FeedbackCategory.MOTIVATION, severity = 0,
        message = tail, expiresAtSec = activeSec + 10
      )
    }
    if (ctx.isWork && !halfwayFired && ctx.classified.segment.durationSec >= 600 &&
      ctx.elapsedInSegmentSec >= ctx.classified.segment.durationSec / 2
    ) {
      halfwayFired = true
      events += AnalysisEvent(
        ruleId = "slot-halfway", category = FeedbackCategory.MOTIVATION, severity = 0,
        message = "Halfway through this block — settle in and stay smooth.",
        expiresAtSec = activeSec + 10
      )
    }

    return events
  }

  /**
   * Increments the named sustain counter; true each time the condition has
   * held for another [seconds] (so a persisting condition re-candidates
   * periodically — cooldowns and dedupe decide whether it is re-spoken).
   */
  private fun sustained(key: String, seconds: Int): Boolean {
    val n = (sustain[key] ?: 0) + 1
    sustain[key] = n
    return n % seconds == 0
  }

  private fun formatDuration(sec: Int): String =
    if (sec % 60 == 0) "${sec / 60} min" else "${sec / 60}:${"%02d".format(sec % 60)}"

  companion object {
    private const val HR_CEILING_PCT = 0.97
    private const val ERG_SPIRAL_CADENCE_RPM = 65.0
  }
}
