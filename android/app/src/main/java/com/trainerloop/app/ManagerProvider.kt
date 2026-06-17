package com.trainerloop.app

import com.trainerloop.ble.FtmsManager
import com.trainerloop.ble.HrManager
import kotlinx.coroutines.flow.StateFlow

interface ManagerProvider {
  val ftmsManager: StateFlow<FtmsManager?>
  val hrManager: StateFlow<HrManager?>
}
