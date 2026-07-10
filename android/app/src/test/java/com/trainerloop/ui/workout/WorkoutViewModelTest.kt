package com.trainerloop.ui.workout

import android.bluetooth.BluetoothDevice
import com.trainerloop.ble.FtmsControlManager
import com.trainerloop.ble.FtmsManager
import com.trainerloop.ble.HrManager
import com.trainerloop.ble.model.IndoorBikeData
import com.trainerloop.ble.model.Stamped
import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.CoachAction
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.UserProfile
import com.trainerloop.data.model.Workout
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.data.model.WorkoutSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `emitted telemetry updates current power cadence and hr`() = runTest(testDispatcher) {
    val ftmsData = MutableStateFlow<Stamped<IndoorBikeData>?>(null)
    val heartRate = MutableStateFlow<Stamped<Int>?>(null)
    val ftms = mockFtmsManager(data = ftmsData)
    val hr = mockHrManager(heartRate = heartRate)
    val ftmsFlow = MutableStateFlow<FtmsManager?>(ftms)
    val hrFlow = MutableStateFlow<HrManager?>(hr)

    val viewModel = WorkoutViewModel(
      workout = sampleWorkout(),
      ftmsManagerFlow = ftmsFlow,
      hrManagerFlow = hrFlow,
      dispatcher = testDispatcher
    )

    ftmsData.value = stamp(IndoorBikeData(
      powerWatts = 250,
      cadenceRpm = 90.0,
      speedKph = null,
      resistanceLevel = null,
      averagePower = null,
      averageSpeed = null,
      totalDistanceMeters = null,
      heartRateBpm = null,
      elapsedTimeSec = null,
      remainingTimeSec = null
    ))
    heartRate.value = stamp(145)

    runCurrent()

    val state = viewModel.uiState.value
    assertEquals("power", 250, state.currentPowerWatts)
    assertEquals("cadence", 90, state.currentCadenceRpm)
    assertEquals("hr", 145, state.currentHrBpm)
  }

  @Test
  fun `dropout keeps last known values`() = runTest(testDispatcher) {
    val ftmsData = MutableStateFlow<Stamped<IndoorBikeData>?>(null)
    val heartRate = MutableStateFlow<Stamped<Int>?>(null)
    val ftms = mockFtmsManager(data = ftmsData)
    val hr = mockHrManager(heartRate = heartRate)
    val ftmsFlow = MutableStateFlow<FtmsManager?>(ftms)
    val hrFlow = MutableStateFlow<HrManager?>(hr)

    val viewModel = WorkoutViewModel(
      workout = sampleWorkout(),
      ftmsManagerFlow = ftmsFlow,
      hrManagerFlow = hrFlow,
      dispatcher = testDispatcher
    )

    ftmsData.value = stamp(IndoorBikeData(
      powerWatts = 200,
      cadenceRpm = 85.0,
      speedKph = null,
      resistanceLevel = null,
      averagePower = null,
      averageSpeed = null,
      totalDistanceMeters = null,
      heartRateBpm = null,
      elapsedTimeSec = null,
      remainingTimeSec = null
    ))
    heartRate.value = stamp(140)

    ftmsData.value = null

    runCurrent()

    val state = viewModel.uiState.value
    assertEquals("power", 200, state.currentPowerWatts)
    assertEquals("cadence", 85, state.currentCadenceRpm)
    assertEquals("hr", 140, state.currentHrBpm)
  }

  @Test
  fun `no managers keeps metrics at zero`() = runTest(testDispatcher) {
    val viewModel = WorkoutViewModel(workout = sampleWorkout())

    val state = viewModel.uiState.value
    assertEquals(0, state.currentPowerWatts)
    assertEquals(0, state.currentCadenceRpm)
    assertEquals(0, state.currentHrBpm)
  }

  @Test
  fun `pre-start state exposes the first interval target`() = runTest(testDispatcher) {
    val viewModel = WorkoutViewModel(workout = sampleWorkout(), dispatcher = testDispatcher)

    val state = viewModel.uiState.value
    assertFalse(state.isRunning)
    assertEquals(TargetRange(200, 220), state.targetRange)
  }

  /**
   * Regression test for the watts-not-showing bug. The original bug was that
   * the ViewModel captured the manager reference at construction time, so
   * a manager that attached *after* the screen was composed left the
   * recorder null and power frozen at 0.
   */
  @Test
  fun `manager attaching after VM creation wires into the recorder`() = runTest(testDispatcher) {
    // Simulate the app starting with no manager (e.g. user navigated to
    // the workout screen before connecting the trainer).
    val ftmsFlow = MutableStateFlow<FtmsManager?>(null)
    val hrFlow = MutableStateFlow<HrManager?>(null)

    val viewModel = WorkoutViewModel(
      workout = sampleWorkout(),
      ftmsManagerFlow = ftmsFlow,
      hrManagerFlow = hrFlow,
      dispatcher = testDispatcher
    )

    // Pretend the user just got on the bike and the trainer reports
    // 230 W — but only AFTER the ViewModel was created.
    val ftmsData = MutableStateFlow<Stamped<IndoorBikeData>?>(null)
    val heartRate = MutableStateFlow<Stamped<Int>?>(null)
    val ftms = mockFtmsManager(data = ftmsData)
    val hr = mockHrManager(heartRate = heartRate)
    ftmsFlow.value = ftms
    hrFlow.value = hr

    runCurrent()

    // Now emit some real data.
    ftmsData.value = stamp(IndoorBikeData(
      powerWatts = 230,
      cadenceRpm = 88.0,
      speedKph = 32.0,
      resistanceLevel = null,
      averagePower = null,
      averageSpeed = null,
      totalDistanceMeters = null,
      heartRateBpm = null,
      elapsedTimeSec = null,
      remainingTimeSec = null
    ))
    heartRate.value = stamp(152)

    runCurrent()

    val state = viewModel.uiState.value
    assertEquals("power should reach UI after late attach", 230, state.currentPowerWatts)
    assertEquals("cadence should reach UI after late attach", 88, state.currentCadenceRpm)
    assertEquals("hr should reach UI after late attach", 152, state.currentHrBpm)
  }

  @Test
  fun `manager swap carries buffered samples into the replacement recorder`() =
    runTest(testDispatcher) {
      val firstData = MutableStateFlow<Stamped<IndoorBikeData>?>(
        stamp(IndoorBikeData(
          powerWatts = 200,
          cadenceRpm = 85.0,
          speedKph = null,
          resistanceLevel = null,
          averagePower = null,
          averageSpeed = null,
          totalDistanceMeters = null,
          heartRateBpm = null,
          elapsedTimeSec = null,
          remainingTimeSec = null
        ))
      )
      val secondData = MutableStateFlow<Stamped<IndoorBikeData>?>(
        stamp(IndoorBikeData(
          powerWatts = 210,
          cadenceRpm = 86.0,
          speedKph = null,
          resistanceLevel = null,
          averagePower = null,
          averageSpeed = null,
          totalDistanceMeters = null,
          heartRateBpm = null,
          elapsedTimeSec = null,
          remainingTimeSec = null
        ))
      )
      val firstManager = mockFtmsManager(data = firstData)
      val secondManager = mockFtmsManager(data = secondData)
      val ftmsFlow = MutableStateFlow<FtmsManager?>(firstManager)
      val viewModel = WorkoutViewModel(
        workout = sampleWorkout(),
        ftmsManagerFlow = ftmsFlow,
        dispatcher = testDispatcher
      )

      runCurrent()
      viewModel.start()
      advanceTimeBy(3_000)
      runCurrent()

      assertEquals(0, viewModel.uiState.value.samples.size)

      ftmsFlow.value = secondManager
      runCurrent()

      assertEquals(3, viewModel.uiState.value.samples.size)
    }

  /**
   * Fast-path HR: the displayed HR should follow HR-strap notifications
   * even when the 1 Hz clock has not advanced.
   */
  @Test
  fun `hr updates immediately when strap notifies (fast path)`() = runTest(testDispatcher) {
    val ftmsData = MutableStateFlow<Stamped<IndoorBikeData>?>(null)
    val heartRate = MutableStateFlow<Stamped<Int>?>(null)
    val ftms = mockFtmsManager(data = ftmsData)
    val hr = mockHrManager(heartRate = heartRate)
    val ftmsFlow = MutableStateFlow<FtmsManager?>(ftms)
    val hrFlow = MutableStateFlow<HrManager?>(hr)

    val viewModel = WorkoutViewModel(
      workout = sampleWorkout(),
      ftmsManagerFlow = ftmsFlow,
      hrManagerFlow = hrFlow,
      dispatcher = testDispatcher
    )

    // Pretend the clock hasn't advanced and the FTMS data is silent.
    // The HR strap notifies at 1 Hz; the UI should reflect it without
    // waiting for the clock tick.
    runCurrent()
    assertEquals(0, viewModel.uiState.value.currentHrBpm)

    heartRate.value = stamp(138)
    runCurrent()
    assertEquals(138, viewModel.uiState.value.currentHrBpm)

    heartRate.value = stamp(142)
    runCurrent()
    assertEquals(142, viewModel.uiState.value.currentHrBpm)
  }

  @Test
  fun `intensity offset adjusts target range`() = runTest(testDispatcher) {
    val viewModel = WorkoutViewModel(workout = sampleWorkout())

    viewModel.adjustIntensityUp()

    val state = viewModel.uiState.value
    assertEquals(5, state.intensityOffsetPct)
    assertTrue(state.targetRange.low > 0)
  }

  @Test
  fun `initial intensity offset honors saved erg bias`() = runTest(testDispatcher) {
    val viewModel = WorkoutViewModel(
      workout = sampleWorkout(),
      dispatcher = testDispatcher,
      userProfile = UserProfile(ergBiasPct = 5)
    )

    assertEquals(5, viewModel.uiState.value.intensityOffsetPct)
  }

  @Test
  fun `stop resets intensity offset to saved erg bias`() = runTest(testDispatcher) {
    val viewModel = WorkoutViewModel(
      workout = sampleWorkout(),
      dispatcher = testDispatcher,
      userProfile = UserProfile(ergBiasPct = 5)
    )

    viewModel.adjustIntensityUp()
    viewModel.stop()

    assertEquals(5, viewModel.uiState.value.intensityOffsetPct)
  }

  @Test
  fun `adjust intensity up action raises the offset`() = runTest(testDispatcher) {
    val viewModel = WorkoutViewModel(workout = sampleWorkout(), dispatcher = testDispatcher)

    viewModel.applyCoachAction(CoachAction.AdjustIntensityUp(percent = 5))

    assertEquals(5, viewModel.uiState.value.intensityOffsetPct)
  }

  @Test
  fun `adjust intensity down clamps at -20`() = runTest(testDispatcher) {
    val viewModel = WorkoutViewModel(workout = sampleWorkout(), dispatcher = testDispatcher)

    repeat(6) { viewModel.applyCoachAction(CoachAction.AdjustIntensityDown(percent = 5)) }

    assertEquals(-20, viewModel.uiState.value.intensityOffsetPct)
  }

  @Test
  fun `skip remaining seeks to the first cooldown segment`() = runTest(testDispatcher) {
    val viewModel = WorkoutViewModel(workout = intervalWorkout(), dispatcher = testDispatcher)

    viewModel.start()
    advanceTimeBy(10_000)
    viewModel.applyCoachAction(CoachAction.SkipRemainingOnIntervals)
    advanceTimeBy(1_000)

    assertEquals(2, viewModel.uiState.value.segmentIndex)
  }

  @Test
  fun `stop with samples emits finish event`() = runTest(testDispatcher) {
    val ftmsData = MutableStateFlow<Stamped<IndoorBikeData>?>(null)
    val ftms = mockFtmsManager(data = ftmsData)
    val ftmsFlow = MutableStateFlow<FtmsManager?>(ftms)

    val viewModel = WorkoutViewModel(
      workout = sampleWorkout(),
      ftmsManagerFlow = ftmsFlow,
      dispatcher = testDispatcher
    )

    ftmsData.value = stamp(IndoorBikeData(
      powerWatts = 200,
      cadenceRpm = 85.0,
      speedKph = null,
      resistanceLevel = null,
      averagePower = null,
      averageSpeed = null,
      totalDistanceMeters = null,
      heartRateBpm = null,
      elapsedTimeSec = null,
      remainingTimeSec = null
    ))
    runCurrent()
    // Fake a recorded sample so stop() has something to emit.
    viewModel.start()
    advanceTimeBy(1_000)
    runCurrent()

    viewModel.stop()

    assertTrue("finishEvent should carry samples", viewModel.finishEvent.value?.samples?.isNotEmpty() == true)
    assertFalse(viewModel.finishEvent.value?.completedNaturally == true)
  }

  @Test
  fun `stop with no samples leaves finish event null`() = runTest(testDispatcher) {
    val viewModel = WorkoutViewModel(workout = sampleWorkout(), dispatcher = testDispatcher)

    viewModel.stop()

    assertEquals(null, viewModel.finishEvent.value)
  }

  @Test
  fun `toggle erg disables erg`() = runTest(testDispatcher) {
    val viewModel = WorkoutViewModel(workout = sampleWorkout())

    assertTrue(viewModel.uiState.value.isErgEnabled)

    viewModel.toggleErg()

    assertFalse(viewModel.uiState.value.isErgEnabled)
  }

  private fun mockFtmsManager(
    data: MutableStateFlow<Stamped<IndoorBikeData>?> = MutableStateFlow(null)
  ): FtmsManager {
    val device = mockk<BluetoothDevice>(relaxed = true)
    val manager = mockk<FtmsManager>(relaxed = true)
    every { manager.device } returns device
    every { manager.data } returns data
    every { manager.isConnected } returns MutableStateFlow(false)
    every { manager.batteryLevel } returns MutableStateFlow(null)
    every { manager.manufacturer } returns MutableStateFlow(null)
    every { manager.model } returns MutableStateFlow(null)
    return manager
  }

  private fun mockHrManager(
    heartRate: MutableStateFlow<Stamped<Int>?> = MutableStateFlow(null)
  ): HrManager {
    val device = mockk<BluetoothDevice>(relaxed = true)
    val manager = mockk<HrManager>(relaxed = true)
    every { manager.device } returns device
    every { manager.heartRate } returns heartRate
    every { manager.isConnected } returns MutableStateFlow(false)
    return manager
  }

  @Test
  fun `extendCurrentRecovery grows the active recovery segment and total`() = runTest(testDispatcher) {
    val viewModel = WorkoutViewModel(workout = recoveryWorkout(), dispatcher = testDispatcher)
    viewModel.start()
    runCurrent()

    // Segment 0 is RECOVERY at elapsed 0.
    viewModel.extendCurrentRecovery(30)
    runCurrent()

    val segs = viewModel.uiState.value.segments
    assertEquals("recovery grown", 90, segs[0].durationSec)
    assertEquals("total grown", 90 + 60, segs.sumOf { it.durationSec })
  }

  @Test
  fun `extendCurrentRecovery is a no-op outside a recovery segment`() = runTest(testDispatcher) {
    val viewModel = WorkoutViewModel(workout = sampleWorkout(), dispatcher = testDispatcher)
    viewModel.start()
    runCurrent()

    viewModel.extendCurrentRecovery(30)
    runCurrent()

    assertEquals("unchanged", 60, viewModel.uiState.value.segments[0].durationSec)
  }

  @Test
  fun `ramp test ends after 5s below half the step target`() = runTest(testDispatcher) {
    val ftmsData = MutableStateFlow<Stamped<IndoorBikeData>?>(null)
    val ftms = mockFtmsManager(data = ftmsData)
    val viewModel = WorkoutViewModel(
      workout = com.trainerloop.domain.RampTest.generate(250),
      ftmsManagerFlow = MutableStateFlow<FtmsManager?>(ftms),
      dispatcher = testDispatcher
    )
    ftmsData.value = stamp(lowPowerData(30)) // first step targets 100W; 30 < 50
    runCurrent()
    viewModel.start()
    viewModel.seek(300) // skip warmup into the first WORK step
    runCurrent()

    advanceTimeBy(6_000)
    runCurrent()

    assertFalse("test should auto-stop", viewModel.uiState.value.isRunning)
    assertTrue("finish emitted", viewModel.finishEvent.value != null)
  }

  @Test
  fun `ramp test warmup is exempt from failure detection`() = runTest(testDispatcher) {
    val ftmsData = MutableStateFlow<Stamped<IndoorBikeData>?>(null)
    val ftms = mockFtmsManager(data = ftmsData)
    val viewModel = WorkoutViewModel(
      workout = com.trainerloop.domain.RampTest.generate(250),
      ftmsManagerFlow = MutableStateFlow<FtmsManager?>(ftms),
      dispatcher = testDispatcher
    )
    ftmsData.value = stamp(lowPowerData(10))
    runCurrent()
    viewModel.start()
    runCurrent()

    advanceTimeBy(10_000) // still in the 300s warmup
    runCurrent()

    assertTrue("warmup keeps running", viewModel.uiState.value.isRunning)
  }

  @Test
  fun `non-ramp workouts never auto-stop on low power`() = runTest(testDispatcher) {
    val ftmsData = MutableStateFlow<Stamped<IndoorBikeData>?>(null)
    val ftms = mockFtmsManager(data = ftmsData)
    val viewModel = WorkoutViewModel(
      workout = sampleWorkout(), // WORK step at 200-220W
      ftmsManagerFlow = MutableStateFlow<FtmsManager?>(ftms),
      dispatcher = testDispatcher
    )
    ftmsData.value = stamp(lowPowerData(10))
    runCurrent()
    viewModel.start()
    runCurrent()

    advanceTimeBy(10_000)
    runCurrent()

    assertTrue("normal workout keeps running", viewModel.uiState.value.isRunning)
  }

  private fun lowPowerData(watts: Int) = IndoorBikeData(
    powerWatts = watts,
    cadenceRpm = 60.0,
    speedKph = null,
    resistanceLevel = null,
    averagePower = null,
    averageSpeed = null,
    totalDistanceMeters = null,
    heartRateBpm = null,
    elapsedTimeSec = null,
    remainingTimeSec = null
  )

  private fun <T> stamp(value: T): Stamped<T> =
    Stamped(value, android.os.SystemClock.elapsedRealtime())

  private fun recoveryWorkout(): Workout = Workout(
    id = "rec-workout",
    name = "Recovery Test",
    description = null,
    source = WorkoutSource.MANUAL,
    segments = listOf(
      WorkoutSegment.Step(
        id = "r1",
        durationSec = 60,
        label = "Ease",
        phase = SegmentPhase.RECOVERY,
        isWork = false,
        targetRange = TargetRange(low = 100, high = 110)
      ),
      WorkoutSegment.Step(
        id = "w1",
        durationSec = 60,
        label = "Work",
        phase = SegmentPhase.WORK,
        isWork = true,
        targetRange = TargetRange(low = 200, high = 220)
      )
    )
  )

  private fun intervalWorkout(): Workout = Workout(
    id = "interval-workout",
    name = "Interval Test",
    description = null,
    source = WorkoutSource.MANUAL,
    segments = listOf(
      WorkoutSegment.Step(
        id = "w1",
        durationSec = 300,
        label = "Work 1",
        phase = SegmentPhase.WORK,
        isWork = true,
        targetRange = TargetRange(low = 200, high = 220)
      ),
      WorkoutSegment.Step(
        id = "w2",
        durationSec = 300,
        label = "Work 2",
        phase = SegmentPhase.WORK,
        isWork = true,
        targetRange = TargetRange(low = 200, high = 220)
      ),
      WorkoutSegment.Step(
        id = "c1",
        durationSec = 120,
        label = "Cooldown",
        phase = SegmentPhase.COOLDOWN,
        isWork = false,
        targetRange = TargetRange(low = 100, high = 110)
      )
    )
  )

  private fun sampleWorkout(): Workout = Workout(
    id = "test-workout",
    name = "Test Workout",
    description = null,
    source = WorkoutSource.MANUAL,
    segments = listOf(
      WorkoutSegment.Step(
        id = "s1",
        durationSec = 60,
        label = "Steady",
        phase = SegmentPhase.WORK,
        isWork = true,
        targetRange = TargetRange(low = 200, high = 220)
      )
    )
  )
}
