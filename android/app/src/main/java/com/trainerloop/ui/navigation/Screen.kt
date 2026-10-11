package com.trainerloop.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
  // Primary destinations
  object Home : Screen("home")
  object Workouts : Screen("workouts")
  object History : Screen("history")
  object Profile : Screen("profile")

  // Other flows
  object Devices : Screen("devices")
  object SessionDetail : Screen("session_detail/{sessionId}") {
    fun createRoute(sessionId: String): String = "session_detail/${Uri.encode(sessionId)}"
  }
  object WorkoutDetail : Screen("workout_detail/{workoutId}") {
    fun createRoute(workoutId: String): String = "workout_detail/${Uri.encode(workoutId)}"
  }
  object WorkoutPlayer : Screen("workout_player/{sessionId}/{workoutId}") {
    fun createRoute(sessionId: Long, workoutId: String): String =
      "workout_player/$sessionId/${Uri.encode(workoutId)}"
  }
  object WorkoutComplete : Screen("workout_complete/{sessionId}/{workoutId}/{workoutName}/{startTimeMs}") {
    fun createRoute(sessionId: String, workoutId: String, workoutName: String, startTimeMs: Long): String {
      val encodedName = Uri.encode(workoutName)
      return "workout_complete/${Uri.encode(sessionId)}/${Uri.encode(workoutId)}/$encodedName/$startTimeMs"
    }
  }

  companion object {
    val bottomTabs = listOf(Home, Workouts, History)
  }
}
