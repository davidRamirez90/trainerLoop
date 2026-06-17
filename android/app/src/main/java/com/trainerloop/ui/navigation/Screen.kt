package com.trainerloop.ui.navigation

sealed class Screen(val route: String) {
  object Library : Screen("library")
  object WorkoutPreview : Screen("workout_preview/{workoutId}") {
    fun createRoute(workoutId: String) = "workout_preview/$workoutId"
  }
  object Workout : Screen("workout/{sessionId}") {
    fun createRoute(sessionId: Int) = "workout/$sessionId"
  }
  object SessionSummary : Screen("session_summary/{sessionId}") {
    fun createRoute(sessionId: String) = "session_summary/$sessionId"
  }
  object Connect : Screen("connect")
  object Settings : Screen("settings")
}
