package com.trainerloop.domain

import com.trainerloop.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.math.sqrt

class CoachEngine(
  private val profile: CoachProfile,
  private val segments: List<WorkoutSegment>
) {

  private val _events = MutableStateFlow<List<CoachEvent>>(emptyList())
  val events: StateFlow<List<CoachEvent>> = _events

  private val _suggestions = MutableStateFlow<List<CoachSuggestion>>(emptyList())
  val suggestions: StateFlow<List<CoachSuggestion>> = _suggestions

  private val _pendingSuggestion = MutableStateFlow<CoachSuggestion?>(null)
  val pendingSuggestion: StateFlow<CoachSuggestion?> = _pendingSuggestion

  private val mutex = Mutex()

  private var currentSessionId: Int = -1
  private var lastActiveSec: Int = 0
  private var lastSegment: SegmentSnapshot? = null
  private val completedWorkIntervals = mutableListOf<IntervalSummary>()
  private val rejectedDownSuggestions = mutableListOf<CoachSuggestion>()
  private var lastSuggestionAtSec: Int? = null
  private var completionLogged = false

  data class Input(
    val activeSec: Int,
    val isRunning: Boolean,
    val isComplete: Boolean,
    val hasPlan: Boolean,
    val sessionId: Int,
    val segmentIndex: Int,
    val elapsedInSegmentSec: Int,
    val segmentStartSec: Int,
    val segmentEndSec: Int,
    val targetRange: TargetRange,
    val samples: List<TelemetrySample>,
    val intensityOffsetPct: Int = 0,
    val ergEnabled: Boolean = false
  )

  suspend fun tick(input: Input) {
    mutex.withLock {
      if (input.sessionId != currentSessionId) {
        reset(input.sessionId)
      }
      lastActiveSec = input.activeSec

      if (!input.isRunning && !input.isComplete) return

      if (input.isComplete && !completionLogged) {
        logCompletion(input.activeSec)
        return
      }

      val currentSegment = segments.getOrNull(input.segmentIndex)
      handleSegmentTransition(input, currentSegment)

      if (currentSegment?.isWork == true) {
        evaluateWorkSegment(input)
      }
    }
  }

  suspend fun accept(suggestionId: String): CoachSuggestion? {
    return mutex.withLock {
      updateSuggestionStatus(suggestionId, SuggestionStatus.ACCEPTED)
    }
  }

  suspend fun reject(suggestionId: String) {
    mutex.withLock {
      val suggestion = updateSuggestionStatus(suggestionId, SuggestionStatus.REJECTED)
      if (suggestion?.action is CoachAction.AdjustIntensityDown) {
        rejectedDownSuggestions.add(suggestion)
        if (rejectedDownSuggestions.size > MAX_REJECTED_DOWN_HISTORY) {
          rejectedDownSuggestions.removeAt(0)
        }
      }
    }
  }

  private fun reset(sessionId: Int) {
    currentSessionId = sessionId
    lastActiveSec = 0
    lastSegment = null
    completedWorkIntervals.clear()
    rejectedDownSuggestions.clear()
    lastSuggestionAtSec = null
    completionLogged = false
    _suggestions.value = emptyList()
    _pendingSuggestion.value = null
    _events.value = emptyList()
  }

  private fun handleSegmentTransition(input: Input, currentSegment: WorkoutSegment?) {
    val previous = lastSegment
    if (previous != null && currentSegment != null && previous.id != currentSegment.id && previous.isWork) {
      val previousSegment = segments.getOrNull(previous.index)
      val targetMid = targetMidForSegment(previousSegment)
      val metrics = computeMetrics(input.samples, previous.startSec, previous.endSec, targetMid)
      if (metrics != null) {
        completedWorkIntervals.add(
          IntervalSummary(
            adherencePct = metrics.adherencePct,
            cadenceVariance = metrics.cadenceVariance,
            hrDriftPct = metrics.hrDriftPct
          )
        )
        if (completedWorkIntervals.size > MAX_INTERVAL_HISTORY) {
          completedWorkIntervals.removeAt(0)
        }
      }

      if (currentSegment.phase == SegmentPhase.RECOVERY && metrics != null && canSuggest(input)) {
        if (metrics.adherencePct <= profile.rules.targetAdherenceIntervene &&
          metrics.hrDriftPct >= profile.rules.hrDriftWarn
        ) {
          val seconds = profile.interventions.recoveryExtendStepSec
          val action = CoachAction.ExtendRecovery(seconds)
          addSuggestion(
            CoachSuggestion(
              id = createId(),
              action = action,
              message = CoachMessageBuilder.suggestionMessage(profile, action),
              rationale = CoachMessageBuilder.rationale(profile, action),
              segmentIndex = input.segmentIndex
            ),
            input.activeSec
          )
        }
      }

      checkSkipRemaining(input)
    }

    if (currentSegment != null) {
      lastSegment = SegmentSnapshot(
        id = currentSegment.id,
        index = input.segmentIndex,
        isWork = currentSegment.isWork,
        phase = currentSegment.phase,
        startSec = input.segmentStartSec,
        endSec = input.segmentEndSec
      )
    }
  }

  private fun checkSkipRemaining(input: Input) {
    if (!profile.interventions.allowSkipRemainingOnIntervals) return
    val failedIntervals = completedWorkIntervals.takeLast(2)
    if (failedIntervals.size < 2) return
    val meetsFailure = failedIntervals.all { interval ->
      interval.adherencePct <= profile.rules.targetAdherenceIntervene ||
        interval.hrDriftPct >= profile.rules.hrDriftIntervene ||
        interval.cadenceVariance >= profile.rules.cadenceVarianceIntervene
    }
    if (!meetsFailure) return
    if (rejectedDownSuggestions.takeLast(2).size < 2) return
    if (!canSuggest(input)) return

    val action = CoachAction.SkipRemainingOnIntervals
    addSuggestion(
      CoachSuggestion(
        id = createId(),
        action = action,
        message = CoachMessageBuilder.suggestionMessage(profile, action),
        rationale = CoachMessageBuilder.rationale(profile, action),
        segmentIndex = input.segmentIndex
      ),
      input.activeSec
    )
  }

  private fun evaluateWorkSegment(input: Input) {
    if (input.elapsedInSegmentSec < ADHERENCE_WINDOW_SEC) return
    if (!canSuggest(input)) return

    val targetMid = (input.targetRange.low + input.targetRange.high) / 2.0
    val recent = computeMetrics(
      input.samples,
      maxOf(0, input.activeSec - ADHERENCE_WINDOW_SEC),
      input.activeSec,
      targetMid
    )
    val stability = computeMetrics(
      input.samples,
      maxOf(0, input.activeSec - STABILITY_WINDOW_SEC),
      input.activeSec,
      targetMid
    )
    val drift = computeMetrics(
      input.samples,
      maxOf(0, input.activeSec - HR_DRIFT_WINDOW_SEC),
      input.activeSec,
      targetMid
    )
    if (recent == null || stability == null || drift == null) return

    val canAdjustUp = input.intensityOffsetPct + profile.interventions.intensityAdjustStepPct <=
      profile.interventions.intensityAdjustMaxPct
    val canAdjustDown = input.intensityOffsetPct - profile.interventions.intensityAdjustStepPct >=
      profile.interventions.intensityAdjustMinPct

    val reduceCondition = (recent.adherencePct <= profile.rules.targetAdherenceIntervene || input.ergEnabled) &&
      (drift.hrDriftPct >= profile.rules.hrDriftIntervene ||
        drift.cadenceVariance >= profile.rules.cadenceVarianceIntervene)

    if (reduceCondition && canAdjustDown) {
      val percent = profile.interventions.intensityAdjustStepPct.toInt()
      val action = CoachAction.AdjustIntensityDown(percent)
      addSuggestion(
        CoachSuggestion(
          id = createId(),
          action = action,
          message = CoachMessageBuilder.suggestionMessage(profile, action),
          rationale = CoachMessageBuilder.rationale(profile, action),
          segmentIndex = input.segmentIndex
        ),
        input.activeSec
      )
      return
    }

    val recentRejections = _suggestions.value
      .filter { it.status == SuggestionStatus.REJECTED }
      .takeLast(2)
    val allowIncrease = recentRejections.size < 2 &&
      input.elapsedInSegmentSec >= MIN_ELAPSED_FOR_INCREASE_SEC &&
      stability.adherencePct >= profile.rules.targetAdherenceWarn &&
      stability.cadenceVariance <= profile.rules.cadenceVarianceWarn &&
      drift.hrDriftPct <= profile.rules.hrDriftWarn

    if (allowIncrease && canAdjustUp) {
      val percent = profile.interventions.intensityAdjustStepPct.toInt()
      val action = CoachAction.AdjustIntensityUp(percent)
      addSuggestion(
        CoachSuggestion(
          id = createId(),
          action = action,
          message = CoachMessageBuilder.suggestionMessage(profile, action),
          rationale = CoachMessageBuilder.rationale(profile, action),
          segmentIndex = input.segmentIndex
        ),
        input.activeSec
      )
    }
  }

  private fun canSuggest(input: Input): Boolean {
    if (!input.hasPlan) return false
    if (!input.isRunning) return false
    if (input.activeSec < profile.rules.minElapsedSecondsForSuggestions) return false
    if (_pendingSuggestion.value != null) return false
    val lastAt = lastSuggestionAtSec
    if (lastAt != null && input.activeSec - lastAt < profile.rules.cooldownSeconds) return false
    return true
  }

  private fun addSuggestion(suggestion: CoachSuggestion, activeSec: Int) {
    _suggestions.value = _suggestions.value + suggestion
    _pendingSuggestion.value = suggestion
    _events.value = _events.value + CoachEvent(
      id = createId(),
      sessionId = currentSessionId.toString(),
      timestamp = activeSec.toString(),
      type = CoachEventType.SUGGESTION,
      message = suggestion.message,
      rationale = suggestion.rationale,
      suggestion = suggestion
    )
    lastSuggestionAtSec = activeSec
  }

  private fun updateSuggestionStatus(suggestionId: String, status: SuggestionStatus): CoachSuggestion? {
    val suggestion = _suggestions.value.find { it.id == suggestionId && it.status == SuggestionStatus.PENDING }
      ?: return null
    val updated = suggestion.copy(status = status)
    _suggestions.value = _suggestions.value.map {
      if (it.id == suggestionId) updated else it
    }
    _pendingSuggestion.value = _suggestions.value.find { it.status == SuggestionStatus.PENDING }
    _events.value = _events.value + CoachEvent(
      id = createId(),
      sessionId = currentSessionId.toString(),
      timestamp = lastActiveSec.toString(),
      type = CoachEventType.SUGGESTION,
      message = "${status.name.lowercase().replaceFirstChar { it.uppercase() }}: ${suggestion.message}",
      suggestion = updated,
      userResponse = CoachResponse(ResponseType.valueOf(status.name), lastActiveSec.toString())
    )
    return updated
  }

  private fun logCompletion(activeSec: Int) {
    completionLogged = true
    _events.value = _events.value + CoachEvent(
      id = createId(),
      sessionId = currentSessionId.toString(),
      timestamp = activeSec.toString(),
      type = CoachEventType.COMPLETION,
      message = CoachMessageBuilder.completionMessage(profile)
    )
  }

  private fun targetMidForSegment(segment: WorkoutSegment?): Double {
    return when (segment) {
      is WorkoutSegment.Step -> (segment.targetRange.low + segment.targetRange.high) / 2.0
      is WorkoutSegment.Ramp -> (segment.startPower + segment.endPower) / 2.0
      else -> 0.0
    }
  }

  private data class SegmentSnapshot(
    val id: String,
    val index: Int,
    val isWork: Boolean,
    val phase: SegmentPhase,
    val startSec: Int,
    val endSec: Int
  )

  private data class IntervalSummary(
    val adherencePct: Double,
    val cadenceVariance: Double,
    val hrDriftPct: Double
  )

  private data class WindowMetrics(
    val avgPower: Double,
    val adherencePct: Double,
    val cadenceVariance: Double,
    val hrDriftPct: Double
  )

  companion object {
    private const val ADHERENCE_WINDOW_SEC = 30
    private const val STABILITY_WINDOW_SEC = 90
    private const val HR_DRIFT_WINDOW_SEC = 120
    private const val MIN_SAMPLES = 4
    private const val MAX_INTERVAL_HISTORY = 8
    private const val MAX_REJECTED_DOWN_HISTORY = 2
    private const val MIN_ELAPSED_FOR_INCREASE_SEC = 45

    private fun computeMetrics(
      samples: List<TelemetrySample>,
      fromSec: Int,
      toSec: Int,
      targetMid: Double
    ): WindowMetrics? {
      val window = samples.filter { it.timeSec in fromSec..toSec }
      if (window.size < MIN_SAMPLES) return null
      val powerValues = window.map { it.powerWatts }
      val cadenceValues = window.map { it.cadenceRpm }.filter { it > 0 }
      val hrValues = window.map { it.hrBpm }.filter { it > 0 }
      val avgPower = powerValues.average()
      val adherencePct = if (targetMid > 0) (avgPower / targetMid) * 100 else 0.0
      val cadenceVariance = if (cadenceValues.isNotEmpty()) stddev(cadenceValues) else 0.0
      val hrDriftPct = if (hrValues.size > 1) {
        val third = (hrValues.size / 3).coerceAtLeast(1)
        val start = hrValues.take(third).average()
        val end = hrValues.takeLast(third).average()
        if (start > 0) ((end - start) / start) * 100 else 0.0
      } else 0.0
      return WindowMetrics(avgPower, adherencePct, cadenceVariance, hrDriftPct)
    }

    private fun stddev(values: List<Int>): Double {
      if (values.isEmpty()) return 0.0
      val mean = values.average()
      val variance = values.map { (it - mean) * (it - mean) }.average()
      return sqrt(variance)
    }

    private fun createId(): String = UUID.randomUUID().toString()
  }
}
