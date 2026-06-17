package com.trainerloop.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trainerloop.data.model.SegmentPhase
import com.trainerloop.data.model.TargetRange
import com.trainerloop.data.model.Workout
import com.trainerloop.data.model.WorkoutSegment
import com.trainerloop.data.model.WorkoutSource
import com.trainerloop.domain.WorkoutSummaryMath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File

enum class WorkoutCategory(val label: String) {
  ALL("All"),
  ENDURANCE("Endurance"),
  SWEET_SPOT("Sweet Spot"),
  THRESHOLD("Threshold"),
  VO2_MAX("VO2 Max")
}

data class WorkoutListItem(
  val workout: Workout,
  val category: WorkoutCategory,
  val stats: com.trainerloop.domain.WorkoutStats
)

data class LibraryUiState(
  val searchQuery: String = "",
  val selectedCategory: WorkoutCategory = WorkoutCategory.ALL,
  val workouts: List<WorkoutListItem> = emptyList(),
  val filteredWorkouts: List<WorkoutListItem> = emptyList(),
  val categories: List<WorkoutCategory> = WorkoutCategory.entries,
  val isLoading: Boolean = false,
  val error: String? = null
)

class WorkoutLibraryViewModel(application: Application) : AndroidViewModel(application) {

  private val importFile = File(application.filesDir, "imported_workouts.json")

  private val _uiState = MutableStateFlow(LibraryUiState())
  val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

  init {
    loadWorkouts()
  }

  fun onSearchQueryChange(query: String) {
    _uiState.value = _uiState.value.copy(searchQuery = query)
    applyFilter()
  }

  fun onCategorySelected(category: WorkoutCategory) {
    _uiState.value = _uiState.value.copy(selectedCategory = category)
    applyFilter()
  }

  private fun loadWorkouts() {
    val builtIn = builtInWorkouts().map { it.toListItem() }
    val imported = loadImportedWorkouts().map { it.toListItem() }
    val all = builtIn + imported
    _uiState.value = _uiState.value.copy(workouts = all)
    applyFilter()
  }

  private fun applyFilter() {
    val state = _uiState.value
    val query = state.searchQuery.trim().lowercase()
    val filtered = state.workouts.filter { item ->
      val matchesCategory = state.selectedCategory == WorkoutCategory.ALL ||
        item.category == state.selectedCategory
      val matchesSearch = query.isEmpty() ||
        item.workout.name.lowercase().contains(query) ||
        item.workout.description?.lowercase()?.contains(query) == true
      matchesCategory && matchesSearch
    }
    _uiState.value = state.copy(filteredWorkouts = filtered)
  }

  fun importWorkout(uri: Uri) {
    val context = getApplication<Application>()
    _uiState.value = _uiState.value.copy(isLoading = true, error = null)

    viewModelScope.launch {
      val result = WorkoutImportHelper.importWorkout(context, uri)
      if (result != null) {
        saveImportedWorkout(result)
        loadWorkouts()
        _uiState.value = _uiState.value.copy(isLoading = false)
      } else {
        _uiState.value = _uiState.value.copy(
          isLoading = false,
          error = "Failed to import workout"
        )
      }
    }
  }

  fun clearError() {
    _uiState.value = _uiState.value.copy(error = null)
  }

  private fun Workout.toListItem(): WorkoutListItem {
    return WorkoutListItem(
      workout = this,
      category = categorize(this),
      stats = WorkoutSummaryMath.workoutStats(this)
    )
  }

  private fun categorize(workout: Workout): WorkoutCategory {
    return when (workout.id) {
      "endurance" -> WorkoutCategory.ENDURANCE
      "sweet_spot" -> WorkoutCategory.SWEET_SPOT
      "pyramid" -> WorkoutCategory.THRESHOLD
      else -> {
        val ifactor = WorkoutSummaryMath.workoutStats(workout).intensityFactor
        when {
          ifactor < 0.75 -> WorkoutCategory.ENDURANCE
          ifactor < 0.90 -> WorkoutCategory.SWEET_SPOT
          ifactor < 1.05 -> WorkoutCategory.THRESHOLD
          else -> WorkoutCategory.VO2_MAX
        }
      }
    }
  }

  private fun saveImportedWorkout(imported: ImportedWorkout) {
    val existing = loadImportedWorkouts().toMutableList()
    existing.add(imported.workout)
    val json = JSONArray()
    existing.forEach { workout ->
      json.put(workoutToJson(workout))
    }
    importFile.writeText(json.toString())
  }

  private fun loadImportedWorkouts(): List<Workout> {
    if (!importFile.exists()) return emptyList()
    return try {
      val json = JSONArray(importFile.readText())
      (0 until json.length()).map { i ->
        jsonToWorkout(json.getJSONObject(i))
      }
    } catch (e: Exception) {
      emptyList()
    }
  }

