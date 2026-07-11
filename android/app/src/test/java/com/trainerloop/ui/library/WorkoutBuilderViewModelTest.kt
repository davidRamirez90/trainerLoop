package com.trainerloop.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutBuilderViewModelTest {

  @Test
  fun `moveStep reorders the draft steps`() {
    val viewModel = WorkoutBuilderViewModel()
    viewModel.onMinutesChange(index = 0, minutes = "1")
    viewModel.addStep()
    viewModel.onMinutesChange(index = 1, minutes = "2")
    viewModel.addStep()
    viewModel.onMinutesChange(index = 2, minutes = "3")

    viewModel.moveStep(fromIndex = 0, toIndex = 2)

    assertEquals(listOf("2", "3", "1"), viewModel.uiState.value.steps.map { it.minutes })
  }
}
