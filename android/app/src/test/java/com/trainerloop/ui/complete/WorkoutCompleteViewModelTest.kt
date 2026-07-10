package com.trainerloop.ui.complete

import android.app.Application
import com.trainerloop.data.model.SessionData
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.data.repository.SessionRepository
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutCompleteViewModelTest {

  private val dispatcher = UnconfinedTestDispatcher()
  private lateinit var application: Application
  private lateinit var sessionRepository: FakeSessionRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    application = mockk(relaxed = true)
    every { application.filesDir } returns File(System.getProperty("java.io.tmpdir") ?: ".")
    sessionRepository = FakeSessionRepository()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `init does not save or upload`() = runTest(dispatcher) {
    val viewModel = createViewModel()

    assertTrue(sessionRepository.saved.isEmpty())
    assertEquals(null, viewModel.uiState.value.uploadStatus)
  }

  @Test
  fun `onSave saves once and marks completed flag from constructor`() = runTest(dispatcher) {
    val viewModel = createViewModel()

    viewModel.onSave()
    viewModel.onSave()

    assertEquals(1, sessionRepository.saved.size)
    assertEquals(false, sessionRepository.saved[0].completed)
  }

  @Test
  fun `onDiscard before save deletes nothing from the repository`() = runTest(dispatcher) {
    val viewModel = createViewModel()

    viewModel.onDiscard()

    assertTrue(sessionRepository.deleted.isEmpty())
    assertTrue(viewModel.uiState.value.isDiscarded)
  }

  @Test
  fun `onDiscard after save deletes the session`() = runTest(dispatcher) {
    val viewModel = createViewModel()

    viewModel.onSave()
    viewModel.onDiscard()

    assertEquals(listOf("session-1"), sessionRepository.deleted)
  }

  private fun createViewModel(): WorkoutCompleteViewModel = WorkoutCompleteViewModel(
    application = application,
    sessionId = "session-1",
    workoutId = "workout-1",
    workoutName = "Test Workout",
    samples = listOf(TelemetrySample(1, 150, 80, 140)),
    sessionRepository = sessionRepository
  )
}

private class FakeSessionRepository : SessionRepository(mockk(relaxed = true)) {
  val saved = mutableListOf<SessionData>()
  val deleted = mutableListOf<String>()

  override suspend fun save(session: SessionData) {
    saved += session
  }

  override suspend fun deleteById(id: String) {
    deleted += id
  }
}
