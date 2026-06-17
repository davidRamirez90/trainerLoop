package com.trainerloop.domain

import com.trainerloop.ble.model.IndoorBikeData
import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.WorkoutSegment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryRecorderTest {

  @Test
  fun `records sample on each clock tick merging ftms and hr`() = runTest {
    val testDispatcher = StandardTestDispatcher(testScheduler)
    val clock = WorkoutClock(shortWorkout(durationSec = 5), testDispatcher)
    val ftmsData = MutableStateFlow<IndoorBikeData?>(null)
    val hrData = MutableStateFlow<Int?>(null)
    val recorder = TelemetryRecorder(
      clock,
      TelemetryRecorder.DataProvider(ftmsData, hrData),
      testDispatcher
    )

    recorder.startCollecting()
    runCurrent()

    ftmsData.value = bikeData(powerWatts = 200, cadenceRpm = 85.0)
    hrData.value = 150

    clock.start()
    runCurrent()
    advanceTimeBy(1000)
    runCurrent()

    val latest = recorder.latest.value
    assertEquals(1, latest.timeSec)
    assertEquals(200, latest.powerWatts)
    assertEquals(85, latest.cadenceRpm)
    assertEquals(150, latest.hrBpm)
    assertFalse(latest.dropout)
  }

  @Test
  fun `marks dropout when no ftms data`() = runTest {
    val testDispatcher = StandardTestDispatcher(testScheduler)
    val clock = WorkoutClock(shortWorkout(durationSec = 5), testDispatcher)
    val ftmsData = MutableStateFlow<IndoorBikeData?>(null)
    val hrData = MutableStateFlow<Int?>(150)
    val recorder = TelemetryRecorder(
      clock,
      TelemetryRecorder.DataProvider(ftmsData, hrData),
      testDispatcher
    )

    recorder.startCollecting()
    runCurrent()
    clock.start()
    runCurrent()
    advanceTimeBy(1000)
    runCurrent()

    val latest = recorder.latest.value
    assertEquals(1, latest.timeSec)
    assertTrue(latest.dropout)
  }

  @Test
  fun `keeps last known values during dropout`() = runTest {
    val testDispatcher = StandardTestDispatcher(testScheduler)
    val clock = WorkoutClock(shortWorkout(durationSec = 10), testDispatcher)
    val ftmsData = MutableStateFlow<IndoorBikeData?>(null)
    val hrData = MutableStateFlow<Int?>(null)
    val recorder = TelemetryRecorder(
      clock,
      TelemetryRecorder.DataProvider(ftmsData, hrData),
      testDispatcher
    )

    recorder.startCollecting()
    runCurrent()

    ftmsData.value = bikeData(powerWatts = 200, cadenceRpm = 85.0)
    hrData.value = 150

    clock.start()
    runCurrent()
    advanceTimeBy(1000)
    runCurrent()

    ftmsData.value = null
    advanceTimeBy(2000)
    runCurrent()

    val samples = recorder.samples.value
    assertEquals(3, samples.size)
    assertEquals(1, samples[0].timeSec)
    assertFalse(samples[0].dropout)
    assertEquals(200, samples[0].powerWatts)
    assertEquals(150, samples[0].hrBpm)

    assertEquals(2, samples[1].timeSec)
    assertTrue(samples[1].dropout)
    assertEquals(200, samples[1].powerWatts)
    assertEquals(150, samples[1].hrBpm)

    assertEquals(3, samples[2].timeSec)
    assertTrue(samples[2].dropout)
  }

  @Test
  fun `updates power when new ftms data arrives`() = runTest {
    val testDispatcher = StandardTestDispatcher(testScheduler)
    val clock = WorkoutClock(shortWorkout(durationSec = 10), testDispatcher)
    val ftmsData = MutableStateFlow<IndoorBikeData?>(null)
    val hrData = MutableStateFlow<Int?>(150)
    val recorder = TelemetryRecorder(
      clock,
      TelemetryRecorder.DataProvider(ftmsData, hrData),
      testDispatcher
    )

    recorder.startCollecting()
    runCurrent()
    clock.start()
    runCurrent()

    ftmsData.value = bikeData(powerWatts = 150, cadenceRpm = 80.0)
    advanceTimeBy(1000)
    runCurrent()

    ftmsData.value = bikeData(powerWatts = 250, cadenceRpm = 90.0)
    advanceTimeBy(1000)
    runCurrent()

    val samples = recorder.samples.value
    assertEquals(2, samples.size)
    assertEquals(150, samples[0].powerWatts)
    assertEquals(250, samples[1].powerWatts)
  }

  @Test
  fun `reset clears samples`() = runTest {
    val testDispatcher = StandardTestDispatcher(testScheduler)
    val clock = WorkoutClock(shortWorkout(durationSec = 5), testDispatcher)
    val ftmsData = MutableStateFlow<IndoorBikeData?>(bikeData(powerWatts = 200))
    val hrData = MutableStateFlow<Int?>(150)
    val recorder = TelemetryRecorder(
      clock,
      TelemetryRecorder.DataProvider(ftmsData, hrData),
      testDispatcher
    )

    recorder.startCollecting()
    runCurrent()
    clock.start()
    runCurrent()
    advanceTimeBy(1000)
    runCurrent()

    assertTrue(recorder.samples.value.isNotEmpty())

    recorder.reset(2)

    assertTrue(recorder.samples.value.isEmpty())
    assertTrue(recorder.latest.value.dropout)
  }

  private fun bikeData(
    powerWatts: Int = 0,
    cadenceRpm: Double? = null,
    speedKph: Double? = null
  ): IndoorBikeData = IndoorBikeData(
    powerWatts = powerWatts,
    cadenceRpm = cadenceRpm,
    speedKph = speedKph,
    resistanceLevel = null,
    averagePower = null,
    averageSpeed = null,
    totalDistanceMeters = null,
    heartRateBpm = null,
    elapsedTimeSec = null,
    remainingTimeSec = null
  )

  private fun shortWorkout(durationSec: Int = 5): List<WorkoutSegment> {
    return listOf(
      WorkoutSegment.Step(
        id = "s1",
        durationSec = durationSec,
        label = null,
        phase = SegmentPhase.WORK,
        isWork = true,
        targetRange = TargetRange(100, 100)
      )
    )
  }
}
