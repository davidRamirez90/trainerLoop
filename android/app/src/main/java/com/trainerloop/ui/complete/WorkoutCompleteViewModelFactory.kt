package com.trainerloop.ui.complete

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.trainerloop.data.model.TelemetrySample

class WorkoutCompleteViewModelFactory(
  private val application: Application,
  private val sessionId: String,
  private val workoutId: String,
  private val workoutName: String,
  private val samples: List<TelemetrySample>,
  private val startTimeMs: Long,
  private val coachJson: String = "",
  private val sessionType: String = "WORKOUT",
  private val routeId: String? = null,
  private val completed: Boolean = false
) : ViewModelProvider.Factory {

  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(WorkoutCompleteViewModel::class.java)) {
      return WorkoutCompleteViewModel(
        application = application,
        sessionId = sessionId,
        workoutId = workoutId,
        workoutName = workoutName,
        samples = samples,
        startTimeMs = startTimeMs,
        coachJson = coachJson,
        sessionType = sessionType,
        routeId = routeId,
        completed = completed
      ) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
  }
}
