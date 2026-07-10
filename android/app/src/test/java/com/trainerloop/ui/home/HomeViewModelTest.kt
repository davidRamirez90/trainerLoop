@file:Suppress("OPT_IN_USAGE")

package com.trainerloop.ui.home

import android.app.Application
import android.bluetooth.BluetoothDevice
import com.trainerloop.app.ManagerProvider
import com.trainerloop.ble.FtmsManager
import com.trainerloop.ble.HrManager
import com.trainerloop.ble.model.Stamped
import com.trainerloop.data.model.SessionSummary
import com.trainerloop.data.model.UserProfile
import com.trainerloop.data.repository.ProfileRepository
import com.trainerloop.data.repository.SessionRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HomeViewModelTest {

  private lateinit var application: Application
  private lateinit var profileRepository: FakeProfileRepository
  private lateinit var sessionRepository: FakeSessionRepository
  private lateinit var managerProvider: FakeManagerProvider

  @Before
  fun setup() {
    application = mockk(relaxed = true)
    profileRepository = FakeProfileRepository()
    sessionRepository = FakeSessionRepository()
    managerProvider = FakeManagerProvider()
  }

  @Test
  fun `profile values flow into uiState`() = runTest {
    profileRepository.emit(
      UserProfile(
        name = "Alex",
        ftp = 300,
        weightKg = 72.5
      )
    )

    val viewModel = createViewModel(CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()))

    val state = viewModel.uiState.value
    assertEquals("Alex", state.riderName)
    assertEquals(300, state.ftp)
    assertEquals(72.5, state.weightKg, 0.001)
  }

  @Test
  fun `most recent session is surfaced`() = runTest {
    sessionRepository.emit(
      listOf(
        summary(id = "older", startedAt = "2026-06-15T08:00:00Z"),
        summary(id = "newer", startedAt = "2026-06-17T10:00:00Z"),
        summary(id = "middle", startedAt = "2026-06-16T09:00:00Z")
      )
    )

    val viewModel = createViewModel(CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()))

    assertEquals("newer", viewModel.uiState.value.recentSession?.id)
  }

  @Test
  fun `attaching trainer updates connectedTrainer and isTrainerConnected`() = runTest {
    val device = mockk<BluetoothDevice> {
      every { name } returns "KICKR"
    }
    val ftmsManager = FakeFtmsManager(device)
    managerProvider.attachTrainer(ftmsManager.mock)

    val viewModel = createViewModel(CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()))

    assertEquals(device, viewModel.uiState.value.connectedTrainer)
    assertFalse(viewModel.uiState.value.isTrainerConnected)

    ftmsManager.setConnected(true)

    assertTrue(viewModel.uiState.value.isTrainerConnected)
    assertEquals("KICKR", viewModel.uiState.value.connectedTrainer?.name)
  }

  @Test
  fun `replacing manager stops old collectors and resets stale fields`() = runTest {
    val oldDevice = mockk<BluetoothDevice> {
      every { name } returns "Old Trainer"
    }
    val newDevice = mockk<BluetoothDevice> {
      every { name } returns "New Trainer"
    }
    val oldFtms = FakeFtmsManager(oldDevice).apply {
      setConnected(true)
      setBattery(80)
      setModel("Old Model")
    }
    val newFtms = FakeFtmsManager(newDevice)

    managerProvider.attachTrainer(oldFtms.mock)
    val viewModel = createViewModel(CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()))

    assertEquals("Old Trainer", viewModel.uiState.value.connectedTrainer?.name)
    assertTrue(viewModel.uiState.value.isTrainerConnected)
    assertEquals(80, viewModel.uiState.value.trainerBattery)
    assertEquals("Old Model", viewModel.uiState.value.trainerModel)

    managerProvider.attachTrainer(newFtms.mock)

    assertEquals("New Trainer", viewModel.uiState.value.connectedTrainer?.name)
    assertFalse(viewModel.uiState.value.isTrainerConnected)
    assertNull(viewModel.uiState.value.trainerBattery)
    assertNull(viewModel.uiState.value.trainerModel)

    oldFtms.setConnected(false)
    oldFtms.setBattery(99)
    oldFtms.setModel("Should not apply")

    // Old collector must be cancelled; stale values should not overwrite new manager's state.
    assertNull(viewModel.uiState.value.trainerBattery)
    assertNull(viewModel.uiState.value.trainerModel)
    assertFalse(viewModel.uiState.value.isTrainerConnected)

    newFtms.setConnected(true)
    newFtms.setBattery(50)
    newFtms.setModel("New Model")

    assertTrue(viewModel.uiState.value.isTrainerConnected)
    assertEquals(50, viewModel.uiState.value.trainerBattery)
    assertEquals("New Model", viewModel.uiState.value.trainerModel)
  }

  @Test
  fun `hr manager updates latestHrBpm`() = runTest {
    val device = mockk<BluetoothDevice> {
      every { name } returns "HRM"
    }
    val hrManager = FakeHrManager(device)
    managerProvider.attachHr(hrManager.mock)

    val viewModel = createViewModel(CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()))

    assertEquals(device, viewModel.uiState.value.connectedHr)
    assertNull(viewModel.uiState.value.latestHrBpm)

    hrManager.setHeartRate(145)

    assertEquals(145, viewModel.uiState.value.latestHrBpm)
  }

  private fun createViewModel(scope: CoroutineScope): HomeViewModel {
    return HomeViewModel(
      application = application,
      profileRepository = profileRepository,
      sessionRepository = sessionRepository,
      managerProvider = managerProvider,
      coroutineScope = scope
    )
  }

  private fun summary(
    id: String,
    startedAt: String,
    workoutName: String = "Test"
  ): SessionSummary = SessionSummary(
    id = id,
    workoutId = "workout-$id",
    workoutName = workoutName,
    startedAt = startedAt,
    endedAt = null,
    durationSec = 1800,
    completed = true,
    avgPower = 200,
    maxPower = 300,
    avgCadence = 90,
    avgHr = 140
  )
}

