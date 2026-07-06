package com.trainerloop.data.model

data class UserProfile(
  val name: String = "Rider",
  val ftp: Int = 250,
  val weightKg: Double = 75.0,
  val maxHr: Int = 190,
  val restingHr: Int = 55,
  /** Lactate-threshold HR; when absent the coach estimates it from maxHr. */
  val lthr: Int? = null,
  val ergBiasPct: Int = 0,
  val selectedCoachProfileId: String = "default",
  /** Master switch for coach feedback (live cards, TTS, suggestions). */
  val coachEnabled: Boolean = true,
  val intervalsIcuAthleteId: String = "",
  val intervalsIcuApiKey: String = ""
)
