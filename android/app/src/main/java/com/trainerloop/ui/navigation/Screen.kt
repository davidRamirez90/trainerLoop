package com.trainerloop.ui.navigation

sealed class Screen(val route: String) {
  // Bottom tabs
  object Home : Screen("home")
  object Workouts : Screen("workouts")
  object Ride : Screen("ride")
  object History : Screen("history")
  object Profile : Screen("profile")

  // Other flows
  object Devices : Screen("devices")
  object WorkoutDetail : Screen("workout_detail/{workoutId}") {
    fun createRoute(workoutId: String): String = "workout_detail/$workoutId"
  }
  object WorkoutPlayer : Screen("workout_player/{sessionId}") {
    fun createRoute(sessionId: Long): String = "workout_player/$sessionId"
  }
  object WorkoutComplete : Screen("workout_complete/{sessionId}/{workoutName}/{startTimeMs}") {
    fun createRoute(sessionId: String, workoutName: String, startTimeMs: Long): String {
      val encodedName = java.net.URLEncoder.encode(workoutName, "UTF-8")
      return "workout_complete/$sessionId/$encodedName/$startTimeMs"
    }
  }

  companion object {
    val bottomTabs = listOf(Home, Workouts, Ride, History, Profile)
  }
}
