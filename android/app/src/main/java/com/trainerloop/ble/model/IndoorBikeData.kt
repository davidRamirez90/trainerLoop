package com.trainerloop.ble.model

data class IndoorBikeData(
  val powerWatts: Int?,
  val cadenceRpm: Double?,
  val speedKph: Double?,
  val resistanceLevel: Int?,
  val averagePower: Int?,
  val averageSpeed: Double?,
  val totalDistanceMeters: Int?,
  val heartRateBpm: Int?,
  val elapsedTimeSec: Int?,
  val remainingTimeSec: Int?
)

object IndoorBikeDataParser {

  fun parse(bytes: ByteArray): IndoorBikeData? {
    if (bytes.size < 2) return null

    var offset = 0
    val flags = bytes.readUint16Le(offset)
    offset += 2
    com.trainerloop.ble.BleLog.d(
      "parse flags=0x${"%04X".format(flags)} size=${bytes.size}"
    )

    val hasAverageSpeed = flags and (1 shl 1) != 0
    val hasInstantCadence = flags and (1 shl 2) != 0
    val hasAverageCadence = flags and (1 shl 3) != 0
    val hasTotalDistance = flags and (1 shl 4) != 0
    val hasResistanceLevel = flags and (1 shl 5) != 0
    val hasInstantPower = flags and (1 shl 6) != 0
    val hasAveragePower = flags and (1 shl 7) != 0
    val hasExpendedEnergy = flags and (1 shl 8) != 0
    val hasHeartRate = flags and (1 shl 9) != 0
    val hasMet = flags and (1 shl 10) != 0
    val hasElapsedTime = flags and (1 shl 11) != 0
    val hasRemainingTime = flags and (1 shl 12) != 0

    // Instantaneous Speed is absent when the FTMS "More Data" bit is set.
    val speedKph = if (flags and 1 == 0) {
      if (offset + 2 > bytes.size) return null
      val value = bytes.readUint16Le(offset) / 100.0
      offset += 2
      value
    } else null

    val averageSpeed = if (hasAverageSpeed) {
      if (offset + 2 > bytes.size) return null
      val value = bytes.readUint16Le(offset) / 100.0
      offset += 2
      value
    } else null

    val cadenceRpm = if (hasInstantCadence) {
      if (offset + 2 > bytes.size) return null
      val value = bytes.readUint16Le(offset) / 2.0
      offset += 2
      value
    } else null

    if (hasAverageCadence) {
      if (offset + 2 > bytes.size) return null
      offset += 2
    }

    val totalDistanceMeters = if (hasTotalDistance) {
      if (offset + 3 > bytes.size) return null
      val value = bytes.readUint24Le(offset)
      offset += 3
      value
    } else null

    val resistanceLevel = if (hasResistanceLevel) {
      if (offset + 2 > bytes.size) return null
      val raw = bytes.readInt16Le(offset)
      offset += 2
      // 0.1 resolution, round to nearest integer
      kotlin.math.round(raw / 10.0).toInt()
    } else null

    val powerWatts = if (hasInstantPower) {
      if (offset + 2 > bytes.size) return null
      val value = bytes.readInt16Le(offset)
      offset += 2
      value
    } else null

    val averagePower = if (hasAveragePower) {
      if (offset + 2 > bytes.size) return null
      val value = bytes.readInt16Le(offset)
      offset += 2
      value
    } else null

    // Skip Expended Energy (5 bytes: uint16 total + uint16 per hour + uint8 per minute)
    if (hasExpendedEnergy) {
      if (offset + 5 > bytes.size) return null
      offset += 5
    }

    val heartRateBpm = if (hasHeartRate) {
      if (offset + 1 > bytes.size) return null
      val value = bytes[offset].toInt() and 0xFF
      offset += 1
      value
    } else null

    // Skip MET (uint8, 0.1 kcal/(kg*h))
    if (hasMet) {
      if (offset + 1 > bytes.size) return null
      offset += 1
    }

    val elapsedTimeSec = if (hasElapsedTime) {
      if (offset + 2 > bytes.size) return null
      val value = bytes.readUint16Le(offset)
      offset += 2
      value
    } else null

    val remainingTimeSec = if (hasRemainingTime) {
      if (offset + 2 > bytes.size) return null
      val value = bytes.readUint16Le(offset)
      offset += 2
      value
    } else null

    val data = IndoorBikeData(
      powerWatts = powerWatts,
      cadenceRpm = cadenceRpm,
      speedKph = speedKph,
      resistanceLevel = resistanceLevel,
      averagePower = averagePower,
      averageSpeed = averageSpeed,
      totalDistanceMeters = totalDistanceMeters,
      heartRateBpm = heartRateBpm,
      elapsedTimeSec = elapsedTimeSec,
      remainingTimeSec = remainingTimeSec
    )
    com.trainerloop.ble.BleLog.d(
      "parsed power=${powerWatts} cad=${cadenceRpm} spd=${speedKph} hr=${heartRateBpm}"
    )
    return data
  }
}

internal fun ByteArray.readUint16Le(offset: Int): Int {
  return (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)
}

internal fun ByteArray.readInt16Le(offset: Int): Int {
  val low = this[offset].toInt() and 0xFF
  val high = this[offset + 1].toInt()
  return if (high and 0x80 != 0) {
    low or ((high and 0x7F) shl 8) - 0x8000
  } else {
    low or (high shl 8)
  }
}

internal fun ByteArray.readUint24Le(offset: Int): Int {
  return (this[offset].toInt() and 0xFF) or
    ((this[offset + 1].toInt() and 0xFF) shl 8) or
    ((this[offset + 2].toInt() and 0xFF) shl 16)
}
