package com.trainerloop.ui.workout

import android.bluetooth.BluetoothDevice
import com.trainerloop.ble.FtmsControlManager
import com.trainerloop.ble.FtmsManager
import com.trainerloop.ble.HrManager
import com.trainerloop.ble.model.IndoorBikeData
import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.Workout
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.data.model.WorkoutSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    val ftmsData = MutableStateFlow<IndoorBikeData?>(null)
    val heartRate = MutableStateFlow<Int?>(null)
    val ftms = mockFtmsManager(data = ftmsData)
    val hr = mockHrManager(heartRate = heartRate)

    val viewModel = WorkoutViewModel(
      workout = sampleWorkout(),
      ftmsManager = ftms,
      hrManager = hr,
      dispatcher = testDispatcher
    )

    ftmsData.value = IndoorBikeData(
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
    )
    heartRate.value = 145

    runCurrent()

    val state = viewModel.uiState.value
    assertEquals("power", 250, state.currentPowerWatts)
    assertEquals("cadence", 90, state.currentCadenceRpm)
    assertEquals("hr", 145, state.currentHrBpm)
  }

  @Test
  fun `dropout keeps last known values`() = runTest(testDispatcher) {
    val ftmsData = MutableStateFlow<IndoorBikeData?>(null)
    val heartRate = MutableStateFlow<Int?>(null)
    val ftms = mockFtmsManager(data = ftmsData)
    val hr = mockHrManager(heartRate = heartRate)

    val viewModel = WorkoutViewModel(
      workout = sampleWorkout(),
      ftmsManager = ftms,
      hrManager = hr,
      dispatcher = testDispatcher
    )

    ftmsData.value = IndoorBikeData(
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
    )
    heartRate.value = 140

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
  fun `intensity offset adjusts target range`() = runTest(testDispatcher) {
    val viewModel = WorkoutViewModel(workout = sampleWorkout())

    viewModel.adjustIntensityUp()

    val state = viewModel.uiState.value
    assertEquals(5, state.intensityOffsetPct)
    assertTrue(state.targetRange.low > 0)
  }

  @Test
  fun `toggle erg disables erg`() = runTest(testDispatcher) {
    val viewModel = WorkoutViewModel(workout = sampleWorkout())

    assertTrue(viewModel.uiState.value.isErgEnabled)

    viewModel.toggleErg()

    assertFalse(viewModel.uiState.value.isErgEnabled)
  }

  private fun mockFtmsManager(
    data: MutableStateFlow<IndoorBikeData?> = MutableStateFlow(null)
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
    heartRate: MutableStateFlow<Int?> = MutableStateFlow(null)
  ): HrManager {
    val device = mockk<BluetoothDevice>(relaxed = true)
    val manager = mockk<HrManager>(relaxed = true)
    every { manager.device } returns device
    every { manager.heartRate } returns heartRate
    every { manager.isConnected } returns MutableStateFlow(false)
    return manager
  }

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