private class FakeProfileRepository : ProfileRepository(mockk(relaxed = true)) {
  private val _profile = MutableStateFlow(UserProfile())

  override val profile: Flow<UserProfile> = _profile.asStateFlow()

  fun emit(profile: UserProfile) {
    _profile.value = profile
  }
}

private class FakeSessionRepository : SessionRepository(mockk(relaxed = true)) {
  private val _summaries = MutableStateFlow<List<SessionSummary>>(emptyList())

  override fun summaries(): Flow<List<SessionSummary>> = _summaries.asStateFlow()

  fun emit(summaries: List<SessionSummary>) {
    _summaries.value = summaries
  }
}

private class FakeManagerProvider : ManagerProvider {
  private val _ftmsManager = MutableStateFlow<FtmsManager?>(null)
  private val _hrManager = MutableStateFlow<HrManager?>(null)

  override val ftmsManager: StateFlow<FtmsManager?> = _ftmsManager.asStateFlow()
  override val hrManager: StateFlow<HrManager?> = _hrManager.asStateFlow()

  fun attachTrainer(manager: FtmsManager) {
    _ftmsManager.value = manager
  }

  fun attachHr(manager: HrManager) {
    _hrManager.value = manager
  }
}

private class FakeFtmsManager(
  val device: BluetoothDevice
) {
  private val _isConnected = MutableStateFlow(false)
  private val _batteryLevel = MutableStateFlow<Int?>(null)
  private val _model = MutableStateFlow<String?>(null)

  val mock: FtmsManager = mockk(relaxed = true)

  init {
    every { mock.device } returns device
    every { mock.isConnected } returns _isConnected.asStateFlow()
    every { mock.batteryLevel } returns _batteryLevel.asStateFlow()
    every { mock.model } returns _model.asStateFlow()
  }

  fun setConnected(connected: Boolean) {
    _isConnected.value = connected
  }

  fun setBattery(battery: Int?) {
    _batteryLevel.value = battery
  }

  fun setModel(model: String?) {
    _model.value = model
  }
}

private class FakeHrManager(
  val device: BluetoothDevice
) {
  private val _heartRate = MutableStateFlow<Stamped<Int>?>(null)
  private val _isConnected = MutableStateFlow(false)

  val mock: HrManager = mockk(relaxed = true)

  init {
    every { mock.device } returns device
    every { mock.heartRate } returns _heartRate.asStateFlow()
    every { mock.isConnected } returns _isConnected.asStateFlow()
  }

  fun setHeartRate(bpm: Int?) {
    _heartRate.value = bpm?.let { Stamped(it, android.os.SystemClock.elapsedRealtime()) }
  }

  fun setConnected(connected: Boolean) {
    _isConnected.value = connected
  }
}
