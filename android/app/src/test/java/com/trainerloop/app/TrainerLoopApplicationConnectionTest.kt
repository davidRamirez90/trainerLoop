package com.trainerloop.app

import android.bluetooth.BluetoothDevice
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrainerLoopApplicationConnectionTest {
  private val dispatcher = UnconfinedTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `same peripheral shares trainer GATT link with HR manager`() = runTest(dispatcher) {
    val app = TrainerLoopApplication()

    app.attachTrainer(device("AA:BB:CC:DD:EE:FF"))
    app.attachHr(device("AA:BB:CC:DD:EE:FF"))
    runCurrent()

    assertTrue(app.hrManager.value!!.usesSharedTrainerConnection)
  }

  @Test
  fun `different HR peripheral keeps its own GATT link`() = runTest(dispatcher) {
    val app = TrainerLoopApplication()

    app.attachTrainer(device("AA:BB:CC:DD:EE:FF"))
    app.attachHr(device("11:22:33:44:55:66"))
    runCurrent()

    assertFalse(app.hrManager.value!!.usesSharedTrainerConnection)
  }

  private fun device(address: String): BluetoothDevice =
    mockk(relaxed = true) {
      every { this@mockk.address } returns address
    }
}
