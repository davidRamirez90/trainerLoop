package com.trainerloop.ble

/** One decoded frame from the Zwift Click's async / sync-TX characteristics. */
sealed interface ClickMessage {
  /** Pressed-state of both buttons; re-sent repeatedly while a button is held. */
  data class ButtonState(val plusPressed: Boolean, val minusPressed: Boolean) : ClickMessage
  data class Battery(val percent: Int) : ClickMessage
  /** "RideOn"-prefixed handshake acknowledgement (sync TX). */
  data object HandshakeAck : ClickMessage
  data object KeepAlive : ClickMessage
  data object Unknown : ClickMessage
}

/**
 * Zwift Click wire protocol. Community reverse-engineered, not vendor
 * published — a firmware update can break it. All knowledge of the format
 * is confined to this file; see the "Protocol Reference" section of
 * docs/plans/2026-07-10-zwift-click-shifter-plan.md for the source captures.
 *
 * Frames are one type byte followed by a protobuf whose fields are all
 * varints, so a full protobuf library is not needed. Button enum values are
 * inverted relative to intuition: 0 = pressed (ON), 1 = released (OFF).
 */
object ZwiftClickProtocol {
  val RIDE_ON = byteArrayOf(0x52, 0x69, 0x64, 0x65, 0x4F, 0x6E) // "RideOn"

  private const val MSG_KEEPALIVE = 0x15
  private const val MSG_BATTERY = 0x19
  private const val MSG_BUTTONS = 0x37

  private const val FIELD_BUTTON_PLUS = 1
  private const val FIELD_BUTTON_MINUS = 2
  private const val FIELD_BATTERY_LEVEL = 1
  private const val VALUE_PRESSED = 0L

  fun parse(bytes: ByteArray): ClickMessage {
    if (bytes.isEmpty()) return ClickMessage.Unknown
    if (isRideOnPrefixed(bytes)) return ClickMessage.HandshakeAck
    val payload = bytes.copyOfRange(1, bytes.size)
    return when (bytes[0].toInt() and 0xFF) {
      MSG_KEEPALIVE -> ClickMessage.KeepAlive
      MSG_BATTERY -> parseBattery(payload)
      MSG_BUTTONS -> parseButtons(payload)
      else -> ClickMessage.Unknown
    }
  }

  private fun isRideOnPrefixed(bytes: ByteArray): Boolean =
    bytes.size >= RIDE_ON.size && bytes.copyOfRange(0, RIDE_ON.size).contentEquals(RIDE_ON)

  private fun parseBattery(payload: ByteArray): ClickMessage {
    val fields = decodeVarintFields(payload) ?: return ClickMessage.Unknown
    val level = fields[FIELD_BATTERY_LEVEL] ?: return ClickMessage.Unknown
    return ClickMessage.Battery(level.toInt().coerceIn(0, 100))
  }

  private fun parseButtons(payload: ByteArray): ClickMessage {
    val fields = decodeVarintFields(payload) ?: return ClickMessage.Unknown
    // Observed frames always carry both fields explicitly. If one is missing
    // the frame is not what we expect — return Unknown instead of guessing,
    // because a phantom shift is worse than a dropped one.
    val plus = fields[FIELD_BUTTON_PLUS] ?: return ClickMessage.Unknown
    val minus = fields[FIELD_BUTTON_MINUS] ?: return ClickMessage.Unknown
    return ClickMessage.ButtonState(
      plusPressed = plus == VALUE_PRESSED,
      minusPressed = minus == VALUE_PRESSED
    )
  }

  /**
   * Minimal protobuf reader for messages made only of varint fields
   * (wire type 0). Returns field-number → value, or null on malformed
   * input or any non-varint wire type.
   */
  private fun decodeVarintFields(payload: ByteArray): Map<Int, Long>? {
    val fields = mutableMapOf<Int, Long>()
    var i = 0
    while (i < payload.size) {
      val tag = readVarint(payload, i) ?: return null
      val fieldNumber = (tag.value ushr 3).toInt()
      val wireType = (tag.value and 0x7).toInt()
      if (wireType != 0) return null
      val value = readVarint(payload, tag.nextIndex) ?: return null
      fields[fieldNumber] = value.value
      i = value.nextIndex
    }
    return fields
  }

  private data class Varint(val value: Long, val nextIndex: Int)

  private fun readVarint(bytes: ByteArray, start: Int): Varint? {
    var result = 0L
    var shift = 0
    var i = start
    while (i < bytes.size && shift < 64) {
      val b = bytes[i].toInt()
      result = result or ((b.toLong() and 0x7F) shl shift)
      i++
      if (b and 0x80 == 0) return Varint(result, i)
      shift += 7
    }
    return null
  }
}
