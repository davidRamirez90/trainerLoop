# Zwift Click Physical Shifter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a rider shift the app's existing 14 virtual gears with a paired Zwift Click over BLE, as a third input source alongside the on-screen ▲/▼ buttons and volume keys.

**Architecture:** A new `ZwiftClickManager` in `com.trainerloop.ble` mirrors `HrManager` (own `BleConnection`, own peripheral). Pure protocol logic (frame parsing, handshake bytes, press-edge detection) lives in a separate `ZwiftClickProtocol.kt` so it is JVM-unit-testable without Bluetooth. The manager exposes a `SharedFlow<ClickShift>`; `FreeRideViewModel` collects it and forwards to its existing `shiftUp()`/`shiftDown()` — no changes to `VirtualDrivetrain`, physics, or the ERG path. `TrainerLoopApplication` owns the manager lifecycle exactly as it does for HR, and the Devices screen gains a "Controllers" section.

**Tech Stack:** Kotlin, Android BLE (existing `BleConnection`/`GattCallback` plumbing), Kotlin coroutines/Flow, JUnit 4 + MockK + kotlinx-coroutines-test. **No new dependencies** — the Click's protobuf payloads are two varint fields, decoded by ~30 lines of hand-rolled code instead of a protobuf library.

## Global Constraints

