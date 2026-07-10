package com.trainerloop.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainerloop.ble.FtmsControlManager
import com.trainerloop.ble.FtmsManager
import com.trainerloop.ble.HrManager
import com.trainerloop.data.model.CoachEvent
import com.trainerloop.data.model.CoachInterventions
import com.trainerloop.data.model.CoachMessages
import com.trainerloop.data.model.CoachProfile
import com.trainerloop.data.model.CoachRules
import com.trainerloop.data.model.CoachAction
import com.trainerloop.data.model.CoachSuggestion
import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.data.model.UserProfile
import com.trainerloop.data.model.Workout
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.data.model.withDurationSec
import com.trainerloop.domain.CoachEngine
import com.trainerloop.domain.coach.FeedbackItem
import com.trainerloop.domain.coach.LiveCoach
import com.trainerloop.domain.RampTest
import com.trainerloop.domain.TelemetryRecorder
import com.trainerloop.domain.WorkoutClock
import com.trainerloop.domain.WorkoutMath
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class WorkoutUiState(
  val isRunning: Boolean = false,
  val isComplete: Boolean = false,
  val elapsedSec: Int = 0,
  val activeSec: Int = 0,
  val segmentIndex: Int = 0,
  val segmentStartSec: Int = 0,
  val segmentEndSec: Int = 0,
  val elapsedInSegmentSec: Int = 0,
  val targetRange: TargetRange = TargetRange(0, 0),
  val currentPowerWatts: Int = 0,
  val currentCadenceRpm: Int = 0,
  val currentHrBpm: Int = 0,
  val sensorDropout: Boolean = true,
  val samples: List<TelemetrySample> = emptyList(),
  /** Live segment list — mutated when a recovery is extended mid-ride. */
  val segments: List<WorkoutSegment> = emptyList(),
  val intensityOffsetPct: Int = 0,
  val isErgEnabled: Boolean = true,
  /** Seconds spent on-target within the current interval so far. */
  val inZoneSec: Int = 0,
  val error: String? = null,
  // Coach state
  val pendingSuggestion: CoachSuggestion? = null,
  val coachEvents: List<CoachEvent> = emptyList(),
  // Live Feedback Coach Mode
  val liveFeedback: FeedbackItem? = null,
  val feedbackLog: List<FeedbackItem> = emptyList(),
  val currentVirtualSpeedKph: Double? = null,
  val currentGradePercent: Double? = null,
  val virtualDistanceM: Double? = null,
  /** Expected elevation per second (static per workout); null when sim is off. */
  val elevationProfile: DoubleArray? = null
)

data class WorkoutFinishData(
  val workoutId: String,
  val workoutName: String,
  val startTimeMs: Long,
  val samples: List<TelemetrySample>,
  val coachJson: String = "",
  val completedNaturally: Boolean = false
)

private data class WorkoutControlTick(
  val running: Boolean,
  val ergEnabled: Boolean,
  val targetRange: TargetRange
)

