package com.trainerloop.ui.workout.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.trainerloop.data.model.Workout

class WorkoutDetailViewModelFactory(
  private val workout: Workout,
  private val ftp: Int
) : ViewModelProvider.Factory {

  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(WorkoutDetailViewModel::class.java)) {
      return WorkoutDetailViewModel(workout, ftp) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
  }
}
