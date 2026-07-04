package com.trainerloop.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.trainerloop.ble.FtmsControlManager
import com.trainerloop.ble.FtmsManager
import com.trainerloop.ble.HrManager
import com.trainerloop.data.model.Workout
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * @param ftmsManagerFlow the application-owned [FtmsManager] state. Pass the
 *   `StateFlow` (not a snapshot) so a manager that appears *after* this
 *   factory-built ViewModel is created still wires into the recorder.
 * @param hrManagerFlow same idea for the HR sensor.
 */
class WorkoutViewModelFactory(
  private val workout: Workout,
  private val ftmsManagerFlow: StateFlow<FtmsManager?> = MutableStateFlow(null),
  private val hrManagerFlow: StateFlow<HrManager?> = MutableStateFlow(null),
  private val ftmsControlManagerFlow: StateFlow<FtmsControlManager?> = MutableStateFlow(null),
  private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModelProvider.Factory {

  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(WorkoutViewModel::class.java)) {
      com.trainerloop.ble.BleLog.d(
        "WorkoutViewModelFactory.create ftms=${ftmsManagerFlow.value?.device?.address} " +
          "hr=${hrManagerFlow.value?.device?.address} " +
          "ctrl=${ftmsControlManagerFlow.value?.device?.address}"
      )
      return WorkoutViewModel(
        workout,
        ftmsManagerFlow,
        hrManagerFlow,
        ftmsControlManagerFlow,
        dispatcher
      ) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
  }
}
