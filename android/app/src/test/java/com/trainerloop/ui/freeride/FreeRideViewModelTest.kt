package com.trainerloop.ui.freeride

import com.trainerloop.ble.FtmsManager
import com.trainerloop.ble.HrManager
import com.trainerloop.ble.ClickShift
import com.trainerloop.ble.ZwiftClickManager
import com.trainerloop.ble.model.IndoorBikeData
import com.trainerloop.data.model.Route
import com.trainerloop.data.model.RoutePoint
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FreeRideViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @Before fun setup() { Dispatchers.setMain(testDispatcher) }
  @After fun tearDown() { Dispatchers.resetMain() }

  private fun route(lengthM: Double = 2000.0) = Route("Test", List((lengthM / 10.0).toInt() + 1) { i ->
    RoutePoint(i * 10.0, 47.0 + i * 0.0001, 8.0, 500.0, 0.0)
  })

  private fun bikeData(power: Int, cadence: Double) = IndoorBikeData(
    powerWatts = power, cadenceRpm = cadence, speedKph = null, resistanceLevel = null,
    averagePower = null, averageSpeed = null, totalDistanceMeters = null,
    heartRateBpm = null, elapsedTimeSec = null, remainingTimeSec = null
  )

  private fun mockFtms(data: MutableStateFlow<IndoorBikeData?>): FtmsManager =
    mockk(relaxed = true) { every { this@mockk.data } returns data }

  private fun viewModel(ftmsData: MutableStateFlow<IndoorBikeData?>) = FreeRideViewModel(
    route = route(),
    routeId = "r1",
    ftmsManagerFlow = MutableStateFlow<FtmsManager?>(mockFtms(ftmsData)),
    hrManagerFlow = MutableStateFlow<HrManager?>(null),
    dispatcher = testDispatcher
  )

  @Test
  fun `pedaling advances distance and computes a target`() = runTest(testDispatcher) {
    val ftmsData = MutableStateFlow<IndoorBikeData?>(bikeData(180, 90.0))
    val vm = viewModel(ftmsData)
    vm.start()
    runCurrent()
    advanceTimeBy(30_000)
    runCurrent()
    val state = vm.uiState.value
    assertTrue("distance ${state.distanceM}", state.distanceM > 50.0)
    assertTrue("target ${state.targetPowerWatts}", state.targetPowerWatts > 0)
    assertEquals(7, state.gear)
  }

  @Test
  fun `shifting changes gear and is clamped`() = runTest(testDispatcher) {
    val vm = viewModel(MutableStateFlow(bikeData(180, 90.0)))
    vm.shiftUp()
    assertEquals(8, vm.uiState.value.gear)
    repeat(20) { vm.shiftDown() }
    assertEquals(1, vm.uiState.value.gear)
  }

  @Test
  fun `pause freezes distance`() = runTest(testDispatcher) {
    val ftmsData = MutableStateFlow<IndoorBikeData?>(bikeData(180, 90.0))
    val vm = viewModel(ftmsData)
    vm.start()
    runCurrent()
    advanceTimeBy(10_000)
    runCurrent()
    vm.pause()
    runCurrent()
    val frozen = vm.uiState.value.distanceM
    advanceTimeBy(10_000)
    runCurrent()
    assertEquals(frozen, vm.uiState.value.distanceM, 1e-6)
  }

  @Test
  fun `stop emits finish data with samples`() = runTest(testDispatcher) {
    val ftmsData = MutableStateFlow<IndoorBikeData?>(bikeData(180, 90.0))
    val vm = viewModel(ftmsData)
    vm.start()
    runCurrent()
    advanceTimeBy(5_000)
    runCurrent()
    vm.stop()
    runCurrent()
    val finish = vm.finishEvent.value
    assertNotNull(finish)
    assertTrue(finish!!.samples.isNotEmpty())
    assertEquals("Test", finish.workoutName)
    assertFalse(finish.completedNaturally)
  }

  @Test
  fun `zwift click shift events change gear like the buttons`() = runTest(testDispatcher) {
    val shifts = MutableSharedFlow<ClickShift>()
    val click: ZwiftClickManager = mockk(relaxed = true) {
      every { shiftEvents } returns shifts
    }
    val vm = FreeRideViewModel(
      route = route(),
      routeId = "r1",
      ftmsManagerFlow = MutableStateFlow(mockFtms(MutableStateFlow(bikeData(180, 90.0)))),
      hrManagerFlow = MutableStateFlow(null),
      clickManagerFlow = MutableStateFlow<ZwiftClickManager?>(click),
      dispatcher = testDispatcher
    )
    runCurrent()

    shifts.emit(ClickShift.UP)
    runCurrent()
    assertEquals(8, vm.uiState.value.gear)

    shifts.emit(ClickShift.DOWN)
    shifts.emit(ClickShift.DOWN)
    runCurrent()
    assertEquals(6, vm.uiState.value.gear)
  }

  @Test
  fun `no click paired behaves as today`() = runTest(testDispatcher) {
    val vm = viewModel(MutableStateFlow(bikeData(180, 90.0)))
    runCurrent()
    assertEquals(7, vm.uiState.value.gear)
    vm.shiftUp()
    assertEquals(8, vm.uiState.value.gear)
  }
}
