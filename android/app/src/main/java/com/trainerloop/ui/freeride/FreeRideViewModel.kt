package com.trainerloop.ui.freeride

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainerloop.ble.ClickShift
import com.trainerloop.ble.FtmsControlManager
import com.trainerloop.ble.FtmsControlStatus
import com.trainerloop.ble.FtmsManager
import com.trainerloop.ble.HrManager
import com.trainerloop.ble.ZwiftClickManager
import com.trainerloop.data.model.Route
import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.data.model.UserProfile
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.domain.TelemetryRecorder
import com.trainerloop.domain.WorkoutClock
import com.trainerloop.domain.sim.FreeRideTracker
import com.trainerloop.domain.sim.PhysicsParams
import com.trainerloop.ui.workout.WorkoutFinishData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class FreeRideUiState(
  val isRunning: Boolean = false,
  val elapsedSec: Int = 0,
  val gear: Int = com.trainerloop.domain.sim.VirtualDrivetrain.START_GEAR,
  val speedKph: Double = 0.0,
  val gradePercent: Double = 0.0,
  val distanceM: Double = 0.0,
  val remainingM: Double = 0.0,
  val targetPowerWatts: Int = 0,
  val currentPowerWatts: Int = 0,
  val currentCadenceRpm: Int = 0,
  val currentHrBpm: Int = 0,
  val routeComplete: Boolean = false,
  val samples: List<TelemetrySample> = emptyList()
)

