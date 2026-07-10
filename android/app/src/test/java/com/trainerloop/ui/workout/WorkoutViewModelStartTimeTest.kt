package com.trainerloop.ui.workout

import android.bluetooth.BluetoothDevice
import com.trainerloop.ble.FtmsManager
import com.trainerloop.ble.model.IndoorBikeData
import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.Workout
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.data.model.WorkoutSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModelStartTimeTest {

  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setup() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private val workout = Workout(
    id = "w",
    name = "W",
    description = null,
    source = WorkoutSource.MANUAL,
    segments = listOf(
      WorkoutSegment.FreeRide(
        id = "s",
        durationSec = 600,
        label = null,
        phase = SegmentPhase.WORK
      )
    )
  )

  @Test
  fun `finish start time is wall clock at first start, unaffected by pause`() = runTest(dispatcher) {
    val ftmsData = MutableStateFlow<IndoorBikeData?>(lowPowerData())
    val ftms = mockFtmsManager(ftmsData)
    var wallClock = 1_000_000L
    val viewModel = WorkoutViewModel(
      workout = workout,
      ftmsManagerFlow = MutableStateFlow<FtmsManager?>(ftms),
      dispatcher = dispatcher,
      now = { wallClock }
    )

    runCurrent()
    viewModel.start()
    advanceTimeBy(10_000)
    wallClock += 10_000
    runCurrent()
    viewModel.pause()
    advanceTimeBy(600_000)
    wallClock += 600_000
    viewModel.resume()
    advanceTimeBy(5_000)
    wallClock += 5_000
    runCurrent()
    viewModel.stop()
    runCurrent()

    assertEquals(1_000_000L, viewModel.finishEvent.value?.startTimeMs ?: -1L)
  }

  private fun mockFtmsManager(data: MutableStateFlow<IndoorBikeData?>): FtmsManager {
    val manager = mockk<FtmsManager>(relaxed = true)
    every { manager.device } returns mockk<BluetoothDevice>(relaxed = true)
    every { manager.data } returns data
    every { manager.isConnected } returns MutableStateFlow(false)
    every { manager.batteryLevel } returns MutableStateFlow(null)
    every { manager.manufacturer } returns MutableStateFlow(null)
    every { manager.model } returns MutableStateFlow(null)
    return manager
  }

  private fun lowPowerData() = IndoorBikeData(
    powerWatts = 100,
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
}