- No new Gradle dependencies.
- 2-space indentation, match existing file style (see any file in `app/src/main/java/com/trainerloop/ble/`).
- minSdk 30, compileSdk 35; guard Tiramisu-only APIs as `BleConnection` already does (you won't need to — reuse `BleConnection`, never call `BluetoothGatt` directly).
- All file paths below are relative to `android/` (the Gradle root). Run all Gradle commands from `android/`.
- No Zwift Play / Zwift Ride / Click v2 support (different message types 0x07/0x23, Ride uses service `FC82`). Out of scope.
- No press-and-hold auto-repeat (one press = one shift). Out of scope for this iteration.
- No manifest changes — BLUETOOTH_SCAN/CONNECT permissions already cover this.
- Existing UI shifters (buttons, volume keys) must be untouched and keep working; with no Click paired the app must behave exactly as today.

---

## Protocol Reference (community reverse-engineered)

Everything below was verified against the OpenBikeControl/bikecontrol source
(`lib/bluetooth/devices/zwift/constants.dart`, `zwift_device.dart`,
`zwift_click.dart`, `prop_public/lib/protocol/zwift.pb.dart`). It is **not
vendor-published** and can change with a Click firmware update — which is why
Task 7 (hardware verification) is mandatory before calling this feature done,
and why all protocol knowledge is confined to `ZwiftClickProtocol.kt` +
`ZwiftClickManager.kt`.

**GATT layout** (Zwift Click v1, a.k.a. "BC1"; advertises local name `Zwift Click`):

| Item | UUID | Role |
|---|---|---|
| Custom service | `00000001-19CA-4651-86E5-FA29DCDD09D1` | advertised → usable as a scan filter |
| Async characteristic | `00000002-19CA-4651-86E5-FA29DCDD09D1` | **notify** — button/battery/keepalive frames |
| Sync RX characteristic | `00000003-19CA-4651-86E5-FA29DCDD09D1` | **write** — we send the handshake here |
| Sync TX characteristic | `00000004-19CA-4651-86E5-FA29DCDD09D1` | **indicate** — handshake response |
| Battery service | standard `0x180F` / `0x2A19` | already in `BleConstants` |

**Handshake (unencrypted mode):**
1. Subscribe to Async (notify) and Sync TX (indicate).
2. Write ASCII `"RideOn"` = `52 69 64 65 4F 6E` to Sync RX (write-without-response preferred).
3. Device replies on Sync TX with `"RideOn"` + `01 03` + its public-key bytes. We only need the `RideOn` prefix as the ack; ignore the rest (the key is for the encrypted mode Zwift's own app uses — the Click keeps working unencrypted).

**Data frames** (on Async after handshake): byte 0 is a message type, remainder is a tiny protobuf:

| Type | Meaning | Payload |
|---|---|---|
| `0x15` | keepalive/empty | ignore |
| `0x19` | battery level | protobuf field 1 varint = percent (frame: `19 08 <level>`) |
| `0x37` | Click keypad status | protobuf `ClickKeyPadStatus { Button_Plus = 1; Button_Minus = 2; }`, enum values **ON = 0 (pressed), OFF = 1 (released)**. Typical frame: `37 08 00 10 01` = plus pressed, minus released |

The Click re-sends the same button state several times per press and while
held, so shift events must be derived from released→pressed **edges** — that
edge detection is also the debounce.

---

### Task 1: Protocol constants and frame parser

**Files:**
- Modify: `app/src/main/java/com/trainerloop/ble/BleConstants.kt`
- Create: `app/src/main/java/com/trainerloop/ble/ZwiftClickProtocol.kt`
- Test: `app/src/test/java/com/trainerloop/ble/ZwiftClickProtocolTest.kt`

**Interfaces:**
- Consumes: nothing (pure Kotlin).
- Produces:
  - `BleConstants.ZWIFT_CLICK_SERVICE`, `ZWIFT_CLICK_ASYNC`, `ZWIFT_CLICK_SYNC_RX`, `ZWIFT_CLICK_SYNC_TX: UUID`
  - `sealed interface ClickMessage` with `ButtonState(plusPressed: Boolean, minusPressed: Boolean)`, `Battery(percent: Int)`, `HandshakeAck`, `KeepAlive`, `Unknown`
  - `ZwiftClickProtocol.RIDE_ON: ByteArray`
  - `ZwiftClickProtocol.parse(bytes: ByteArray): ClickMessage`

- [x] **Step 1: Add the UUID constants**

Append to the bottom of the `BleConstants` object in `app/src/main/java/com/trainerloop/ble/BleConstants.kt`:

```kotlin
  // Zwift Click — proprietary controller service (community reverse-engineered,
  // see docs/plans/2026-07-10-zwift-click-shifter-plan.md "Protocol Reference").
  val ZWIFT_CLICK_SERVICE = UUID.fromString("00000001-19CA-4651-86E5-FA29DCDD09D1")
  val ZWIFT_CLICK_ASYNC = UUID.fromString("00000002-19CA-4651-86E5-FA29DCDD09D1")
  val ZWIFT_CLICK_SYNC_RX = UUID.fromString("00000003-19CA-4651-86E5-FA29DCDD09D1")
  val ZWIFT_CLICK_SYNC_TX = UUID.fromString("00000004-19CA-4651-86E5-FA29DCDD09D1")
```

- [x] **Step 2: Write the failing tests**

Create `app/src/test/java/com/trainerloop/ble/ZwiftClickProtocolTest.kt`:

```kotlin
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
```

- [x] **Step 3: Run the tests to verify they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "com.trainerloop.ble.ZwiftClickProtocolTest"
```

Expected: FAIL — `Unresolved reference: ZwiftClickProtocol` (compilation error).

- [x] **Step 4: Implement the protocol module**

Create `app/src/main/java/com/trainerloop/ble/ZwiftClickProtocol.kt`:

```kotlin
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
```

- [x] **Step 5: Run the tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.trainerloop.ble.ZwiftClickProtocolTest"
```

Expected: PASS (13 tests).

- [x] **Step 6: Commit**

```bash
git add app/src/main/java/com/trainerloop/ble/BleConstants.kt \
        app/src/main/java/com/trainerloop/ble/ZwiftClickProtocol.kt \
        app/src/test/java/com/trainerloop/ble/ZwiftClickProtocolTest.kt
git commit -m "feat(ble): Zwift Click protocol constants and frame parser"
```

---

### Task 2: Press-edge shift detector

**Files:**
- Create: `app/src/main/java/com/trainerloop/ble/ClickShiftDetector.kt`
- Test: `app/src/test/java/com/trainerloop/ble/ClickShiftDetectorTest.kt`

**Interfaces:**
- Consumes: `ClickMessage.ButtonState` from Task 1.
- Produces:
  - `enum class ClickShift { UP, DOWN }`
  - `class ClickShiftDetector` with `fun onState(state: ClickMessage.ButtonState): List<ClickShift>` and `fun reset()`

- [x] **Step 1: Write the failing tests**

Create `app/src/test/java/com/trainerloop/ble/ClickShiftDetectorTest.kt`:

```kotlin
package com.trainerloop.ble

import org.junit.Assert.assertEquals
import org.junit.Test

class ClickShiftDetectorTest {

  private val detector = ClickShiftDetector()

  private fun state(plus: Boolean = false, minus: Boolean = false) =
    ClickMessage.ButtonState(plusPressed = plus, minusPressed = minus)

  @Test
  fun `plus press emits UP once`() {
    assertEquals(listOf(ClickShift.UP), detector.onState(state(plus = true)))
  }

  @Test
  fun `repeated pressed frames do not re-emit`() {
    detector.onState(state(plus = true))
    assertEquals(emptyList<ClickShift>(), detector.onState(state(plus = true)))
    assertEquals(emptyList<ClickShift>(), detector.onState(state(plus = true)))
  }

  @Test
  fun `release then press emits again`() {
    detector.onState(state(plus = true))
    detector.onState(state())
    assertEquals(listOf(ClickShift.UP), detector.onState(state(plus = true)))
  }

  @Test
  fun `minus press emits DOWN`() {
    assertEquals(listOf(ClickShift.DOWN), detector.onState(state(minus = true)))
  }

  @Test
  fun `release frames emit nothing`() {
    assertEquals(emptyList<ClickShift>(), detector.onState(state()))
  }

  @Test
  fun `simultaneous press emits both`() {
    assertEquals(
      listOf(ClickShift.UP, ClickShift.DOWN),
      detector.onState(state(plus = true, minus = true))
    )
  }

  @Test
  fun `reset forgets held buttons`() {
    detector.onState(state(plus = true))
    detector.reset()
    // After a reconnect the first frame may still say "pressed"; it must
    // count as a fresh press, not be swallowed as a stale repeat.
    assertEquals(listOf(ClickShift.UP), detector.onState(state(plus = true)))
  }
}
```

- [x] **Step 2: Run the tests to verify they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "com.trainerloop.ble.ClickShiftDetectorTest"
```

Expected: FAIL — `Unresolved reference: ClickShiftDetector`.

- [x] **Step 3: Implement the detector**

Create `app/src/main/java/com/trainerloop/ble/ClickShiftDetector.kt`:

```kotlin
package com.trainerloop.ble

enum class ClickShift { UP, DOWN }

/**
 * Converts repeated [ClickMessage.ButtonState] frames into discrete shift
 * events. The Click re-sends the same state many times per press and while a
 * button is held; only a released→pressed transition emits an event, which
 * also serves as the debounce. Not thread-safe — call from one dispatcher
 * (the manager collects all notification flows on Dispatchers.Main).
 */
class ClickShiftDetector {
  private var plusWasPressed = false
  private var minusWasPressed = false

  fun onState(state: ClickMessage.ButtonState): List<ClickShift> {
    val events = buildList {
      if (state.plusPressed && !plusWasPressed) add(ClickShift.UP)
      if (state.minusPressed && !minusWasPressed) add(ClickShift.DOWN)
    }
    plusWasPressed = state.plusPressed
    minusWasPressed = state.minusPressed
    return events
  }

  /** Call on reconnect so a stale "pressed" memory can't swallow a real press. */
  fun reset() {
    plusWasPressed = false
    minusWasPressed = false
  }
}
```

- [x] **Step 4: Run the tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.trainerloop.ble.ClickShiftDetectorTest"
```

Expected: PASS (7 tests).

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/trainerloop/ble/ClickShiftDetector.kt \
        app/src/test/java/com/trainerloop/ble/ClickShiftDetectorTest.kt
git commit -m "feat(ble): Zwift Click press-edge shift detector"
```

---

### Task 3: ZwiftClickManager

**Files:**
- Create: `app/src/main/java/com/trainerloop/ble/ZwiftClickManager.kt`

**Interfaces:**
- Consumes: `BleConnection` (existing: `connect()`, `discoverServices()`, `getCharacteristic()`, `enableNotifications()`, `writeCharacteristic()`, `read()`, `addReconnectHandler()`, `disconnect()`), `ZwiftClickProtocol.parse` + `RIDE_ON` (Task 1), `ClickShiftDetector`/`ClickShift` (Task 2), `BleConstants` UUIDs (Task 1).
- Produces (used by Tasks 4–6):
  - `class ZwiftClickManager(context: Context, val device: BluetoothDevice)`
  - `val shiftEvents: SharedFlow<ClickShift>`
  - `val batteryLevel: StateFlow<Int?>`
  - `val isConnected: StateFlow<Boolean>`
  - `suspend fun connect(): Result<Unit>`
  - `suspend fun disconnect()`

There is no unit test for this class — it is a thin orchestration layer over
Android Bluetooth objects that can't be constructed on the JVM, matching the
existing precedent (`HrManager`/`FtmsManager` have no unit tests; their parsers
do). All decodable logic already lives in Tasks 1–2. Real-device behaviour is
covered by Task 7.

- [x] **Step 1: Implement the manager**

Create `app/src/main/java/com/trainerloop/ble/ZwiftClickManager.kt`:

```kotlin
package com.trainerloop.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Zwift Click BLE client. The Click is its own peripheral, so unlike the
 * FTMS managers this owns a private [BleConnection] (same pattern as
 * [HrManager]). Performs the proprietary RideOn handshake and turns button
 * notifications into [ClickShift] events — see [ZwiftClickProtocol] for the
 * wire format and its provenance.
 */
class ZwiftClickManager(
  private val context: Context,
  val device: BluetoothDevice
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var connection: BleConnection? = null
  private val shiftDetector = ClickShiftDetector()

  // extraBufferCapacity so tryEmit from the notification collector never
  // drops a shift while the ViewModel collector is momentarily busy.
  private val _shiftEvents = MutableSharedFlow<ClickShift>(extraBufferCapacity = 16)
  val shiftEvents: SharedFlow<ClickShift> = _shiftEvents.asSharedFlow()

  private val _batteryLevel = MutableStateFlow<Int?>(null)
  val batteryLevel: StateFlow<Int?> = _batteryLevel.asStateFlow()

  private val _isConnected = MutableStateFlow(false)
  val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

  suspend fun connect(): Result<Unit> {
    BleLog.d("ZwiftClickManager.connect device=${device.address}")
    val conn = BleConnection(context, device)
    connection = conn

    conn.connect().getOrElse {
      BleLog.e("ZwiftClickManager.connect: connect() failed: ${it.message}")
      return Result.failure(it)
    }
    conn.discoverServices().getOrElse {
      BleLog.e("ZwiftClickManager.connect: discoverServices failed: ${it.message}")
      return Result.failure(it)
    }
    subscribeAndHandshake(conn).getOrElse {
      BleLog.e("ZwiftClickManager.connect: handshake failed: ${it.message}")
      conn.disconnect()
      connection = null
      return Result.failure(it)
    }

    // Auto re-handshake on reconnect. Service discovery is re-run once by
    // BleConnection before handlers fire; notification channels were closed
    // on the drop, so re-arming creates fresh collectors (GattCallback
    // replaces the per-characteristic channel — old collectors end cleanly).
    conn.addReconnectHandler {
      shiftDetector.reset()
      subscribeAndHandshake(conn)
        .onFailure { BleLog.e("Zwift Click re-handshake failed: ${it.message}") }
    }

    _isConnected.value = true
    BleLog.d("ZwiftClickManager.connect success")
    return Result.success(Unit)
  }

  private suspend fun subscribeAndHandshake(conn: BleConnection): Result<Unit> {
    val asyncChar = conn.getCharacteristic(
      BleConstants.ZWIFT_CLICK_SERVICE, BleConstants.ZWIFT_CLICK_ASYNC
    )
    val syncTxChar = conn.getCharacteristic(
      BleConstants.ZWIFT_CLICK_SERVICE, BleConstants.ZWIFT_CLICK_SYNC_TX
    )
    val syncRxChar = conn.getCharacteristic(
      BleConstants.ZWIFT_CLICK_SERVICE, BleConstants.ZWIFT_CLICK_SYNC_RX
    )
    if (asyncChar == null || syncTxChar == null || syncRxChar == null) {
      return Result.failure(
        Exception(
          "Zwift Click service/characteristics not found — " +
            "device may need a firmware update via the Zwift Companion app"
        )
      )
    }

    val handshakeAck = CompletableDeferred<Unit>()

    // Arm both notification sources BEFORE writing RideOn so the ack (an
    // indication on sync TX) cannot be missed.
    collectFrames(conn.enableNotifications(asyncChar), handshakeAck)
    collectFrames(conn.enableNotifications(syncTxChar), handshakeAck)

    // The Click's sync RX is write-without-response; fall back to a
    // response write if a future firmware drops the no-response property.
    val withResponse =
      syncRxChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE == 0
    conn.writeCharacteristic(syncRxChar, ZwiftClickProtocol.RIDE_ON, withResponse)
      .getOrElse { return Result.failure(it) }

    withTimeoutOrNull(HANDSHAKE_TIMEOUT_MS) { handshakeAck.await() }
      ?: return Result.failure(Exception("Zwift Click RideOn handshake timed out"))

    // Standard battery service (0x180F) seeds the level; 0x19 notification
    // frames keep it fresh afterwards. Null (service absent) is fine.
    scope.launch {
      conn.read(BleConstants.BATTERY_SERVICE, BleConstants.BATTERY_LEVEL) {
        if (it.isEmpty()) null else it[0].toInt() and 0xFF
      }?.let { _batteryLevel.value = it }
    }
    return Result.success(Unit)
  }

  private fun collectFrames(flow: Flow<ByteArray>, handshakeAck: CompletableDeferred<Unit>) {
    scope.launch {
      try {
        flow.collect { bytes -> onFrame(bytes, handshakeAck) }
      } catch (t: Throwable) {
        BleLog.e("Zwift Click notification collector crashed", t)
      }
    }
  }

  private fun onFrame(bytes: ByteArray, handshakeAck: CompletableDeferred<Unit>) {
    when (val message = ZwiftClickProtocol.parse(bytes)) {
      is ClickMessage.HandshakeAck -> {
        BleLog.d("Zwift Click handshake acknowledged")
        handshakeAck.complete(Unit)
      }
      is ClickMessage.ButtonState -> {
        shiftDetector.onState(message).forEach { shift ->
          BleLog.d("Zwift Click shift $shift")
          _shiftEvents.tryEmit(shift)
        }
      }
      is ClickMessage.Battery -> _batteryLevel.value = message.percent
      ClickMessage.KeepAlive -> {}
      ClickMessage.Unknown -> BleLog.w(
        "Zwift Click unknown frame: " +
          bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
      )
    }
  }

  suspend fun disconnect() {
    BleLog.d("ZwiftClickManager.disconnect device=${device.address}")
    connection?.disconnect()
    connection = null
    _isConnected.value = false
    _batteryLevel.value = null
    scope.cancel()
  }

  companion object {
    private const val HANDSHAKE_TIMEOUT_MS = 5_000L
  }
}
```

- [x] **Step 2: Verify it compiles (unit tests also still pass)**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all existing tests plus Tasks 1–2 tests pass.

- [x] **Step 3: Commit**

```bash
git add app/src/main/java/com/trainerloop/ble/ZwiftClickManager.kt
git commit -m "feat(ble): ZwiftClickManager with RideOn handshake and shift events"
```

---

### Task 4: Application-level manager ownership

**Files:**
- Modify: `app/src/main/java/com/trainerloop/app/TrainerLoopApplication.kt`

**Interfaces:**
- Consumes: `ZwiftClickManager` (Task 3).
- Produces (used by Tasks 5–6):
  - `TrainerLoopApplication.clickManager: StateFlow<ZwiftClickManager?>`
  - `fun attachClick(device: BluetoothDevice)`
  - `fun clearClick()`
  - `clearDevices()` also clears the Click.

Do **not** touch `ManagerProvider` — it only exists for the ftms/hr pairs that
other components consume through the interface; everything Click-related reads
the concrete `trainerLoopApp` like `ftmsControlManager` already does.

- [x] **Step 1: Add the manager StateFlow and attach/clear functions**

In `app/src/main/java/com/trainerloop/app/TrainerLoopApplication.kt`:

Add import:

```kotlin
import com.trainerloop.ble.ZwiftClickManager
```

Below the `_ftmsControlManager` property, add:

```kotlin
  private val _clickManager = MutableStateFlow<ZwiftClickManager?>(null)
  val clickManager: StateFlow<ZwiftClickManager?> = _clickManager.asStateFlow()
```

Below `clearHr()`, add (same swap-then-async-dispose pattern as `attachHr`/`clearHr`):

```kotlin
  fun attachClick(device: BluetoothDevice) {
    val previousClick = _clickManager.value
    _clickManager.value = ZwiftClickManager(this, device)
    appScope.launch {
      previousClick?.disconnect()
    }
  }

  fun clearClick() {
    val previousClick = _clickManager.value
    _clickManager.value = null
    appScope.launch {
      previousClick?.disconnect()
    }
  }
```

- [x] **Step 2: Include the Click in clearDevices()**

Replace the body of `clearDevices()` with:

```kotlin
  fun clearDevices() {
    val previousFtms = _ftmsManager.value
    val previousControl = _ftmsControlManager.value
    val previousHr = _hrManager.value
    val previousClick = _clickManager.value
    val previousConn = trainerConnection
    _ftmsManager.value = null
    _ftmsControlManager.value = null
    _hrManager.value = null
    _clickManager.value = null
    trainerConnection = null
    appScope.launch {
      previousFtms?.disconnect()
      previousControl?.disconnect()
      previousHr?.disconnect()
      previousClick?.disconnect()
      previousConn?.disconnect()
    }
  }
```

- [x] **Step 3: Verify compilation**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [x] **Step 4: Commit**

```bash
git add app/src/main/java/com/trainerloop/app/TrainerLoopApplication.kt
git commit -m "feat(app): own ZwiftClickManager lifecycle in application"
```

---

### Task 5: Devices screen — scan, connect, battery

**Files:**
- Modify: `app/src/main/java/com/trainerloop/ui/devices/DevicesViewModel.kt`
- Modify: `app/src/main/java/com/trainerloop/ui/devices/DevicesScreen.kt`

**Interfaces:**
- Consumes: `TrainerLoopApplication.attachClick`/`clearClick`/`clickManager` (Task 4), `ZwiftClickManager.connect`/`isConnected`/`batteryLevel` (Task 3), `BleConstants.ZWIFT_CLICK_SERVICE` (Task 1).
- Produces: `DevicesUiState` gains `clickDevices`, `connectedClick`, `pendingClickAddress`, `isConnectingClick`, `clickBattery`; `DevicesViewModel` gains `connectClick(device)`, `disconnectClick()`.

Note: the Zwift Play advertises the same custom service and will therefore
also appear under "Controllers". It will pair and handshake but produce no
shifts (its 0x07 frames parse as `Unknown`). Acceptable for now — Play support
is explicitly out of scope.

- [x] **Step 1: Extend DevicesUiState and add scan filtering**

In `app/src/main/java/com/trainerloop/ui/devices/DevicesViewModel.kt`:

Extend the `DevicesUiState` data class — after `hrDevices`, add:

```kotlin
  val clickDevices: List<BleDevice> = emptyList(),
```

after `connectedHr`, add:

```kotlin
  val connectedClick: BleDevice? = null,
```

after `pendingHrAddress`, add:

```kotlin
  val pendingClickAddress: String? = null,
```

after `isConnectingHr`, add:

```kotlin
  val isConnectingClick: Boolean = false,
```

after `latestHrBpm`, add:

```kotlin
  val clickBattery: Int? = null,
```

Add the job fields next to `hrConnectionJob` / `hrCollectorJob`:

```kotlin
  private var clickConnectionJob: Job? = null
  private var clickCollectorJob: Job? = null
```

In `startScan()`, change the scanned services list to include the Click:

```kotlin
    val flow = scanner.startScan(
      services = listOf(
        BleConstants.FTMS_SERVICE,
        BleConstants.HEART_RATE_SERVICE,
        BleConstants.ZWIFT_CLICK_SERVICE
      ),
      durationMs = 10_000L
    )
```

In the scan `collect` block, add controller filtering and carry it into the state copy:

```kotlin
        flow.collect { devices ->
          val trainers = devices.filter { device ->
            device.services.contains(BleConstants.FTMS_SERVICE)
          }
          val hrSensors = devices.filter { device ->
            device.services.contains(BleConstants.HEART_RATE_SERVICE)
          }
          val controllers = devices.filter { device ->
            device.services.contains(BleConstants.ZWIFT_CLICK_SERVICE)
          }
          _uiState.value = _uiState.value.copy(
            trainerDevices = trainers,
            hrDevices = hrSensors,
            clickDevices = controllers,
            isScanning = true
          )
        }
```

- [x] **Step 2: Add connectClick / disconnectClick / collectClickState**

Still in `DevicesViewModel.kt`, add below `connectHr` / `disconnectHr` (mirrors them exactly):

```kotlin
  fun connectClick(device: BleDevice) {
    val app = appContext.trainerLoopApp
    _uiState.value = _uiState.value.copy(
      isConnectingClick = true,
      pendingClickAddress = device.address,
      error = null
    )

    val btDevice = resolveBluetoothDevice(appContext, device.address)
    if (btDevice == null) {
      _uiState.value = _uiState.value.copy(
        isConnectingClick = false,
        pendingClickAddress = null,
        error = "Could not resolve Bluetooth device ${device.address}."
      )
      return
    }

    clickCollectorJob?.cancel()
    clickConnectionJob?.cancel()
    clickConnectionJob = viewModelScope.launch {
      var success = false
      var capturedClick: com.trainerloop.ble.ZwiftClickManager? = null
      try {
        app.attachClick(btDevice)
        val clickManager = app.clickManager.value ?: run {
          _uiState.value = _uiState.value.copy(
            connectedClick = null,
            isConnectingClick = false,
            pendingClickAddress = null,
            error = "Controller connection failed: manager not created"
          )
          return@launch
        }
        capturedClick = clickManager

        val result = try {
          clickManager.connect()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Result.failure(e)
        }
        if (result.isSuccess) {
          _uiState.value = _uiState.value.copy(
            connectedClick = device,
            isConnectingClick = false,
            pendingClickAddress = null
          )
          collectClickState()
          success = true
        } else {
          _uiState.value = _uiState.value.copy(
            connectedClick = null,
            isConnectingClick = false,
            pendingClickAddress = null,
            error = "Controller connection failed: ${result.exceptionOrNull()?.message ?: "unknown"}"
          )
        }
      } finally {
        if (!success && app.clickManager.value == capturedClick) {
          app.clearClick()
        }
      }
    }
  }

  fun disconnectClick() {
    clickConnectionJob?.cancel()
    clickCollectorJob?.cancel()
    val app = appContext.trainerLoopApp
    viewModelScope.launch {
      app.clearClick()
    }
    _uiState.value = _uiState.value.copy(
      connectedClick = null,
      clickBattery = null,
      pendingClickAddress = null,
      error = null
    )
  }

  private fun collectClickState() {
    clickCollectorJob?.cancel()
    val app = appContext.trainerLoopApp
    val manager = app.clickManager.value ?: return
    clickCollectorJob = viewModelScope.launch {
      launch {
        manager.batteryLevel.collect { battery ->
          _uiState.value = _uiState.value.copy(clickBattery = battery)
        }
      }
      launch {
        manager.isConnected.collect { connected ->
          if (connected) {
            _uiState.value = _uiState.value.copy(connectedClick = manager.device.toBleDevice())
          } else if (_uiState.value.connectedClick != null) {
            _uiState.value = _uiState.value.copy(connectedClick = null)
          }
        }
      }
    }
  }
```

- [x] **Step 3: Restore on re-entry and clean up**

In `restoreConnectedDevices()`, extend to:

```kotlin
  private fun restoreConnectedDevices() {
    val app = appContext.trainerLoopApp
    val trainer = app.ftmsManager.value?.takeIf { it.isConnected.value }?.device?.toBleDevice()
    val hr = app.hrManager.value?.takeIf { it.isConnected.value }?.device?.toBleDevice()
    val click = app.clickManager.value?.takeIf { it.isConnected.value }?.device?.toBleDevice()
    _uiState.value = _uiState.value.copy(
      connectedTrainer = trainer,
      connectedHr = hr,
      connectedClick = click
    )
    if (trainer != null) collectTrainerState()
    if (hr != null) collectHrState()
    if (click != null) collectClickState()
  }
```

In `onCleared()`, add before `scanner.stopScan()`:

```kotlin
    clickConnectionJob?.cancel()
    clickCollectorJob?.cancel()
```

- [x] **Step 4: Add the Controllers section to DevicesScreen**

In `app/src/main/java/com/trainerloop/ui/devices/DevicesScreen.kt`:

Update the empty-paired check to include the Click:

```kotlin
      if (uiState.connectedTrainer == null && uiState.connectedHr == null && uiState.connectedClick == null) {
```

After the `uiState.connectedHr?.let { ... }` paired-card block, add:

```kotlin
      uiState.connectedClick?.let { device ->
        item {
          PairedDeviceCard(
            name = device.name ?: "Zwift Click",
            connected = true,
            detail = uiState.clickBattery?.let { "Battery $it%" } ?: "Connected",
            onDisconnect = { viewModel.disconnectClick() }
          )
        }
      }
```

Extend `pairedAddresses` and add the available-controllers list:

```kotlin
      val pairedAddresses = setOfNotNull(
        uiState.connectedTrainer?.address,
        uiState.connectedHr?.address,
        uiState.connectedClick?.address
      )
      val availableTrainers = uiState.trainerDevices.filter { it.address !in pairedAddresses }
      val availableHr = uiState.hrDevices.filter { it.address !in pairedAddresses }
      val availableControllers = uiState.clickDevices.filter { it.address !in pairedAddresses }
```

After the `if (availableHr.isNotEmpty()) { ... }` block, add:

```kotlin
      if (availableControllers.isNotEmpty()) {
        item {
          Text(
            text = "Controllers",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        items(availableControllers) { device ->
          AvailableDeviceCard(
            device = device,
            isConnecting = uiState.isConnectingClick && uiState.pendingClickAddress == device.address,
            onConnect = { viewModel.connectClick(device) }
          )
        }
      }
```

And extend the no-devices-found condition:

```kotlin
      if (availableTrainers.isEmpty() && availableHr.isEmpty() && availableControllers.isEmpty() && !uiState.isScanning) {
```

- [x] **Step 5: Verify compilation and tests**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [x] **Step 6: Commit**

```bash
git add app/src/main/java/com/trainerloop/ui/devices/DevicesViewModel.kt \
        app/src/main/java/com/trainerloop/ui/devices/DevicesScreen.kt
git commit -m "feat(devices): scan, pair and show battery for Zwift Click"
```

---

### Task 6: Feed Click shifts into FreeRideViewModel

**Files:**
- Modify: `app/src/main/java/com/trainerloop/ui/freeride/FreeRideViewModel.kt`
- Modify: `app/src/main/java/com/trainerloop/ui/freeride/FreeRideViewModelFactory.kt`
- Modify: `app/src/main/java/com/trainerloop/ui/TrainerLoopApp.kt:145-156`
- Test: `app/src/test/java/com/trainerloop/ui/freeride/FreeRideViewModelTest.kt`

**Interfaces:**
- Consumes: `ZwiftClickManager.shiftEvents: SharedFlow<ClickShift>` (Task 3), `TrainerLoopApplication.clickManager` (Task 4).
- Produces: `FreeRideViewModel` and `FreeRideViewModelFactory` gain a `clickManagerFlow: StateFlow<ZwiftClickManager?>` constructor parameter (defaulted, so no other caller breaks).

- [x] **Step 1: Write the failing test**

Add to `app/src/test/java/com/trainerloop/ui/freeride/FreeRideViewModelTest.kt`.

New imports:

```kotlin
import com.trainerloop.ble.ClickShift
import com.trainerloop.ble.ZwiftClickManager
import kotlinx.coroutines.flow.MutableSharedFlow
```

New test at the bottom of the class:

```kotlin
  @Test
  fun `zwift click shift events change gear like the buttons`() = runTest(testDispatcher) {
    val shifts = MutableSharedFlow<ClickShift>()
    val click: ZwiftClickManager = mockk(relaxed = true) {
      every { shiftEvents } returns shifts
    }
    val vm = FreeRideViewModel(
      route = route(),
      routeId = "r1",
      ftmsManagerFlow = MutableStateFlow(mockFtms(MutableStateFlow(bikeData(180, 90.0)))),
      hrManagerFlow = MutableStateFlow(null),
      clickManagerFlow = MutableStateFlow<ZwiftClickManager?>(click),
      dispatcher = testDispatcher
    )
    runCurrent()

    shifts.emit(ClickShift.UP)
    runCurrent()
    assertEquals(8, vm.uiState.value.gear)

    shifts.emit(ClickShift.DOWN)
    shifts.emit(ClickShift.DOWN)
    runCurrent()
    assertEquals(6, vm.uiState.value.gear)
  }

  @Test
  fun `no click paired behaves as today`() = runTest(testDispatcher) {
    val vm = viewModel(MutableStateFlow(bikeData(180, 90.0)))
    runCurrent()
    assertEquals(7, vm.uiState.value.gear)
    vm.shiftUp()
    assertEquals(8, vm.uiState.value.gear)
  }
```

- [x] **Step 2: Run the tests to verify the new one fails**

```bash
./gradlew :app:testDebugUnitTest --tests "com.trainerloop.ui.freeride.FreeRideViewModelTest"
```

Expected: FAIL — no `clickManagerFlow` parameter (compilation error).

- [x] **Step 3: Add the parameter and collector to FreeRideViewModel**

In `app/src/main/java/com/trainerloop/ui/freeride/FreeRideViewModel.kt`:

New imports:

```kotlin
import com.trainerloop.ble.ClickShift
import com.trainerloop.ble.ZwiftClickManager
import kotlinx.coroutines.flow.emptyFlow
```

Add the constructor parameter after `ftmsControlManagerFlow`:

```kotlin
  private val clickManagerFlow: StateFlow<ZwiftClickManager?> = MutableStateFlow(null),
```

At the end of the `init` block, add:

```kotlin
    // Zwift Click: third shift input beside the on-screen buttons and volume
    // keys. Same entry points, so downstream (drivetrain, ERG) is untouched.
    viewModelScope.launch {
      clickManagerFlow
        .flatMapLatest { manager -> manager?.shiftEvents ?: emptyFlow() }
        .collect { shift ->
          when (shift) {
            ClickShift.UP -> shiftUp()
            ClickShift.DOWN -> shiftDown()
          }
        }
    }
```

- [x] **Step 4: Thread the flow through the factory**

In `app/src/main/java/com/trainerloop/ui/freeride/FreeRideViewModelFactory.kt`:

Add import:

```kotlin
import com.trainerloop.ble.ZwiftClickManager
```

Add a constructor parameter after `ftmsControlManagerFlow`:

```kotlin
  private val clickManagerFlow: StateFlow<ZwiftClickManager?>,
```

and pass it in `create()` after `ftmsControlManagerFlow = ftmsControlManagerFlow,`:

```kotlin
      clickManagerFlow = clickManagerFlow,
```

- [x] **Step 5: Wire it in the nav host**

In `app/src/main/java/com/trainerloop/ui/TrainerLoopApp.kt` (the FreeRide composable, currently lines 145–156), add `app.clickManager` to both the `remember` keys and the factory arguments:

```kotlin
        val freeRideFactory = remember(
          loaded, routeId, app.ftmsManager, app.hrManager, app.ftmsControlManager,
          app.clickManager, profile
        ) {
          com.trainerloop.ui.freeride.FreeRideViewModelFactory(
            route = loaded,
            routeId = routeId,
            ftmsManagerFlow = app.ftmsManager,
            hrManagerFlow = app.hrManager,
            ftmsControlManagerFlow = app.ftmsControlManager,
            clickManagerFlow = app.clickManager,
            userProfile = profile
          )
        }
```

- [x] **Step 6: Run the full unit test suite**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all tests pass including the two new ones.

- [x] **Step 7: Commit**

```bash
git add app/src/main/java/com/trainerloop/ui/freeride/FreeRideViewModel.kt \
        app/src/main/java/com/trainerloop/ui/freeride/FreeRideViewModelFactory.kt \
        app/src/main/java/com/trainerloop/ui/TrainerLoopApp.kt \
        app/src/test/java/com/trainerloop/ui/freeride/FreeRideViewModelTest.kt
git commit -m "feat(freeride): shift virtual gears from Zwift Click events"
```

---

### Task 7: Hardware verification (manual — requires a physical Zwift Click)

This is the spike's validation half: the protocol above is community
reverse-engineered, so it must be confirmed against a real device before the
feature is considered done. **A human with a Zwift Click, an Android phone,
and adb must run this.** If any step fails, the fix belongs in
`ZwiftClickProtocol.kt` / `ZwiftClickManager.kt` only — every observed frame
is already hex-logged by `GattCallback` (`notification char=… bytes=…`), so a
deviation can be diagnosed straight from logcat without a BLE sniffer.

- [ ] **Step 1: Install and start log capture**

```bash
./gradlew :app:installDebug
adb logcat -s BleLog
```

- [ ] **Step 2: Pair the Click**

Wake the Click by pressing either button (it sleeps aggressively; the LED
blinks blue when advertising). In the app: Devices → Scan → the Click appears
under "Controllers" → Connect.

Expected in logcat: `ZwiftClickManager.connect`, service discovery, two
descriptor writes, `Zwift Click handshake acknowledged`,
`ZwiftClickManager.connect success`. Expected in UI: paired card shows
"Zwift Click" with a battery percentage (or "Connected" if 0x180F is absent).

If the handshake times out: capture the hex of the sync-TX frame from logcat
and compare with the "Protocol Reference" section — Zwift firmware updates
have changed response prefixes before (Click v2 answers `02 03`).

- [ ] **Step 3: Shift during a free ride**

Start any GPX free ride. Press plus/minus on the Click.

Expected: `Zwift Click shift UP` / `DOWN` in logcat; the gear readout changes
by exactly 1 per physical press (no double shifts from repeated frames); the
on-screen ▲/▼ buttons and volume keys still work in parallel; ERG target
follows the gear as it does for the other inputs.

- [ ] **Step 4: Robustness checks**

- Hold a button for 3 s: exactly one shift (no auto-repeat — by design).
- Walk the Click out of range (or pop its battery) mid-ride: paired card
  eventually drops or shows reconnecting; app keeps running; on return the
  Click reconnects (`RECONNECTING` → re-handshake in logcat) and shifting
  works again without re-pairing.
- Ride with no Click paired: everything behaves exactly as before the change.
- Let the Click idle 10+ minutes during a ride (only pedaling, no shifting):
  it must still shift afterwards (keepalive frames `0x15` keep the link warm;
  if it sleeps anyway, note it — waking requires a button press, which is
  itself the shift, so this may be acceptable).

- [ ] **Step 5: Record results**

Append findings (firmware version from the Zwift Companion app, any frame
deviations, battery behaviour) to this plan file under a "Hardware
verification results" heading, then commit:

```bash
git add ../docs/plans/2026-07-10-zwift-click-shifter-plan.md
git commit -m "docs: Zwift Click hardware verification results"
```

---

## Risks / Future Work

- **Protocol drift:** Zwift can change the RideOn protocol in a firmware
  update. Blast radius is confined to `ZwiftClickProtocol.kt` +
  `ZwiftClickManager.kt`; unknown frames are hex-logged, never crash.
- **Zwift Play / Ride / Click v2:** same service, different message types
  (0x07 / 0x23) and, for Ride, service `FC82`. The parser already ignores
  them safely; supporting them means adding message decoders and a device-type
  check on the Zwift manufacturer data (company ID 0x094A, first byte:
  0x09 = Click v1, 0x0A/0x0B = Click v2, 0x02/0x03 = Play, 0x07/0x08 = Ride).
- **Press-and-hold repeat:** the Click reports held state continuously, so
  auto-repeat is a timer in `ClickShiftDetector` away if riders ask for it.
- **Encrypted mode:** not needed — the Click serves the unencrypted mode
  whenever the handshake omits the key-exchange bytes.
