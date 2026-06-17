package com.trainerloop.ble.model

import java.util.UUID

data class BleDevice(
  val address: String,
  val name: String?,
  val services: List<UUID>,
  val rssi: Int
)
