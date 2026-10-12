package com.trainerloop.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainerloop.ble.FtmsControlManager
import com.trainerloop.ble.FtmsManager
import com.trainerloop.ble.HrManager
import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.data.model.UserProfile
import com.trainerloop.data.model.Workout
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.data.model.withDurationSec
import com.trainerloop.domain.RampTest
import com.trainerloop.domain.TelemetryRecorder
import com.trainerloop.domain.WorkoutClock
import com.trainerloop.domain.WorkoutMath
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import com.trainerloop.ble.FtmsControlStatus
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
  val completedNaturally: Boolean = false
)

private data class WorkoutControlTick(
  val running: Boolean,
  val complete: Boolean,
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
  private val now: () -> Long = System::currentTimeMillis
) : ViewModel() {

  private val clock = WorkoutClock(workout.segments, dispatcher)
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
    WorkoutUiState(
      segments = workout.segments,
      elevationProfile = route?.expectedAltitudeM,
      intensityOffsetPct = userProfile.ergBiasPct.coerceIn(-20, 20)
    )
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

  private var retainedSamples: List<TelemetrySample> = emptyList()
  private var sessionStartMs: Long? = null
  private var stopping = false


  // Time-in-zone bookkeeping: reset whenever the interval changes.
  private var inZoneSegmentIndex: Int = -1
  private var inZoneCount: Int = 0

  init {
    // Populate the first interval target before the ride starts. The clock is
    // still at zero, so this does not start the workout or send ERG control.
    updateFromClock()

    // (Re)create the recorder whenever the manager references change.
    viewModelScope.launch {
      combine(ftmsManagerFlow, hrManagerFlow) { ftms, hr -> ftms to hr }
        .distinctUntilChanged()
        .collect { (ftms, hr) ->
          val previous = recorder.value
          previous?.flush()
          val carried = previous?.samples?.value ?: retainedSamples
          retainedSamples = carried
          previous?.stop()
          val next = if (ftms != null) {
            TelemetryRecorder(
              clock, ftms, hr, dispatcher, virtualRide, initialSamples = carried
            )
              .also { it.startCollecting() }
          } else null
          recorder.value = next
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
            currentHrBpm = sample.hrBpm,
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
        .flatMapLatest { r -> r?.samples ?: flowOf(retainedSamples) }
        .collect { samples -> _uiState.value = _uiState.value.copy(samples = samples) }
    }

    // Fast-path HR: bypass the recorder entirely so the displayed HR
    // updates the instant an HR packet arrives instead of waiting for the
    // next 1 Hz clock tick.
    viewModelScope.launch {
      hrManagerFlow
        .flatMapLatest { hr -> hr?.heartRate?.map { it?.value } ?: flowOf(null) }
        .collect { bpm -> _uiState.value = _uiState.value.copy(currentHrBpm = bpm ?: 0) }
    }

    viewModelScope.launch {
      clock.elapsedSec.collect { updateFromClock() }
    }
    viewModelScope.launch {
      clock.activeSec.collect { activeSec ->
        updateFromClock()
        if (activeSec > 0) {
          updateInZone()
          detectRampFailure()
        }
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
          maybeEmitFinish()
        }
      }
    }
    viewModelScope.launch {
      var lastControl: FtmsControlManager? = null
      var lastRunning = false
      var lastErg = false
      val controls = ftmsControlManagerFlow.flatMapLatest { control ->
        control?.status?.map { status -> control to status }
          ?: flowOf(null to FtmsControlStatus.IDLE)
      }
      combine(
        _uiState.map { WorkoutControlTick(it.isRunning, it.isComplete || stopping, it.isErgEnabled, it.targetRange) }
          .distinctUntilChanged(),
        controls
      ) { tick, control -> tick to control }.collectLatest { (tick, controlState) ->
        val (control, status) = controlState
        if (control == null || status != FtmsControlStatus.READY) {
          lastControl = null
          lastRunning = false
          return@collectLatest
        }
        if (!tick.running || tick.complete) {
          if (tick.complete || lastRunning) control.stopPause(stop = tick.complete)
          lastRunning = false
          return@collectLatest
        }
        if (tick.ergEnabled) {
          if (lastControl !== control || !lastRunning || !lastErg) control.startResume()
          // Zero targets release the previous interval's load during untargeted segments.
          val target = (tick.targetRange.low + tick.targetRange.high) / 2
          control.setTargetPower(target.coerceIn(0, 2000))
        } else if (lastControl !== control || !lastRunning || lastErg) {
          control.stopPause(stop = false)
        }
        lastControl = control
        lastRunning = true
        lastErg = tick.ergEnabled
      }
    }
  }

  fun start() {
    stopping = false
    if (sessionStartMs == null) sessionStartMs = now()
    clock.start()
    updateFromClock()
  }

  fun pause() {
    clock.pause()
  }

  fun resume() {
    stopping = false
    clock.resume()
    updateFromClock()
  }

  fun stop() {
    recorder.value?.flush()
    stopping = true
    clock.stop()
    maybeEmitFinish()
    sessionStartMs = null
    retainedSamples = emptyList()
    recorder.value?.reset(clock.sessionId.value)
    _uiState.value = _uiState.value.copy(
      intensityOffsetPct = userProfile.ergBiasPct.coerceIn(-20, 20),
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
    setIntensityOffset(_uiState.value.intensityOffsetPct + 5)
  }

  fun adjustIntensityDown() {
    setIntensityOffset(_uiState.value.intensityOffsetPct - 5)
  }

  fun fineIntensityUp() {
    setIntensityOffset(_uiState.value.intensityOffsetPct + 1)
  }

  fun fineIntensityDown() {
    setIntensityOffset(_uiState.value.intensityOffsetPct - 1)
  }

  private fun setIntensityOffset(pct: Int) {
    _uiState.value = _uiState.value.copy(intensityOffsetPct = pct.coerceIn(-20, 20))
    updateFromClock()
  }

  /**
   * Lengthens the currently-active recovery segment by [deltaSec] and grows the
   * clock's timeline to match, so ERG keeps holding the easy target longer. No-op
   * unless the current segment is a RECOVERY. Invoked by the manual recovery button.
   */
  fun extendCurrentRecovery(deltaSec: Int = RECOVERY_EXTEND_STEP_SEC) {
    val idx = _uiState.value.segmentIndex
    val seg = segments.getOrNull(idx) ?: return
    if (seg.phase != SegmentPhase.RECOVERY) return
    segments = segments.toMutableList().also {
      it[idx] = seg.withDurationSec(seg.durationSec + deltaSec)
    }
    clock.extendTotalDuration(deltaSec)
    _uiState.value = _uiState.value.copy(segments = segments)
    updateFromClock()
  }

  fun maybeEmitFinish() {
    val activeRecorder = recorder.value
    activeRecorder?.flush()
    val state = _uiState.value
    val samples = activeRecorder?.samples?.value ?: state.samples
    _finishEvent.value = WorkoutFinishData(
      workoutId = workout.id,
      workoutName = workout.name,
      startTimeMs = sessionStartMs ?: (now() - state.activeSec * 1000L),
      samples = samples,
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
    if (!state.isRunning || state.sensorDropout) {
      rampBelowTargetSec = 0
      return
    }
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

  private fun updateInZone() {
    val state = _uiState.value
    if (state.segmentIndex != inZoneSegmentIndex) {
      inZoneSegmentIndex = state.segmentIndex
      inZoneCount = 0
    }
    val range = state.targetRange
    if (state.isRunning && !state.sensorDropout && range.low > 0) {
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
      val factor = 1.0 + offset / 100.0
      TargetRange((target.low * factor).toInt(), (target.high * factor).toInt())
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
    private const val RAMP_FAILURE_SEC = 5
    private const val RECOVERY_EXTEND_STEP_SEC = 30
  }
}
