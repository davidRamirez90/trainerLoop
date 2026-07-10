package com.trainerloop.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ZwiftClickProtocolTest {

  @Test
  fun `RIDE_ON is ascii RideOn`() {
    assertArrayEquals("RideOn".toByteArray(Charsets.US_ASCII), ZwiftClickProtocol.RIDE_ON)
  }

  @Test
  fun `handshake response is recognised by RideOn prefix`() {
    // "RideOn" + response start 01 03 + 4 fake public-key bytes
    val frame = "RideOn".toByteArray() + byteArrayOf(0x01, 0x03, 0x0A, 0x0B, 0x0C, 0x0D)
    assertEquals(ClickMessage.HandshakeAck, ZwiftClickProtocol.parse(frame))
  }

  @Test
  fun `plus pressed frame parses`() {
    val frame = byteArrayOf(0x37, 0x08, 0x00, 0x10, 0x01)
    assertEquals(
      ClickMessage.ButtonState(plusPressed = true, minusPressed = false),
      ZwiftClickProtocol.parse(frame)
    )
  }

  @Test
  fun `minus pressed frame parses`() {
    val frame = byteArrayOf(0x37, 0x08, 0x01, 0x10, 0x00)
    assertEquals(
      ClickMessage.ButtonState(plusPressed = false, minusPressed = true),
      ZwiftClickProtocol.parse(frame)
    )
  }

  @Test
  fun `both released frame parses`() {
    val frame = byteArrayOf(0x37, 0x08, 0x01, 0x10, 0x01)
    assertEquals(
      ClickMessage.ButtonState(plusPressed = false, minusPressed = false),
      ZwiftClickProtocol.parse(frame)
    )
  }

  @Test
  fun `both pressed frame parses`() {
    val frame = byteArrayOf(0x37, 0x08, 0x00, 0x10, 0x00)
    assertEquals(
      ClickMessage.ButtonState(plusPressed = true, minusPressed = true),
      ZwiftClickProtocol.parse(frame)
    )
  }

  @Test
  fun `button fields in reverse order still parse`() {
    val frame = byteArrayOf(0x37, 0x10, 0x01, 0x08, 0x00)
    assertEquals(
      ClickMessage.ButtonState(plusPressed = true, minusPressed = false),
      ZwiftClickProtocol.parse(frame)
    )
  }

  @Test
  fun `battery frame parses`() {
    val frame = byteArrayOf(0x19, 0x08, 0x4B) // field 1 varint = 75
    assertEquals(ClickMessage.Battery(75), ZwiftClickProtocol.parse(frame))
  }

  @Test
  fun `keepalive frame parses`() {
    assertEquals(ClickMessage.KeepAlive, ZwiftClickProtocol.parse(byteArrayOf(0x15)))
  }

  @Test
  fun `unknown message type returns Unknown`() {
    // 0x07 is the Zwift Play controller notification — out of scope, must not crash
    val frame = byteArrayOf(0x07, 0x08, 0x00)
    assertEquals(ClickMessage.Unknown, ZwiftClickProtocol.parse(frame))
  }

  @Test
  fun `empty frame returns Unknown`() {
    assertEquals(ClickMessage.Unknown, ZwiftClickProtocol.parse(byteArrayOf()))
  }

  @Test
  fun `truncated button frame returns Unknown`() {
    // tag byte present, value byte missing
    assertEquals(ClickMessage.Unknown, ZwiftClickProtocol.parse(byteArrayOf(0x37, 0x08)))
  }

  @Test
  fun `button frame missing a field returns Unknown`() {
    // only Button_Plus present — real frames always carry both fields; treat
    // deviation as unknown rather than guessing (a phantom shift is worse
    // than a dropped one)
    assertEquals(ClickMessage.Unknown, ZwiftClickProtocol.parse(byteArrayOf(0x37, 0x08, 0x00)))
  }
}
