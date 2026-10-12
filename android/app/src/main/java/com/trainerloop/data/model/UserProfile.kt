package com.trainerloop.data.model

data class UserProfile(
  val name: String = "Rider",
  val ftp: Int = 250,
  val weightKg: Double = 75.0,
  val maxHr: Int = 190,
  val ergBiasPct: Int = 0,
  val completionSoundEnabled: Boolean = false,
  val intervalsIcuAthleteId: String = "",
  val intervalsIcuApiKey: String = "",
  /** Virtual ride simulation (route overlay + physics speed) during workouts. */
  val virtualRideEnabled: Boolean = true,
  val bikeWeightKg: Double = 8.0,
  val rollingResistanceCrr: Double = 0.005,
  val dragAreaCda: Double = 0.32,
)
