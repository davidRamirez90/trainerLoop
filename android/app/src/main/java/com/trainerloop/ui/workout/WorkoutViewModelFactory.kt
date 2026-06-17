package com.trainerloop.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.trainerloop.ble.FtmsControlManager
import com.trainerloop.ble.FtmsManager
import com.trainerloop.ble.HrManager
import com.trainerloop.data.model.Workout

class WorkoutViewModelFactory(
  private val workout: Workout,
  private val ftmsManager: FtmsManager? = null,
  private val hrManager: HrManager? = null,
  private val ftmsControlManager: FtmsControlManager? = null
) : ViewModelProvider.Factory {

  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(WorkoutViewModel::class.java)) {
      return WorkoutViewModel(
        workout,
        ftmsManager,
        hrManager,
        ftmsControlManager
      ) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
  }
}
