package com.trainerloop.ui.library

import androidx.lifecycle.ViewModel
import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.Workout
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.data.model.WorkoutSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BuilderStepDraft(
  val minutes: String = "5",
  val lowW: String = "150",
  val highW: String = "160"
)

data class WorkoutBuilderUiState(
  val name: String = "",
  val steps: List<BuilderStepDraft> = listOf(BuilderStepDraft())
) {
  val hasValidName: Boolean
    get() = name.isNotBlank()

  val hasValidIntervals: Boolean
    get() = steps.isNotEmpty() && steps.all { step ->
      (step.minutes.toIntOrNull() ?: 0) > 0 &&
        (step.lowW.toIntOrNull() ?: 0) > 0 &&
        (step.highW.toIntOrNull() ?: 0) >= (step.lowW.toIntOrNull() ?: 0)
    }

  val isValid: Boolean
    get() = hasValidName && hasValidIntervals

  val saveReason: String?
    get() = when {
      hasValidName.not() -> "Add a name to save"
      steps.isEmpty() -> "Add at least one interval"
      hasValidIntervals.not() -> "Enter valid interval values"
      else -> null
    }

  fun toWorkout(id: String): Workout = Workout(
    id = id,
    name = name.trim(),
    description = "Custom workout",
    source = WorkoutSource.MANUAL,
    segments = steps.mapIndexed { index, step ->
      WorkoutSegment.Step(
        id = "step$index",
        durationSec = step.minutes.toIntOrNull()?.coerceAtLeast(0)?.times(60) ?: 0,
        label = "Interval ${index + 1}",
        phase = SegmentPhase.WORK,
        isWork = true,
        targetRange = TargetRange(
          low = step.lowW.toIntOrNull()?.coerceAtLeast(0) ?: 0,
          high = step.highW.toIntOrNull()?.coerceAtLeast(0) ?: 0
        )
      )
    }
  )
}

class WorkoutBuilderViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(WorkoutBuilderUiState())
  val uiState: StateFlow<WorkoutBuilderUiState> = _uiState.asStateFlow()

  fun onNameChange(name: String) {
    _uiState.value = _uiState.value.copy(name = name)
  }

  fun onMinutesChange(index: Int, minutes: String) {
    updateStep(index) { it.copy(minutes = minutes) }
  }

  fun onLowWChange(index: Int, lowW: String) {
    updateStep(index) { it.copy(lowW = lowW) }
  }

  fun onHighWChange(index: Int, highW: String) {
    updateStep(index) { it.copy(highW = highW) }
  }

  fun addStep() {
    _uiState.value = _uiState.value.copy(
      steps = _uiState.value.steps + BuilderStepDraft()
    )
  }

  fun deleteStep(index: Int) {
    if (index !in _uiState.value.steps.indices) return
    _uiState.value = _uiState.value.copy(
      steps = _uiState.value.steps.toMutableList().apply { removeAt(index) }
    )
  }

  fun moveStep(fromIndex: Int, toIndex: Int) {
    val steps = _uiState.value.steps
    if (fromIndex !in steps.indices || toIndex !in steps.indices || fromIndex == toIndex) return

    val reordered = steps.toMutableList()
    val moved = reordered.removeAt(fromIndex)
    reordered.add(toIndex, moved)
    _uiState.value = _uiState.value.copy(steps = reordered)
  }

  private fun updateStep(index: Int, update: (BuilderStepDraft) -> BuilderStepDraft) {
    val steps = _uiState.value.steps
    if (index !in steps.indices) return
    _uiState.value = _uiState.value.copy(
      steps = steps.toMutableList().apply { this[index] = update(this[index]) }
    )
  }
}
