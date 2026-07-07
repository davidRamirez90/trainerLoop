package com.trainerloop.ui.navigation

sealed class Screen(val route: String) {
  // Bottom tabs
  object Home : Screen("home")
  object Workouts : Screen("workouts")
  object History : Screen("history")
  object Profile : Screen("profile")

  // Other flows
  object Devices : Screen("devices")
  object WorkoutBuilder : Screen("workout_builder")
  object SessionDetail : Screen("session_detail/{sessionId}") {
    fun createRoute(sessionId: String): String = "session_detail/$sessionId"
  }
  object WorkoutDetail : Screen("workout_detail/{workoutId}") {
    fun createRoute(workoutId: String): String = "workout_detail/$workoutId"
  }
  object WorkoutPlayer : Screen("workout_player/{sessionId}") {
    fun createRoute(sessionId: Long): String = "workout_player/$sessionId"
  }
  object WorkoutComplete : Screen("workout_complete/{sessionId}/{workoutId}/{workoutName}/{startTimeMs}") {
    fun createRoute(sessionId: String, workoutId: String, workoutName: String, startTimeMs: Long): String {
      val encodedName = java.net.URLEncoder.encode(workoutName, "UTF-8")
      return "workout_complete/$sessionId/$workoutId/$encodedName/$startTimeMs"
    }
  }

  object Routes : Screen("routes")
  object RouteDetail : Screen("route_detail/{routeId}") {
    fun createRoute(routeId: String): String = "route_detail/$routeId"
  }
  object FreeRide : Screen("free_ride/{routeId}") {
    fun createRoute(routeId: String): String = "free_ride/$routeId"
  }

  companion object {
    val bottomTabs = listOf(Home, Workouts, History, Profile)
  }
}