  private fun builtInWorkouts(): List<Workout> = listOf(
    Workout(
      id = "sweet_spot",
      name = "Sweet Spot",
      description = "Aerobic sweet spot training",
      source = WorkoutSource.MANUAL,
      segments = listOf(
        WorkoutSegment.FreeRide(id = "wu", durationSec = 300, label = "Warm Up", phase = SegmentPhase.WARMUP),
        WorkoutSegment.Step(id = "ss1", durationSec = 600, label = "Sweet Spot", phase = SegmentPhase.WORK, isWork = true, targetRange = TargetRange(200, 210)),
        WorkoutSegment.Step(id = "ss2", durationSec = 600, label = "Sweet Spot", phase = SegmentPhase.WORK, isWork = true, targetRange = TargetRange(210, 220)),
        WorkoutSegment.FreeRide(id = "cd", durationSec = 300, label = "Cool Down", phase = SegmentPhase.COOLDOWN)
      )
    ),
    Workout(
      id = "pyramid",
      name = "Power Pyramid",
      description = "Build into a peak then back down",
      source = WorkoutSource.MANUAL,
      segments = listOf(
        WorkoutSegment.FreeRide(id = "wu", durationSec = 300, label = "Warm Up", phase = SegmentPhase.WARMUP),
        WorkoutSegment.Step(id = "l1", durationSec = 180, label = "Tempo", phase = SegmentPhase.WORK, isWork = true, targetRange = TargetRange(170, 180)),
        WorkoutSegment.Step(id = "l2", durationSec = 180, label = "Sweet Spot", phase = SegmentPhase.WORK, isWork = true, targetRange = TargetRange(200, 210)),
        WorkoutSegment.Step(id = "l3", durationSec = 120, label = "Threshold", phase = SegmentPhase.WORK, isWork = true, targetRange = TargetRange(240, 250)),
        WorkoutSegment.Step(id = "l4", durationSec = 180, label = "Sweet Spot", phase = SegmentPhase.WORK, isWork = true, targetRange = TargetRange(200, 210)),
        WorkoutSegment.Step(id = "l5", durationSec = 180, label = "Tempo", phase = SegmentPhase.WORK, isWork = true, targetRange = TargetRange(170, 180)),
        WorkoutSegment.FreeRide(id = "cd", durationSec = 300, label = "Cool Down", phase = SegmentPhase.COOLDOWN)
      )
    ),
    Workout(
      id = "endurance",
      name = "Endurance Ride",
      description = "Long steady endurance ride",
      source = WorkoutSource.MANUAL,
      segments = listOf(
        WorkoutSegment.FreeRide(id = "wu", durationSec = 600, label = "Easy Start", phase = SegmentPhase.WARMUP),
        WorkoutSegment.FreeRide(id = "main", durationSec = 3600, label = "Endurance", phase = SegmentPhase.WORK),
        WorkoutSegment.FreeRide(id = "cd", durationSec = 600, label = "Cool Down", phase = SegmentPhase.COOLDOWN)
      )
    )
  )

  companion object {
    private fun workoutToJson(w: Workout): org.json.JSONObject {
      return org.json.JSONObject().apply {
        put("id", w.id)
        put("name", w.name)
        put("description", w.description ?: "")
        put("source", w.source.name)
        val segs = JSONArray()
        w.segments.forEach { seg ->
          val obj = org.json.JSONObject()
          obj.put("id", seg.id)
          obj.put("durationSec", seg.durationSec)
          obj.put("label", seg.label ?: "")
          obj.put("phase", seg.phase.name)
          obj.put("isWork", seg.isWork)
          when (seg) {
            is WorkoutSegment.Step -> {
              obj.put("type", "step")
              obj.put("targetLow", seg.targetRange.low)
              obj.put("targetHigh", seg.targetRange.high)
              seg.targetCadence?.let { obj.put("cadenceLow", it.first); obj.put("cadenceHigh", it.last) }
            }
            is WorkoutSegment.Ramp -> {
              obj.put("type", "ramp")
              obj.put("startPower", seg.startPower)
              obj.put("endPower", seg.endPower)
            }
            is WorkoutSegment.FreeRide -> {
              obj.put("type", "freeride")
            }
          }
          segs.put(obj)
        }
        put("segments", segs)
      }
    }

    private fun jsonToWorkout(obj: org.json.JSONObject): Workout {
      val segs = mutableListOf<WorkoutSegment>()
      val segArr = obj.getJSONArray("segments")
      (0 until segArr.length()).forEach { i ->
        val s = segArr.getJSONObject(i)
        val type = s.getString("type")
        val segment = when (type) {
          "step" -> WorkoutSegment.Step(
            id = s.getString("id"),
            durationSec = s.getInt("durationSec"),
            label = s.optString("label", null)?.takeIf { it.isNotEmpty() },
            phase = SegmentPhase.valueOf(s.getString("phase")),
            isWork = s.getBoolean("isWork"),
            targetRange = TargetRange(s.getInt("targetLow"), s.getInt("targetHigh")),
            targetCadence = if (s.has("cadenceLow") && s.has("cadenceHigh")) {
              IntRange(s.getInt("cadenceLow"), s.getInt("cadenceHigh"))
            } else null
          )
          "ramp" -> WorkoutSegment.Ramp(
            id = s.getString("id"),
            durationSec = s.getInt("durationSec"),
            label = s.optString("label", null)?.takeIf { it.isNotEmpty() },
            phase = SegmentPhase.valueOf(s.getString("phase")),
            isWork = s.getBoolean("isWork"),
            startPower = s.getInt("startPower"),
            endPower = s.getInt("endPower")
          )
          else -> WorkoutSegment.FreeRide(
            id = s.getString("id"),
            durationSec = s.getInt("durationSec"),
            label = s.optString("label", null)?.takeIf { it.isNotEmpty() },
            phase = SegmentPhase.valueOf(s.getString("phase")),
            isWork = s.getBoolean("isWork")
          )
        }
        segs.add(segment)
      }
      return Workout(
        id = obj.getString("id"),
        name = obj.getString("name"),
        description = obj.optString("description", null)?.takeIf { it.isNotEmpty() },
        source = WorkoutSource.valueOf(obj.getString("source")),
        segments = segs
      )
    }
  }
}