/**
 * @param ftmsManagerFlow the application-owned [FtmsManager] state. Pass the
 *   `StateFlow` (not a snapshot) so a manager that appears *after* this
 *   ViewModel is created still wires into the recorder.
 * @param hrManagerFlow same idea for the HR sensor.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkoutViewModel(
  private val workout: Workout,
  private val ftmsManagerFlow: StateFlow<FtmsManager?> = MutableStateFlow(null),
  private val hrManagerFlow: StateFlow<HrManager?> = MutableStateFlow(null),
  private val ftmsControlManagerFlow: StateFlow<FtmsControlManager?> = MutableStateFlow(null),
  private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
  private val userProfile: UserProfile = UserProfile(),
  coachProfile: CoachProfile? = null,
  private val now: () -> Long = System::currentTimeMillis
) : ViewModel() {

  private val clock = WorkoutClock(workout.segments, dispatcher)
  private val coachEngine = CoachEngine(coachProfile ?: defaultProfile(), workout.segments)
  private val liveCoach = LiveCoach(workout.segments, userProfile, coachProfile)
  private val isRampTest = RampTest.isRampTest(workout.id)

  private val physicsParams = com.trainerloop.domain.sim.PhysicsParams(
    riderKg = userProfile.weightKg,
    bikeKg = userProfile.bikeWeightKg,
    crr = userProfile.rollingResistanceCrr,
    cda = userProfile.dragAreaCda
  )
  private val route: com.trainerloop.domain.sim.RouteProfile? =
    if (userProfile.virtualRideEnabled && !isRampTest) {
      com.trainerloop.domain.sim.RouteGenerator.generate(workout, userProfile.ftp, physicsParams)
    } else null
  private val virtualRide = route?.let {
    com.trainerloop.domain.sim.VirtualRideTracker(it, physicsParams)
  }

  // Ramp-test failure detection: consecutive seconds below 50% of step target.
  private var rampBelowTargetSec = 0

  /** Live timeline. Diverges from [workout.segments] once a recovery is extended. */
  private var segments: List<WorkoutSegment> = workout.segments

  private val _uiState = MutableStateFlow(
    WorkoutUiState(segments = workout.segments, elevationProfile = route?.expectedAltitudeM)
  )
  val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

  private val _finishEvent = MutableStateFlow<WorkoutFinishData?>(null)
  val finishEvent: StateFlow<WorkoutFinishData?> = _finishEvent.asStateFlow()

  /**
   * The active recorder. Replaced when [ftmsManagerFlow] / [hrManagerFlow]
   * change, so a manager that attaches after this ViewModel was created (or
   * one that reattaches after a disconnect) is picked up.
   */
  private val recorder = MutableStateFlow<TelemetryRecorder?>(null)

  private var wasErgEnabled: Boolean = true
  private var sessionStartMs: Long? = null

  private var ergWriteJob: Job? = null

  // Time-in-zone bookkeeping: reset whenever the interval changes.
  private var inZoneSegmentIndex: Int = -1
  private var inZoneCount: Int = 0

  // v1 suggestion detected but not yet surfaced (waiting on arbitration).
  private var awaitingSuggestion: CoachSuggestion? = null
  private var awaitingSuggestionSinceSec: Int = 0

  init {
    // (Re)create the recorder whenever the manager references change.
    viewModelScope.launch {
      combine(ftmsManagerFlow, hrManagerFlow) { ftms, hr -> ftms to hr }
        .distinctUntilChanged()
        .collect { (ftms, hr) ->
          val previous = recorder.value
          val carried = previous?.samples?.value ?: emptyList()
          val next = if (ftms != null) {
            TelemetryRecorder(
              clock, ftms, hr, dispatcher, virtualRide, initialSamples = carried
            )
              .also { it.startCollecting() }
          } else null
          recorder.value = next
          previous?.stop()
          com.trainerloop.ble.BleLog.d(
            "VM recorder swap: ftms=${ftms?.device?.address} hr=${hr?.device?.address}"
          )
        }
    }

    // Power + cadence via the recorder (1 Hz, gated by clock).
    viewModelScope.launch {
      recorder
        .flatMapLatest { r ->
          if (r == null) flowOf(emptySample())
          else r.latest
        }
        .collect { sample ->
          _uiState.value = _uiState.value.copy(
            currentPowerWatts = sample.powerWatts,
            currentCadenceRpm = sample.cadenceRpm,
            sensorDropout = sample.dropout,
            currentVirtualSpeedKph = sample.virtualSpeedKph,
            currentGradePercent = sample.gradePercent,
            virtualDistanceM = sample.virtualDistanceM
          )
        }
    }

    // Samples list (for the chart).
    viewModelScope.launch {
      recorder
        .flatMapLatest { r -> r?.samples ?: flowOf(emptyList()) }
        .collect { samples -> _uiState.value = _uiState.value.copy(samples = samples) }
    }

    // Fast-path HR: bypass the recorder entirely so the displayed HR
    // updates the instant an HR packet arrives instead of waiting for the
    // next 1 Hz clock tick.
    viewModelScope.launch {
      hrManagerFlow
        .flatMapLatest { hr -> hr?.heartRate?.map { it?.value } ?: flowOf(null) }
        .filterNotNull()
        .collect { bpm -> _uiState.value = _uiState.value.copy(currentHrBpm = bpm) }
    }

    viewModelScope.launch {
      clock.elapsedSec.collect {
        updateFromClock()
        updateInZone()
        if (userProfile.coachEnabled) {
          tickCoach()
          tickLiveCoach()
        }
        detectRampFailure()
      }
    }
    viewModelScope.launch {
      clock.isRunning.collect { running ->
        _uiState.value = _uiState.value.copy(isRunning = running)
      }
    }
    viewModelScope.launch {
      clock.isComplete.collect { complete ->
        _uiState.value = _uiState.value.copy(isComplete = complete)
        if (complete) {
          tickCoach()
          maybeEmitFinish()
        }
      }
    }
    viewModelScope.launch {
      coachEngine.events.collect { events ->
        _uiState.value = _uiState.value.copy(coachEvents = events)
      }
    }
    viewModelScope.launch {
      // v1 suggestions rehomed (§8.1): detection stays in CoachEngine, but the
      // suggestion only reaches the UI once it wins arbitration in LiveCoach.
      coachEngine.pendingSuggestion.collect { suggestion ->
        if (suggestion == null) {
          awaitingSuggestion = null
          _uiState.value = _uiState.value.copy(pendingSuggestion = null)
        } else {
          awaitingSuggestion = suggestion
          awaitingSuggestionSinceSec = _uiState.value.activeSec
          liveCoach.submitExternal(
            com.trainerloop.domain.coach.AnalysisEvent(
              ruleId = "v1-modification",
              category = com.trainerloop.domain.coach.FeedbackCategory.WORKOUT_MODIFICATION,
              severity = 2,
              message = suggestion.message,
              expiresAtSec = _uiState.value.activeSec + SUGGESTION_ARBITRATION_TTL_SEC
            )
          )
        }
      }
    }
    viewModelScope.launch {
      liveCoach.currentFeedback.collect { feedback ->
        if (feedback?.category == com.trainerloop.domain.coach.FeedbackCategory.WORKOUT_MODIFICATION) {
          // Show the accept/reject suggestion card, not a plain feedback card.
          _uiState.value = _uiState.value.copy(pendingSuggestion = awaitingSuggestion)
          liveCoach.dismissCurrent()
        } else {
          _uiState.value = _uiState.value.copy(liveFeedback = feedback)
        }
      }
    }
    viewModelScope.launch {
      liveCoach.feedbackLog.collect { log ->
        _uiState.value = _uiState.value.copy(feedbackLog = log)
      }
    }

    viewModelScope.launch {
      // Drive ERG writes off the values that actually change the target, not the
      // 1 Hz clock. Otherwise every combine re-emit (once/sec) issues a GATT write
      // (~3600/ride) when only segment/toggle changes matter (~tens/ride).
      combine(
        _uiState.map { it.isRunning }.distinctUntilChanged(),
        _uiState.map { it.isErgEnabled }.distinctUntilChanged(),
        _uiState.map { it.targetRange }.distinctUntilChanged()
      ) { running, ergEnabled, targetRange ->
        WorkoutControlTick(running, ergEnabled, targetRange)
      }.collect { tick ->
        handleControlTick(tick)
      }
    }
  }

  fun start() {
    if (sessionStartMs == null) sessionStartMs = now()
    clock.start()
    updateFromClock()
    sendControlWhenReady { it.startResume() }
  }

  fun pause() {
    clock.pause()
    viewModelScope.launch {
      controlNow()?.stopPause(stop = false)
    }
  }

  fun resume() {
    clock.resume()
    updateFromClock()
    sendControlWhenReady { it.startResume() }
  }

  fun stop() {
    clock.stop()
    viewModelScope.launch {
      controlNow()?.stopPause(stop = true)
    }
    maybeEmitFinish()
    sessionStartMs = null
    recorder.value?.reset(clock.sessionId.value)
    _uiState.value = _uiState.value.copy(
      intensityOffsetPct = 0,
      samples = emptyList()
    )
  }

  fun seek(seconds: Int) {
    clock.seek(seconds)
    updateFromClock()
  }

  fun skipSegment() {
    val nextSegmentStart = _uiState.value.segmentEndSec
    if (nextSegmentStart < WorkoutMath.totalDurationSec(segments)) {
      seek(nextSegmentStart)
    }
  }

  fun consumeFinishEvent() {
    _finishEvent.value = null
  }

  fun toggleErg() {
    _uiState.value = _uiState.value.copy(
      isErgEnabled = !_uiState.value.isErgEnabled
    )
  }

  fun adjustIntensityUp() {
    val current = _uiState.value.intensityOffsetPct
    val newOffset = (current + 5).coerceAtMost(20)
    _uiState.value = _uiState.value.copy(intensityOffsetPct = newOffset)
  }

  fun adjustIntensityDown() {
    val current = _uiState.value.intensityOffsetPct
    val newOffset = (current - 5).coerceAtLeast(-20)
    _uiState.value = _uiState.value.copy(intensityOffsetPct = newOffset)
  }

  fun fineIntensityUp() {
    val current = _uiState.value.intensityOffsetPct
    val newOffset = (current + 1).coerceAtMost(20)
    _uiState.value = _uiState.value.copy(intensityOffsetPct = newOffset)
  }

  fun fineIntensityDown() {
    val current = _uiState.value.intensityOffsetPct
    val newOffset = (current - 1).coerceAtLeast(-20)
    _uiState.value = _uiState.value.copy(intensityOffsetPct = newOffset)
  }

  // Coach suggestion handlers
  fun acceptSuggestion(suggestionId: String) {
    viewModelScope.launch {
      val accepted = coachEngine.accept(suggestionId)
      (accepted?.action as? CoachAction.ExtendRecovery)?.let {
        extendCurrentRecovery(it.seconds)
      }
    }
  }

  /**
   * Lengthens the currently-active recovery segment by [deltaSec] and grows the
   * clock's timeline to match, so ERG keeps holding the easy target longer. No-op
   * unless the current segment is a RECOVERY. Also invoked by the manual button.
   */
  fun extendCurrentRecovery(deltaSec: Int = RECOVERY_EXTEND_STEP_SEC) {
    val idx = _uiState.value.segmentIndex
    val seg = segments.getOrNull(idx) ?: return
    if (seg.phase != SegmentPhase.RECOVERY) return
    segments = segments.toMutableList().also {
      it[idx] = seg.withDurationSec(seg.durationSec + deltaSec)
    }
    clock.extendTotalDuration(deltaSec)
    liveCoach.replan(segments)
    _uiState.value = _uiState.value.copy(segments = segments)
    updateFromClock()
  }

  fun rejectSuggestion(suggestionId: String) {
    viewModelScope.launch {
      coachEngine.reject(suggestionId)
    }
  }

  private fun handleControlTick(tick: WorkoutControlTick) {
    if (!tick.running || tick.targetRange == TargetRange(0, 0)) return

    if (!tick.ergEnabled) {
      if (wasErgEnabled) {
        controlNow()?.let { control ->
          ergWriteJob?.cancel()
          ergWriteJob = viewModelScope.launch { control.stopPause(stop = false) }
        }
      }
      wasErgEnabled = false
      return
    }

    wasErgEnabled = true

    val target = (tick.targetRange.low + tick.targetRange.high) / 2
    controlNow()?.let { control ->
      ergWriteJob?.cancel()
      ergWriteJob = viewModelScope.launch { control.setTargetPower(target.coerceIn(0, 2000)) }
    }
  }

  private fun controlNow(): FtmsControlManager? = ftmsControlManagerFlow.value

  /**
   * Sends a control command once the FTMS control point reports READY.
   *
   * Previously [start]/[resume] checked `status == READY` exactly once and
   * dropped the command if the trainer hadn't acked Request Control yet —
   * which, with the old indicate-armed-as-notify bug, was always. Now we
   * wait (bounded) for READY so Start/Resume actually reaches the trainer.
   */
  private fun sendControlWhenReady(action: suspend (FtmsControlManager) -> Unit) {
    val control = controlNow() ?: run {
      com.trainerloop.ble.BleLog.w("sendControlWhenReady: no control manager attached")
      return
    }
    viewModelScope.launch {
      if (control.status.value == com.trainerloop.ble.FtmsControlStatus.READY) {
        action(control)
        return@launch
      }
      val ready = withTimeoutOrNull(CONTROL_READY_TIMEOUT_MS) {
        control.status.filter { it == com.trainerloop.ble.FtmsControlStatus.READY }.first()
      }
      if (ready != null) {
        action(control)
      } else {
        com.trainerloop.ble.BleLog.w(
          "sendControlWhenReady: timed out after ${CONTROL_READY_TIMEOUT_MS}ms " +
            "waiting for READY; status=${control.status.value}"
        )
      }
    }
  }

  fun maybeEmitFinish() {
    val state = _uiState.value
    if (state.samples.isEmpty()) return
    _finishEvent.value = WorkoutFinishData(
      workoutId = workout.id,
      workoutName = workout.name,
      startTimeMs = sessionStartMs ?: (now() - state.elapsedSec * 1000L),
      samples = state.samples,
      coachJson = if (isRampTest) "" else liveCoach.sessionData().toJson(),
      completedNaturally = state.isComplete
    )
  }

  /**
   * Ends a ramp test when measured power stays below 50% of the current step
   * target for 5 consecutive seconds (exhaustion). Warmup is exempt.
   */
  private fun detectRampFailure() {
    if (!isRampTest) return
    val state = _uiState.value
    if (!state.isRunning) return
    val seg = segments.getOrNull(state.segmentIndex)
    if (seg?.phase != SegmentPhase.WORK) {
      rampBelowTargetSec = 0
      return
    }
    val target = (state.targetRange.low + state.targetRange.high) / 2
    if (target > 0 && state.currentPowerWatts < target * 0.5) {
      rampBelowTargetSec++
      if (rampBelowTargetSec >= RAMP_FAILURE_SEC) stop()
    } else {
      rampBelowTargetSec = 0
    }
  }

  private fun tickCoach() {
    if (isRampTest) return // a test is not a workout to be coached through
    val state = _uiState.value
    viewModelScope.launch {
      coachEngine.tick(
        CoachEngine.Input(
          activeSec = state.activeSec,
          isRunning = state.isRunning,
          isComplete = state.isComplete,
          hasPlan = true,
          sessionId = clock.sessionId.value,
          segmentIndex = state.segmentIndex,
          elapsedInSegmentSec = state.elapsedInSegmentSec,
          segmentStartSec = state.segmentStartSec,
          segmentEndSec = state.segmentEndSec,
          targetRange = state.targetRange,
          samples = state.samples,
          intensityOffsetPct = state.intensityOffsetPct,
          ergEnabled = state.isErgEnabled
        )
      )
    }
  }

  /**
   * Counts one second of "on target" per clock tick when the current power is
   * within ~5% of the interval's target band. Resets on interval change so the
   * bar reflects the *current* interval, not the whole session.
   */
  private fun tickLiveCoach() {
    if (isRampTest) return
    val state = _uiState.value
    val target = (state.targetRange.low + state.targetRange.high) / 2.0
    liveCoach.onTick(
      LiveCoach.TickInput(
        elapsedSec = state.elapsedSec,
        activeSec = state.activeSec,
        isRunning = state.isRunning,
        sample = TelemetrySample(
          timeSec = state.activeSec,
          powerWatts = state.currentPowerWatts,
          cadenceRpm = state.currentCadenceRpm,
          hrBpm = state.currentHrBpm,
          dropout = state.sensorDropout
        ),
        targetMidWatts = target,
        ergEnabled = state.isErgEnabled,
        modificationPending = state.pendingSuggestion != null
      )
    )
    // Informational cards auto-dismiss into the log after 12 s.
    val feedback = state.liveFeedback
    if (feedback != null && state.activeSec - feedback.timestampSec > FEEDBACK_AUTO_DISMISS_SEC) {
      liveCoach.dismissCurrent()
    }
    // A suggestion whose arbitration event expired unemitted would otherwise
    // block CoachEngine forever — treat it as declined.
    awaitingSuggestion?.let {
      if (state.pendingSuggestion == null &&
        state.activeSec - awaitingSuggestionSinceSec > SUGGESTION_ARBITRATION_TTL_SEC
      ) {
        awaitingSuggestion = null
        viewModelScope.launch { coachEngine.reject(it.id) }
      }
    }
  }

  private fun updateInZone() {
    val state = _uiState.value
    if (state.segmentIndex != inZoneSegmentIndex) {
      inZoneSegmentIndex = state.segmentIndex
      inZoneCount = 0
    }
    val range = state.targetRange
    if (state.isRunning && range.low > 0) {
      val onTarget = state.currentPowerWatts >= (range.low * 0.95).toInt() &&
        state.currentPowerWatts <= (range.high * 1.05).toInt()
      if (onTarget) inZoneCount++
    }
    _uiState.value = _uiState.value.copy(inZoneSec = inZoneCount)
  }

  private fun updateFromClock() {
    val elapsed = clock.elapsedSec.value
    val active = clock.activeSec.value
    val segIndex = WorkoutMath.segmentIndexAt(segments, elapsed)
    val segStart = segments.take(segIndex).sumOf { it.durationSec }
    val segEnd = segStart + (segments.getOrNull(segIndex)?.durationSec ?: 0)
    val elapsedInSeg = (elapsed - segStart).coerceIn(0, segments.getOrNull(segIndex)?.durationSec ?: 0)
    val offset = _uiState.value.intensityOffsetPct
    val target = WorkoutMath.targetRangeAt(segments, elapsed)
    val adjustedTarget = if (offset != 0) {
      val mid = (target.low + target.high) / 2
      val factor = 1.0 + offset / 100.0
      val adjusted = (mid * factor).toInt()
      TargetRange(adjusted, adjusted)
    } else target

    _uiState.value = _uiState.value.copy(
      elapsedSec = elapsed,
      activeSec = active,
      segmentIndex = segIndex,
      segmentStartSec = segStart,
      segmentEndSec = segEnd,
      elapsedInSegmentSec = elapsedInSeg,
      targetRange = adjustedTarget
    )
  }

  override fun onCleared() {
    clock.stop()
    clock.close()
    recorder.value?.stop()
    super.onCleared()
  }

  private fun emptySample() = TelemetrySample(
    timeSec = 0, powerWatts = 0, cadenceRpm = 0, hrBpm = 0, dropout = true
  )

  companion object {
    private fun defaultProfile(): CoachProfile = CoachProfile(
      id = "default",
      name = "Default",
      description = "Default coach",
      rules = CoachRules(
        targetAdherenceWarn = 95.0,
        targetAdherenceIntervene = 85.0,
        hrDriftWarn = 5.0,
        hrDriftIntervene = 10.0,
        cadenceVarianceWarn = 10.0,
        cadenceVarianceIntervene = 20.0,
        minElapsedSecondsForSuggestions = 10,
        cooldownSeconds = 10
      ),
      interventions = CoachInterventions(
        intensityAdjustStepPct = 5.0,
        intensityAdjustMinPct = -20.0,
        intensityAdjustMaxPct = 20.0,
        recoveryExtendStepSec = 30,
        recoveryExtendMaxSec = 120,
        allowSkipRemainingOnIntervals = false
      ),
      messages = CoachMessages(
        suggestions = mapOf(
          "adjust_intensity_up" to listOf("Increase intensity by {{percent}}%."),
          "adjust_intensity_up_rationale" to listOf("Metrics indicate you can handle more intensity."),
          "adjust_intensity_down" to listOf("Decrease intensity by {{percent}}%."),
          "adjust_intensity_down_rationale" to listOf("Fatigue indicators suggest reducing intensity."),
          "extend_recovery" to listOf("Extend recovery by {{seconds}} seconds."),
          "extend_recovery_rationale" to listOf("Recovery metrics indicate more time needed."),
          "skip_remaining_on_intervals" to listOf("Skip remaining intervals."),
          "skip_remaining_on_intervals_rationale" to listOf("Multiple indicators suggest terminating the session.")
        ),
        completion = listOf("Session complete.")
      )
    )

    private const val CONTROL_READY_TIMEOUT_MS = 5_000L
    private const val FEEDBACK_AUTO_DISMISS_SEC = 12
    private const val SUGGESTION_ARBITRATION_TTL_SEC = 180
    private const val RAMP_FAILURE_SEC = 5
    private const val RECOVERY_EXTEND_STEP_SEC = 30
  }
}
