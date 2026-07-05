package com.trainerloop.ui.home

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trainerloop.app.ManagerProvider
import com.trainerloop.app.trainerLoopApp
import com.trainerloop.ble.FtmsManager
import com.trainerloop.ble.HrManager
import com.trainerloop.data.model.SessionSummary
import com.trainerloop.data.model.Workout
import com.trainerloop.data.repository.ProfileRepository
import com.trainerloop.data.repository.SessionRepository
import com.trainerloop.data.source.local.AppDatabase
import com.trainerloop.data.source.remote.IntervalsIcuClient
import com.trainerloop.domain.WorkoutImporter
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
  val riderName: String = "Rider",
  val ftp: Int = 250,
  val weightKg: Double = 75.0,
  val connectedTrainer: BluetoothDevice? = null,
  val isTrainerConnected: Boolean = false,
  val trainerBattery: Int? = null,
  val trainerModel: String? = null,
  val connectedHr: BluetoothDevice? = null,
  val isHrConnected: Boolean = false,
  val latestHrBpm: Int? = null,
  val recentSession: SessionSummary? = null,
  // intervals.icu planned-workout quick start
  val plannedName: String? = null,
  val plannedWorkout: Workout? = null,
  val plannedLoading: Boolean = false,
  val plannedError: String? = null
)

