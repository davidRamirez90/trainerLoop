package com.trainerloop.ui.freeride

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.trainerloop.ble.FtmsControlManager
import com.trainerloop.ble.FtmsManager
import com.trainerloop.ble.HrManager
import com.trainerloop.data.model.Route
import com.trainerloop.data.model.UserProfile
import kotlinx.coroutines.flow.StateFlow

class FreeRideViewModelFactory(
  private val route: Route,
  private val routeId: String,
  private val ftmsManagerFlow: StateFlow<FtmsManager?>,
  private val hrManagerFlow: StateFlow<HrManager?>,
  private val ftmsControlManagerFlow: StateFlow<FtmsControlManager?>,
  private val userProfile: UserProfile
) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T =
    FreeRideViewModel(
      route = route,
      routeId = routeId,
      ftmsManagerFlow = ftmsManagerFlow,
      hrManagerFlow = hrManagerFlow,
      ftmsControlManagerFlow = ftmsControlManagerFlow,
      userProfile = userProfile
    ) as T
}
