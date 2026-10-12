package com.trainerloop.ui.navigation

import androidx.navigation.NavHostController

/** Home is the root; restoring its saved stack would reopen the tab just left. */
internal fun NavHostController.navigateToTab(route: String) {
  if (currentDestination?.route == route) return
  if (route == Screen.Home.route) {
    popBackStack(Screen.Home.route, inclusive = false)
  } else {
    navigate(route) {
      popUpTo(Screen.Home.route) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }
}
