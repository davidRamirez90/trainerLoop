package com.trainerloop.data.model

data class UserProfile(
  val name: String = "Rider",
  val ftp: Int = 250,
  val weightKg: Double = 75.0,
  val maxHr: Int = 190,
  val restingHr: Int = 55,
  val ergBiasPct: Int = 0,
  val selectedCoachProfileId: String = "default",
  val intervalsIcuAthleteId: String = "",
  val intervalsIcuApiKey: String = ""
)
