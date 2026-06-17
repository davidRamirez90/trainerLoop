package com.trainerloop.ble.model

object HeartRateMeasurementParser {

  fun parse(bytes: ByteArray): Int? {
    if (bytes.size < 2) return null
    val flags = bytes[0].toInt() and 0xFF
    val isUint16 = flags and 0x01 != 0
    return if (isUint16) {
      if (bytes.size < 3) return null
      (bytes[1].toInt() and 0xFF) or ((bytes[2].toInt() and 0xFF) shl 8)
    } else {
      bytes[1].toInt() and 0xFF
    }
  }
}