/**
 * Free-ride session: [WorkoutClock] paces 1 Hz ticks (single open-ended
 * segment), [TelemetryRecorder] drives the [FreeRideTracker] via its stamper
 * hook, and this ViewModel turns tracker targets into gated ERG writes.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FreeRideViewModel(
  val route: Route,
  val routeId: String,
  private val ftmsManagerFlow: StateFlow<FtmsManager?> = MutableStateFlow(null),
  private val hrManagerFlow: StateFlow<HrManager?> = MutableStateFlow(null),
  private val ftmsControlManagerFlow: StateFlow<FtmsControlManager?> = MutableStateFlow(null),
  private val clickManagerFlow: StateFlow<ZwiftClickManager?> = MutableStateFlow(null),
  private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
  userProfile: UserProfile = UserProfile(),
  private val now: () -> Long = System::currentTimeMillis
) : ViewModel() {

  // ponytail: no fixed route duration — a 12 h cap stands in for "open-ended"
  private val clock = WorkoutClock(
    listOf(
      WorkoutSegment.FreeRide(
        id = "free-ride", durationSec = MAX_RIDE_SEC, label = route.name,
        phase = SegmentPhase.WORK
      )
    ),
    dispatcher
  )

  private val tracker = FreeRideTracker(
    route = route,
    physics = PhysicsParams(
      riderKg = userProfile.weightKg,
      bikeKg = userProfile.bikeWeightKg,
      crr = userProfile.rollingResistanceCrr,
      cda = userProfile.dragAreaCda
    ),
    difficulty = userProfile.trainerDifficultyPct / 100.0
  )

  private val _uiState = MutableStateFlow(FreeRideUiState(remainingM = route.totalDistanceM))
  val uiState: StateFlow<FreeRideUiState> = _uiState.asStateFlow()

  private val _finishEvent = MutableStateFlow<WorkoutFinishData?>(null)
  val finishEvent: StateFlow<WorkoutFinishData?> = _finishEvent.asStateFlow()

  private val recorder = MutableStateFlow<TelemetryRecorder?>(null)

  private var lastSentWatts = -1
  private var lastSentAtSec = -10
  private var sessionStartMs: Long? = null

  init {
    viewModelScope.launch {
      combine(ftmsManagerFlow, hrManagerFlow) { ftms, hr -> ftms to hr }
        .distinctUntilChanged()
        .collect { (ftms, hr) ->
          val previous = recorder.value
          recorder.value = if (ftms != null) {
            TelemetryRecorder(clock, ftms, hr, dispatcher, tracker)
              .also { it.startCollecting() }
          } else null
          previous?.stop()
        }
    }

    viewModelScope.launch {
      recorder
        .flatMapLatest { r -> r?.latest ?: flowOf(null) }
        .filterNotNull()
        .collect { sample ->
          val point = tracker.latest.value
          _uiState.value = _uiState.value.copy(
            currentPowerWatts = sample.powerWatts,
            currentCadenceRpm = sample.cadenceRpm,
            currentHrBpm = sample.hrBpm,
            gear = tracker.drivetrain.gear,
            speedKph = point?.speedKph ?: 0.0,
            gradePercent = point?.gradePercent ?: 0.0,
            distanceM = point?.distanceM ?: 0.0,
            remainingM = ((point?.let { route.totalDistanceM - it.distanceM })
              ?: route.totalDistanceM).coerceAtLeast(0.0),
            targetPowerWatts = point?.targetPowerWatts ?: 0,
            routeComplete = point?.routeComplete ?: false
          )
          if (_uiState.value.isRunning && point != null) {
            maybeSendTarget(point.targetPowerWatts, sample.timeSec)
          }
        }
    }

    viewModelScope.launch {
      recorder
        .flatMapLatest { r -> r?.samples ?: flowOf(emptyList()) }
        .collect { samples -> _uiState.value = _uiState.value.copy(samples = samples) }
    }

    viewModelScope.launch {
      clock.elapsedSec.collect { _uiState.value = _uiState.value.copy(elapsedSec = it) }
    }
    viewModelScope.launch {
      clock.isRunning.collect { _uiState.value = _uiState.value.copy(isRunning = it) }
    }

    // Zwift Click: third shift input beside the on-screen buttons and volume
    // keys. Same entry points, so downstream (drivetrain, ERG) is untouched.
    viewModelScope.launch {
      clickManagerFlow
        .flatMapLatest { manager -> manager?.shiftEvents ?: emptyFlow() }
        .collect { shift ->
          when (shift) {
            ClickShift.UP -> shiftUp()
            ClickShift.DOWN -> shiftDown()
          }
        }
    }
  }

  fun start() {
    if (sessionStartMs == null) sessionStartMs = now()
    clock.start()
    sendControlWhenReady { it.startResume() }
  }

  fun pause() {
    clock.pause()
    viewModelScope.launch { ftmsControlManagerFlow.value?.stopPause(stop = false) }
  }

  fun resume() {
    clock.resume()
    sendControlWhenReady { it.startResume() }
  }

  fun stop() {
    clock.stop()
    viewModelScope.launch { ftmsControlManagerFlow.value?.stopPause(stop = true) }
    val samples = _uiState.value.samples
    if (samples.isNotEmpty()) {
      _finishEvent.value = WorkoutFinishData(
        workoutId = "gpx-free-ride",
        workoutName = route.name ?: "GPX Ride",
        startTimeMs = sessionStartMs ?:
          (now() - _uiState.value.elapsedSec * 1000L),
        samples = samples,
        completedNaturally = _uiState.value.routeComplete
      )
    }
    sessionStartMs = null
  }

  fun shiftUp() {
    tracker.drivetrain.shiftUp()
    _uiState.value = _uiState.value.copy(gear = tracker.drivetrain.gear)
  }

  fun shiftDown() {
    tracker.drivetrain.shiftDown()
    _uiState.value = _uiState.value.copy(gear = tracker.drivetrain.gear)
  }

  fun consumeFinishEvent() {
    _finishEvent.value = null
  }

  /** Re-send only on ≥ 2 W change or 2 s elapsed — no control-point spam. */
  private fun maybeSendTarget(watts: Int, timeSec: Int) {
    if (kotlin.math.abs(watts - lastSentWatts) < TARGET_MIN_DELTA_W &&
      timeSec - lastSentAtSec < TARGET_RESEND_SEC
    ) return
    lastSentWatts = watts
    lastSentAtSec = timeSec
    viewModelScope.launch { ftmsControlManagerFlow.value?.setTargetPower(watts) }
  }

  private fun sendControlWhenReady(action: suspend (FtmsControlManager) -> Unit) {
    val control = ftmsControlManagerFlow.value ?: return
    viewModelScope.launch {
      if (control.status.value == FtmsControlStatus.READY) {
        action(control)
        return@launch
      }
      val ready = withTimeoutOrNull(CONTROL_READY_TIMEOUT_MS) {
        control.status.filter { it == FtmsControlStatus.READY }.first()
      }
      if (ready != null) action(control)
    }
  }

  override fun onCleared() {
    clock.stop()
    clock.close()
    recorder.value?.stop()
    super.onCleared()
  }

  companion object {
    private const val MAX_RIDE_SEC = 12 * 3600
    private const val TARGET_MIN_DELTA_W = 2
    private const val TARGET_RESEND_SEC = 2
    private const val CONTROL_READY_TIMEOUT_MS = 5_000L
  }
}
