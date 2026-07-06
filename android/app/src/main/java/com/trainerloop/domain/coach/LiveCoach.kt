package com.trainerloop.domain.coach

import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.data.model.UserProfile
import com.trainerloop.data.model.WorkoutSegment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Live Feedback Coach Mode — the deterministic tick pipeline (§2 layers 2–6):
 * interpreter → athlete state → expectations → analytics → arbitration.
 * Pure Kotlin; call [onTick] once per clock second.
 */
class LiveCoach(
  segments: List<WorkoutSegment>,
  private val profile: UserProfile,
  coach: com.trainerloop.data.model.CoachProfile? = null
) {
  var plan: WorkoutPlanModel = WorkoutInterpreter.interpret(segments, profile.ftp)
    private set

  /** Re-runs the interpreter after a mid-session modification (e.g. extended recovery). */
  fun replan(segments: List<WorkoutSegment>) {
    plan = WorkoutInterpreter.interpret(segments, profile.ftp)
  }

  private val stateModel = AthleteStateModel(profile)
  private val expectationEngine = ExpectationEngine(profile)
  private val analytics = AnalyticsEngine(profile, coach)
  private val decisionEngine = FeedbackDecisionEngine(
    plan.totalDurationSec,
    verbosity = coach?.verbosity ?: 1.0,
    cooldownScale = coach?.cooldownScale ?: 1.0,
    motivationShare = coach?.motivationShare ?: 0.33
  )

  private val _currentFeedback = MutableStateFlow<FeedbackItem?>(null)
  val currentFeedback: StateFlow<FeedbackItem?> = _currentFeedback

  private val _feedbackLog = MutableStateFlow<List<FeedbackItem>>(emptyList())
  val feedbackLog: StateFlow<List<FeedbackItem>> = _feedbackLog

  private var cadenceBaseline: Double? = null
  private var baselineClass: SegmentClass? = null
  private var lastElapsedSec = -1
  private var lastFatigue = 0.0
  private val fatigueCurve = mutableListOf<Double>()

  data class TickInput(
    val elapsedSec: Int,
    val activeSec: Int,
    val isRunning: Boolean,
    val sample: TelemetrySample,
    val targetMidWatts: Double,
    val ergEnabled: Boolean,
    val modificationPending: Boolean
  )

  fun onTick(input: TickInput): FeedbackItem? {
    if (!input.isRunning) return null

    // Seek / pause gap: flush windows so stale samples don't leak across.
    if (lastElapsedSec >= 0 && kotlin.math.abs(input.elapsedSec - lastElapsedSec) > 30) {
      stateModel.invalidateWindows()
    }
    lastElapsedSec = input.elapsedSec

    val ctx = WorkoutInterpreter.contextAt(plan, input.elapsedSec) ?: return null

    val bandPct = expectationEngine.powerBandPct(ctx.segmentClass)
    val state = stateModel.onSample(input.sample, ctx, input.targetMidWatts, bandPct)

    // Cadence baseline: mean cadence in the first work interval of each class.
    if (ctx.isWork && (baselineClass != ctx.segmentClass || cadenceBaseline == null)) {
      state.segmentAvgCadence?.let {
        if (ctx.elapsedInSegmentSec > 60) { cadenceBaseline = it; baselineClass = ctx.segmentClass }
      }
    }

    state.hr10s?.let {
      expectationEngine.calibrate(it, input.targetMidWatts, ctx.elapsedInSegmentSec, ctx.segmentClass)
    }

    lastFatigue = state.fatigueScore
    if (input.activeSec > 0 && input.activeSec % 60 == 0) fatigueCurve.add(state.fatigueScore)

    val envelope = expectationEngine.expectationFor(
      ctx, input.targetMidWatts, state.fatigueScore, cadenceBaseline
    )

    decisionEngine.submit(analytics.analyze(state, envelope, ctx, input.activeSec, input.ergEnabled))

    if (input.activeSec % ARBITRATION_PERIOD_SEC != 0) return null
    val item = decisionEngine.arbitrate(input.activeSec, input.modificationPending) ?: return null
    _currentFeedback.value = item
    _feedbackLog.value = _feedbackLog.value + item
    return item
  }

  /**
   * v1 modification suggestions rehomed as WORKOUT_MODIFICATION (§8.1):
   * the event competes in arbitration like everything else, so the global
   * gap/budget/log apply to it.
   */
  fun submitExternal(event: AnalysisEvent) {
    decisionEngine.submit(listOf(event))
  }

  /** Latest fatigue/confidence snapshot for summary screens. */
  fun ledger(): List<IntervalRecord> = stateModel.ledger.toList()

  /** Snapshot of all derived session data for persistence (§9). */
  fun sessionData(): CoachSessionData = CoachSessionData(
    feedback = _feedbackLog.value,
    intervals = stateModel.ledger.toList(),
    recoveries = stateModel.recoveries.toList(),
    fatigueCurve = fatigueCurve.toList(),
    finalFatigueScore = lastFatigue
  )

  fun dismissCurrent() {
    _currentFeedback.value = null
  }

  companion object {
    const val ARBITRATION_PERIOD_SEC = 5
  }
}
