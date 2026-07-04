package com.trainerloop.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.trainerloop.data.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

open class ProfileRepository(context: Context) {

  private val prefs: SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _profile = MutableStateFlow(load())
  open val profile: Flow<UserProfile> = _profile.asStateFlow()

  fun getProfileSync(): UserProfile = _profile.value

  suspend fun updateProfile(update: (UserProfile) -> UserProfile) {
    val current = _profile.value
    val updated = update(current)
    save(updated)
    _profile.value = updated
  }

  suspend fun updateFtp(ftp: Int) {
    updateProfile { it.copy(ftp = ftp) }
  }

  suspend fun updateName(name: String) {
    updateProfile { it.copy(name = name) }
  }

  suspend fun updateWeight(weightKg: Double) {
    updateProfile { it.copy(weightKg = weightKg) }
  }

  suspend fun updateMaxHr(maxHr: Int) {
    updateProfile { it.copy(maxHr = maxHr) }
  }

  suspend fun updateErgBias(bias: Int) {
    updateProfile { it.copy(ergBiasPct = bias) }
  }

  suspend fun selectCoachProfile(profileId: String) {
    updateProfile { it.copy(selectedCoachProfileId = profileId) }
  }

  suspend fun updateIntervalsIcuCredentials(athleteId: String, apiKey: String) {
    updateProfile { it.copy(intervalsIcuAthleteId = athleteId, intervalsIcuApiKey = apiKey) }
  }

  private fun load(): UserProfile {
    return UserProfile(
      name = prefs.getString(KEY_NAME, "Rider") ?: "Rider",
      ftp = prefs.getInt(KEY_FTP, 250),
      weightKg = prefs.getFloat(KEY_WEIGHT, 75.0f).toDouble(),
      maxHr = prefs.getInt(KEY_MAX_HR, 190),
      restingHr = prefs.getInt(KEY_RESTING_HR, 55),
      ergBiasPct = prefs.getInt(KEY_ERG_BIAS, 0),
      selectedCoachProfileId = prefs.getString(KEY_COACH_PROFILE, "default") ?: "default",
      intervalsIcuAthleteId = prefs.getString(KEY_INTERVALS_ATHLETE_ID, "") ?: "",
      intervalsIcuApiKey = prefs.getString(KEY_INTERVALS_API_KEY, "") ?: ""
    )
  }

  private fun save(profile: UserProfile) {
    prefs.edit()
      .putString(KEY_NAME, profile.name)
      .putInt(KEY_FTP, profile.ftp)
      .putFloat(KEY_WEIGHT, profile.weightKg.toFloat())
      .putInt(KEY_MAX_HR, profile.maxHr)
      .putInt(KEY_RESTING_HR, profile.restingHr)
      .putInt(KEY_ERG_BIAS, profile.ergBiasPct)
      .putString(KEY_COACH_PROFILE, profile.selectedCoachProfileId)
      .putString(KEY_INTERVALS_ATHLETE_ID, profile.intervalsIcuAthleteId)
      .putString(KEY_INTERVALS_API_KEY, profile.intervalsIcuApiKey)
      .apply()
  }

  companion object {
    private const val PREFS_NAME = "trainer_loop_profile"
    private const val KEY_FTP = "ftp"
    private const val KEY_WEIGHT = "weight_kg"
    private const val KEY_MAX_HR = "max_hr"
    private const val KEY_RESTING_HR = "resting_hr"
    private const val KEY_ERG_BIAS = "erg_bias"
    private const val KEY_COACH_PROFILE = "coach_profile"
    private const val KEY_NAME = "name"
    private const val KEY_INTERVALS_ATHLETE_ID = "intervals_icu_athlete_id"
    private const val KEY_INTERVALS_API_KEY = "intervals_icu_api_key"
  }
}