class HomeViewModel(
  application: Application,
  private val profileRepository: ProfileRepository,
  private val sessionRepository: SessionRepository,
  private val managerProvider: ManagerProvider,
  private val coroutineScope: CoroutineScope? = null
) : AndroidViewModel(application) {

  constructor(application: Application) : this(
    application,
    ProfileRepository(application),
    SessionRepository.create(AppDatabase.getInstance(application)),
    application.trainerLoopApp,
    null
  )

  private val scope: CoroutineScope
    get() = coroutineScope ?: viewModelScope

  private val _uiState = MutableStateFlow(HomeUiState())
  val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

  /** Set once the planned workout has been downloaded + parsed and is ready to ride. */
  private val _plannedWorkoutReady = MutableStateFlow<Workout?>(null)
  val plannedWorkoutReady: StateFlow<Workout?> = _plannedWorkoutReady.asStateFlow()

  private var plannedEventId: Long? = null

  private var ftmsJob: Job? = null
  private var hrJob: Job? = null

  init {
    scope.launch {
      profileRepository.profile.collect { profile ->
        _uiState.value = _uiState.value.copy(
          riderName = profile.name,
          ftp = profile.ftp,
          weightKg = profile.weightKg
        )
      }
    }

    scope.launch {
      sessionRepository.summaries().collect { sessions ->
        _uiState.value = _uiState.value.copy(
          recentSession = sessions.maxByOrNull { it.startedAt }
        )
      }
    }

    scope.launch {
      managerProvider.ftmsManager.collect { manager ->
        bindTrainer(manager)
      }
    }

    scope.launch {
      managerProvider.hrManager.collect { manager ->
        bindHr(manager)
      }
    }

    refreshIntervals()
  }

  /**
   * Pulls today's planned workout (for the quick-start card) and the latest
   * FTP/weight from intervals.icu. No-op when credentials aren't configured.
   * Failures are surfaced softly on the card, not fatal.
   */
  fun refreshIntervals() {
    val profile = profileRepository.getProfileSync()
    val athleteId = profile.intervalsIcuAthleteId
    val apiKey = profile.intervalsIcuApiKey
    if (athleteId.isBlank() || apiKey.isBlank()) return

    val client = IntervalsIcuClient(apiKey)
    // Show yesterday's-cached name immediately so the card isn't blank while
    // the network call is in flight (or if it fails offline).
    val cache = getApplication<Application>()
      .getSharedPreferences(PLANNED_CACHE_PREFS, Application.MODE_PRIVATE)
    val cachedToday = LocalDate.now().toString()
    val cachedName = cache.getString(KEY_PLANNED_NAME, null)
      ?.takeIf { cache.getString(KEY_PLANNED_DATE, "") == cachedToday }
    _uiState.value = _uiState.value.copy(
      plannedName = cachedName ?: _uiState.value.plannedName,
      plannedLoading = true,
      plannedError = null
    )

    scope.launch {
      // Sync FTP/weight from the athlete profile so targets stay current.
      runCatching { client.getAthlete(athleteId) }.getOrNull()?.let { athlete ->
        if (athlete.ftp != null || athlete.icu_weight != null) {
          profileRepository.updateProfile {
            it.copy(
              ftp = athlete.ftp ?: it.ftp,
              weightKg = athlete.icu_weight ?: it.weightKg
            )
          }
        }
      }

      val today = LocalDate.now().toString()
      val result = runCatching { client.getTodaysWorkoutEvents(athleteId, today) }
      result.onSuccess { events ->
        val event = events.firstOrNull()
        plannedEventId = event?.id
        val name = event?.name?.takeIf { it.isNotBlank() } ?: event?.let { "Planned workout" }
        cache.edit()
          .putString(KEY_PLANNED_NAME, name)
          .putString(KEY_PLANNED_DATE, cachedToday)
          .apply()
        // Pre-fetch the profile so the card can preview the interval shape and
        // Quick Start jumps straight in without a second round-trip.
        val workout = event?.id?.let { eventId ->
          runCatching {
            val zwo = client.downloadZwo(athleteId, eventId)
            WorkoutImporter.import("$eventId.zwo", zwo, profileRepository.getProfileSync().ftp)
          }.getOrNull()
        }
        _uiState.value = _uiState.value.copy(
          plannedName = name,
          plannedWorkout = workout,
          plannedLoading = false,
          plannedError = null
        )
      }.onFailure {
        _uiState.value = _uiState.value.copy(
          plannedLoading = false,
          plannedError = "Couldn't reach intervals.icu"
        )
      }
    }
  }

  /** Downloads + parses the planned workout, then emits it via [plannedWorkoutReady]. */
  fun startPlanned() {
    // Fast path: refreshIntervals already downloaded + parsed it for the preview.
    _uiState.value.plannedWorkout?.let {
      _plannedWorkoutReady.value = it
      return
    }
    val profile = profileRepository.getProfileSync()
    val athleteId = profile.intervalsIcuAthleteId
    val apiKey = profile.intervalsIcuApiKey
    val eventId = plannedEventId ?: return
    if (athleteId.isBlank() || apiKey.isBlank()) return

    _uiState.value = _uiState.value.copy(plannedLoading = true, plannedError = null)
    val client = IntervalsIcuClient(apiKey)
    scope.launch {
      runCatching {
        val zwo = client.downloadZwo(athleteId, eventId)
        WorkoutImporter.import("$eventId.zwo", zwo, profile.ftp)
      }.onSuccess { workout ->
        _uiState.value = _uiState.value.copy(plannedLoading = false)
        _plannedWorkoutReady.value = workout
      }.onFailure {
        _uiState.value = _uiState.value.copy(
          plannedLoading = false,
          plannedError = "Couldn't load planned workout"
        )
      }
    }
  }

  fun consumePlannedWorkout() {
    _plannedWorkoutReady.value = null
  }

  private companion object {
    const val PLANNED_CACHE_PREFS = "trainer_loop_planned_cache"
    const val KEY_PLANNED_NAME = "planned_name"
    const val KEY_PLANNED_DATE = "planned_date"
  }

  private fun bindTrainer(manager: FtmsManager?) {
    ftmsJob?.cancel()
    ftmsJob = null

    _uiState.value = _uiState.value.copy(
      connectedTrainer = manager?.device,
      isTrainerConnected = false,
      trainerBattery = null,
      trainerModel = null
    )

    manager ?: return

    ftmsJob = scope.launch {
      launch {
        manager.isConnected.collect { connected ->
          _uiState.value = _uiState.value.copy(isTrainerConnected = connected)
        }
      }
      launch {
        manager.batteryLevel.collect { battery ->
          _uiState.value = _uiState.value.copy(trainerBattery = battery)
        }
      }
      launch {
        manager.model.collect { model ->
          _uiState.value = _uiState.value.copy(trainerModel = model)
        }
      }
    }
  }

  private fun bindHr(manager: HrManager?) {
    hrJob?.cancel()
    hrJob = null

    _uiState.value = _uiState.value.copy(
      connectedHr = manager?.device,
      isHrConnected = false,
      latestHrBpm = null
    )

    manager ?: return

    hrJob = scope.launch {
      launch {
        manager.isConnected.collect { connected ->
          _uiState.value = _uiState.value.copy(isHrConnected = connected)
        }
      }
      launch {
        manager.heartRate.collect { bpm ->
          _uiState.value = _uiState.value.copy(latestHrBpm = bpm)
        }
      }
    }
  }
}
