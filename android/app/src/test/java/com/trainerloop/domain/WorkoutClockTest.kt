package com.trainerloop.domain

import app.cash.turbine.test
import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.WorkoutSegment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutClockTest {

  @Test
  fun `initial state`() = runTest {
    val clock = WorkoutClock(shortWorkout(), StandardTestDispatcher(testScheduler))
    clock.elapsedSec.test {
      assertEquals(0, awaitItem())
    }
    clock.activeSec.test {
      assertEquals(0, awaitItem())
    }
    clock.isRunning.test {
      assertEquals(false, awaitItem())
    }
    clock.isComplete.test {
      assertEquals(false, awaitItem())
    }
    clock.sessionId.test {
      assertEquals(0, awaitItem())
    }
  }

  @Test
  fun `start increments session id`() = runTest {
    val clock = WorkoutClock(shortWorkout(), StandardTestDispatcher(testScheduler))
    clock.sessionId.test {
      assertEquals(0, awaitItem())
      clock.start()
      runCurrent()
      assertEquals(1, awaitItem())
    }
  }

  @Test
  fun `elapsed advances every second while running`() = runTest {
    val clock = WorkoutClock(shortWorkout(durationSec = 10), StandardTestDispatcher(testScheduler))
    clock.elapsedSec.test {
      assertEquals(0, awaitItem())
      clock.start()
      runCurrent()
      advanceTimeBy(1000)
      assertEquals(1, awaitItem())
      advanceTimeBy(2000)
      assertEquals(2, awaitItem())
      assertEquals(3, awaitItem())
    }
  }

  @Test
  fun `pause stops elapsed advancement`() = runTest {
    val clock = WorkoutClock(shortWorkout(durationSec = 10), StandardTestDispatcher(testScheduler))
    clock.elapsedSec.test {
      assertEquals(0, awaitItem())
      clock.start()
      runCurrent()
      advanceTimeBy(1000)
      assertEquals(1, awaitItem())
      clock.pause()
      runCurrent()
      advanceTimeBy(2000)
      expectNoEvents()
      clock.resume()
      runCurrent()
      advanceTimeBy(1000)
      assertEquals(2, awaitItem())
    }
  }

  @Test
  fun `stop resets elapsed and active`() = runTest {
    val clock = WorkoutClock(shortWorkout(durationSec = 10), StandardTestDispatcher(testScheduler))
    clock.elapsedSec.test {
      assertEquals(0, awaitItem())
      clock.start()
      runCurrent()
      advanceTimeBy(2000)
      assertEquals(1, awaitItem())
      assertEquals(2, awaitItem())
      clock.stop()
      runCurrent()
      assertEquals(0, awaitItem())
    }
    clock.activeSec.test {
      assertEquals(0, awaitItem())
      clock.start()
      runCurrent()
      advanceTimeBy(2000)
      awaitItem()
      awaitItem()
      clock.stop()
      runCurrent()
      assertEquals(0, awaitItem())
    }
  }

  @Test
  fun `seek jumps elapsed without changing active`() = runTest {
    val clock = WorkoutClock(shortWorkout(durationSec = 10), StandardTestDispatcher(testScheduler))
    clock.elapsedSec.test {
      assertEquals(0, awaitItem())
      clock.start()
      runCurrent()
      clock.seek(5)
      runCurrent()
      assertEquals(5, awaitItem())
    }
    clock.activeSec.test {
      assertEquals(0, awaitItem())
      clock.start()
      runCurrent()
      clock.seek(5)
      runCurrent()
      expectNoEvents()
    }
  }

  @Test
  fun `completion stops clock at total duration`() = runTest {
    val clock = WorkoutClock(shortWorkout(durationSec = 2), StandardTestDispatcher(testScheduler))
    clock.isComplete.test {
      assertEquals(false, awaitItem())
      clock.start()
      runCurrent()
      advanceTimeBy(2000)
      assertEquals(true, awaitItem())
    }
    clock.isRunning.test {
      assertEquals(false, awaitItem())
      clock.start()
      runCurrent()
      advanceTimeBy(2000)
      assertEquals(true, awaitItem())
      assertEquals(false, awaitItem())
    }
  }

  @Test
  fun `session id increments on restart`() = runTest {
    val clock = WorkoutClock(shortWorkout(durationSec = 2), StandardTestDispatcher(testScheduler))
    clock.sessionId.test {
      assertEquals(0, awaitItem())
      clock.start()
      runCurrent()
      assertEquals(1, awaitItem())
      clock.stop()
      runCurrent()
      clock.start()
      runCurrent()
      assertEquals(2, awaitItem())
    }
  }

  @Test
  fun `seek back from complete allows resume`() = runTest {
    val clock = WorkoutClock(shortWorkout(durationSec = 2), StandardTestDispatcher(testScheduler))
    clock.isComplete.test {
      assertEquals(false, awaitItem())
      clock.start()
      runCurrent()
      advanceTimeBy(2000)
      assertEquals(true, awaitItem())
      clock.seek(1)
      runCurrent()
      assertEquals(false, awaitItem())
    }
    clock.elapsedSec.test {
      assertEquals(1, awaitItem())
      clock.resume()
      runCurrent()
      advanceTimeBy(1000)
      assertEquals(2, awaitItem())
    }
  }

  @Test
  fun `close releases scope and stops ticking`() = runTest {
    val clock = WorkoutClock(shortWorkout(durationSec = 10), StandardTestDispatcher(testScheduler))
    clock.elapsedSec.test {
      assertEquals(0, awaitItem())
      clock.start()
      runCurrent()
      advanceTimeBy(1000)
      assertEquals(1, awaitItem())
      clock.close()
      runCurrent()
      advanceTimeBy(2000)
      expectNoEvents()
    }
  }

  @Test
  fun `start while paused resumes without resetting elapsed`() = runTest {
    val clock = WorkoutClock(shortWorkout(durationSec = 60), StandardTestDispatcher(testScheduler))
    clock.elapsedSec.test {
      assertEquals(0, awaitItem())
      clock.start()
      runCurrent()
      advanceTimeBy(5000)
      assertEquals(1, awaitItem())
      assertEquals(2, awaitItem())
      assertEquals(3, awaitItem())
      assertEquals(4, awaitItem())
      assertEquals(5, awaitItem())
      clock.pause()
      runCurrent()
      clock.start() // the bug: this used to reset elapsed to 0
      runCurrent()
      expectNoEvents() // no reset-to-0 emission
      advanceTimeBy(1000)
      assertEquals(6, awaitItem())
    }
  }

  @Test
  fun `start while paused does not bump session id`() = runTest {
    val clock = WorkoutClock(shortWorkout(durationSec = 60), StandardTestDispatcher(testScheduler))
    clock.sessionId.test {
      assertEquals(0, awaitItem())
      clock.start()
      runCurrent()
      assertEquals(1, awaitItem())
      advanceTimeBy(3000)
      clock.pause()
      runCurrent()
      clock.start()
      runCurrent()
      expectNoEvents()
    }
  }

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
