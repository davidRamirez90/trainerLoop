package com.trainerloop.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trainerloop.app.TrainerLoopApplication
import com.trainerloop.data.model.Workout
import com.trainerloop.data.model.WorkoutSource
import com.trainerloop.data.repository.ProfileRepository
import com.trainerloop.data.source.remote.IntervalsIcuClient
import com.trainerloop.domain.WorkoutImporter
import com.trainerloop.domain.WorkoutSummaryMath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

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
  val error: String? = null,
  val canSync: Boolean = false,
  val isSyncing: Boolean = false,
  /** Ids the user has starred; these sort to the top. */
  val favoriteIds: Set<String> = emptySet(),
  /** Ids backed by the on-disk store (imported/built) — the only ones that can be deleted. */
  val deletableIds: Set<String> = emptySet()
)

class WorkoutLibraryViewModel(
  application: Application,
  private val profileRepository: ProfileRepository =
    (application as? TrainerLoopApplication)?.profileRepository ?: ProfileRepository(application)
) : AndroidViewModel(application) {

  private val _uiState = MutableStateFlow(LibraryUiState())
  val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

  init {
    loadWorkouts()
    viewModelScope.launch {
      profileRepository.profile.collect { profile ->
        _uiState.value = _uiState.value.copy(
          canSync = profile.intervalsIcuAthleteId.isNotBlank() &&
            profile.intervalsIcuApiKey.isNotBlank()
        )
        loadWorkouts()
      }
    }
  }

  fun onSearchQueryChange(query: String) {
    _uiState.value = _uiState.value.copy(searchQuery = query)
    applyFilter()
  }

  fun onCategorySelected(category: WorkoutCategory) {
    _uiState.value = _uiState.value.copy(selectedCategory = category)
    applyFilter()
  }

  fun refresh() = loadWorkouts()

  private fun loadWorkouts() {
    val ftp = profileRepository.getProfileSync().ftp
    val builtIn = BuiltInWorkouts.all().map { it.toListItem(ftp) }
    val stored = loadImportedWorkouts()
    val imported = stored.map { it.toListItem(ftp) }
    val all = builtIn + imported
    _uiState.value = _uiState.value.copy(
      workouts = all,
      favoriteIds = FavoriteStore.ids(getApplication()),
      deletableIds = stored.map { it.id }.toSet()
    )
    applyFilter()
  }

  fun toggleFavorite(id: String) {
    FavoriteStore.toggle(getApplication(), id)
    _uiState.value = _uiState.value.copy(favoriteIds = FavoriteStore.ids(getApplication()))
    applyFilter()
  }

  /** Copies a workout (built-in or imported) into the store so it can be tweaked. */
  fun duplicateWorkout(workout: Workout) {
    val copy = workout.copy(
      id = "custom_${System.currentTimeMillis()}",
      name = "${workout.name} (copy)",
      source = WorkoutSource.MANUAL
    )
    ImportedWorkoutStore.add(getApplication<Application>(), copy)
    loadWorkouts()
  }

  fun deleteWorkout(id: String) {
    ImportedWorkoutStore.remove(getApplication<Application>(), id)
    loadWorkouts()
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
    }.sortedBy { it.workout.id !in state.favoriteIds } // favorites (false) sort first
    _uiState.value = state.copy(filteredWorkouts = filtered)
  }

  fun importWorkout(uri: Uri) {
    val context = getApplication<Application>()
    _uiState.value = _uiState.value.copy(isLoading = true, error = null)

    viewModelScope.launch {
      val result = WorkoutImportHelper.importWorkout(
        context,
        uri,
        profileRepository.getProfileSync().ftp
      )
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

  fun sync() {
    val profile = profileRepository.getProfileSync()
    val athleteId = profile.intervalsIcuAthleteId
    val apiKey = profile.intervalsIcuApiKey
    if (athleteId.isBlank() || apiKey.isBlank()) return

    _uiState.value = _uiState.value.copy(isSyncing = true, error = null)
    viewModelScope.launch {
      try {
        val client = IntervalsIcuClient(apiKey)

        val athlete = client.getAthlete(athleteId)
        athlete.ftp?.let { ftp -> profileRepository.updateFtp(ftp) }
        athlete.icu_weight?.let { weight -> profileRepository.updateWeight(weight) }

        val today = LocalDate.now().toString()
        val events = client.getTodaysWorkoutEvents(athleteId, today)
        val ftp = profileRepository.getProfileSync().ftp
        events.forEach { event ->
          val zwo = client.downloadZwo(athleteId, event.id)
          val name = event.name ?: "intervals_${event.id}"
          val workout = WorkoutImporter.import("$name.zwo", zwo, ftp).copy(id = "icu_${event.id}")
          saveImportedWorkout(ImportedWorkout(fileName = "$name.zwo", workout = workout))
        }

        loadWorkouts()
        _uiState.value = _uiState.value.copy(isSyncing = false)
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(isSyncing = false, error = "Sync failed: ${e.message}")
      }
    }
  }

  fun clearError() {
    _uiState.value = _uiState.value.copy(error = null)
  }

  private fun Workout.toListItem(ftp: Int): WorkoutListItem {
    return WorkoutListItem(
      workout = this,
      category = categorize(this, ftp),
      stats = WorkoutSummaryMath.workoutStats(this, ftp)
    )
  }

  private fun categorize(workout: Workout, ftp: Int): WorkoutCategory {
    return when (workout.id) {
      "endurance" -> WorkoutCategory.ENDURANCE
      "sweet_spot" -> WorkoutCategory.SWEET_SPOT
      "pyramid" -> WorkoutCategory.THRESHOLD
      else -> {
        val ifactor = WorkoutSummaryMath.workoutStats(workout, ftp).intensityFactor
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
    ImportedWorkoutStore.add(getApplication<Application>(), imported.workout)
  }

  private fun loadImportedWorkouts(): List<Workout> =
    ImportedWorkoutStore.load(getApplication<Application>())

}
