# TrainerLoop — Deep Code Review

_Reviewed: 2026-07-08 · branch `feature/android-migration`_

## Overall assessment

This is a genuinely well-built personal app. The pure-Kotlin `domain/` core (physics,
FIT, parsers, coaching) is cleanly separated from Android and heavily unit-tested
(golden-master FIT tests, a scripted athlete simulator, replay tests). The hard BLE
problems are thoughtfully handled — one shared GATT link split across data/control
managers, a `gattMutex` to serialize operations, per-manager reconnect handlers. Room
migrations are correct with no destructive fallback.

The defects cluster in three places: **the FIT encoder produces spec-non-conformant
files**, **the intervals.icu client trusts error responses**, and **the BLE
serialization has an Android-13+ hole**. Findings below are verified against source
where marked; severity reflects real-world impact for a single-user app.

**The three highest-value fixes** for a ride-and-upload workflow are #1 + #2 (FIT
correctness — uploaded files are currently malformed) and #3 (intervals.icu error
handling).

---

## 🔴 High — data corruption / crashes / silent ride loss

### 1. FIT session summary uses wrong field numbers — power missing, cadence garbled
`domain/fit/FitEncoder.kt:245-249, 303-307` · **verified**

`sessionFields` maps avg cadence → field 15 (`max_speed` in the FIT profile), avg
power → field 18 (`avg_cadence`), max power → field 19 (`max_cadence`). Fields 20/21
(the real `avg_power`/`max_power`) are never written. A ride with avg 200 W / max
400 W / 90 rpm shows **no power** in the Garmin/Strava session summary,
`avg_cadence` = 200, `max_speed` ≈ 0.09. Per-record data (msg 20) is correct, so this
is summary-only — but power is the headline metric.

**Fix:** field 18 = avg_cadence (uint8), field 20 = avg_power (uint16), field
21 = max_power (uint16); drop the field 15/19 mappings.

### 2. Trailing file CRC excludes the 14-byte header
`domain/fit/FitEncoder.kt:343` · **verified**

`crc16(dataBytes)` omits the header; the FIT spec requires the terminating CRC to
cover the entire file. The app's own `FitDecoder` never validates CRC (so round-trip
and golden-master tests stay green), but strict consumers like Garmin Connect reject
the upload outright.

**Fix:** `crc16(header.subList(0, FIT_HEADER_SIZE) + dataBytes)` (one continuous CRC
across header then data).

### 3. intervals.icu GET calls never check HTTP status — error bodies parsed as data
`data/source/remote/IntervalsIcuClient.kt:78-96` · **verified**

`request()` returns `readBody()`, which reads `errorStream` on non-2xx with no failure
signal. On a 401 (expired key) or 500, `downloadZwo()` hands the HTML/JSON error page
to `WorkoutImporter.import()` as if it were a workout (`WorkoutLibraryViewModel`), and
`getAthlete`/`getTodaysWorkoutEvents` fail JSON parse into a swallowed exception →
silent no-op sync. (`uploadActivity`/`updateFtp` already check the code — only the GET
path is affected.)

**Fix:** throw a typed exception when `responseCode !in 200..299` and let callers map
it to a real error state.

### 4. Android 13+ characteristic write returns before completion, defeating `gattMutex`
`ble/BleConnection.kt:252-258` · **verified**

On Tiramisu+, `writeCharacteristic(...)` returning `GATT_SUCCESS` only means *queued*;
completion arrives via `onCharacteristicWrite`. The code returns `Result.success`
immediately and exits `withLock` — so back-to-back control-point writes (e.g.
`setTargetPower` then `startResume`) race, the exact bug the mutex was added to
prevent (see comment at lines 46-51). The legacy path (260-272) correctly awaits.
With `minSdk 30` / `targetSdk 35`, any device on 13/14/15 hits this path.

**Fix:** on the Tiramisu path also `resetWriteDeferred(characteristic.uuid)` and
`await()` inside the lock, treating `GATT_SUCCESS` only as "initiated".

### 5. `BluetoothGatt` leaked on failed connect
`ble/BleConnection.kt:78-92` · **verified**

`gatt = gattInstance` is assigned before awaiting the result; on the failure branch
`gattInstance.close()` is never called and `gatt` stays non-null. A subsequent
`connect()` overwrites `gatt` without closing the old one. Over repeated failed
attempts this exhausts the limited BluetoothGatt client slots.

**Fix:** `gattInstance.close(); gatt = null` in the failure branch (and guard against
overwriting a live gatt).

### 6. Unbounded `await()` while holding `gattMutex` → permanent GATT deadlock
`ble/BleConnection.kt:159, 223, 270, 291` · **verified**

`discoverServices`, `enableNotifications`, the legacy write, and `read` all await
their callback inside `gattMutex.withLock` with no timeout. A silent/flaky peripheral
that never fires the callback (common with BLE) holds the mutex forever, wedging all
future ops and stalling reconnect re-arming.

**Fix:** wrap each await in `withTimeout(...)` and complete/fail the deferred on
timeout so the mutex is released.

### 7. `connectedDevice` foreground service may crash on API 34+ without BT permission
`app/WorkoutForegroundService.kt:38,45` + manifest `foregroundServiceType`

On API 34+, starting a `connectedDevice`-typed FGS requires a prerequisite runtime
permission (e.g. `BLUETOOTH_CONNECT`) held at the `startForeground` call. A manual or
ramp-test workout can start with no trainer connected; `BlePermissions.REQUIRED` is
only requested, not enforced. If the user denied `BLUETOOTH_CONNECT`, `WorkoutScreen`'s
`LaunchedEffect` (`WorkoutScreen.kt:166`) fires `start()` and `startForeground` throws
`SecurityException`, crashing the app at workout start.

**Fix:** gate the service start on `BlePermissions.hasPermissions()`, or pass an
explicit fallback FGS type with try/catch, or only use `connectedDevice` when a device
is actually attached.

### 8. Free-ride sessions never start the foreground service
`ui/freeride/FreeRideScreen.kt` (contrast `WorkoutScreen.kt:163-180`)

The FGS exists to keep BLE + the workout clock alive while backgrounded. `WorkoutScreen`
starts/updates/stops it on `isRunning`; `FreeRideScreen` does not. A backgrounded GPX
free ride (or one paused, which releases `keepScreenOn`) has no foreground service, so
the process is subject to background CPU throttling / kill — the `WorkoutClock` tick
loop stalls and BLE/ERG streaming drops, losing the ride.

**Fix:** mirror the `WorkoutScreen` start/update/stop `LaunchedEffect`s in
`FreeRideScreen`.

---

## 🟠 Medium

### In-flight session lost on config change / process death → empty save
`app/TrainerLoopApplication.kt:43-49`, consumed `TrainerLoopApp.kt:241, 296-303`

`selectedWorkout` / `pendingSessionSamples` / `pendingCoachJson` live on the
Application singleton. `WorkoutPlayer` falls back to the dev "Sweet Spot" sample after
a low-memory kill. Worse: `WorkoutComplete` reads and nulls `pendingSessionSamples`
during composition, and `configChanges` doesn't list night-mode/locale/density — those
recreate the Activity → `NavHost` rebuilds `WorkoutComplete` with `emptyList()` →
`computeSummary()`/`saveSession()` early-return and the finished ride is saved empty.

**Fix:** pass payloads via `SavedStateHandle`, or persist the finished session to the
DB before navigating and load by id (also resolves the hardcoded `sessionId = 1L`).

### Workout name mis-encoded in navigation route (`+` for space)
`ui/navigation/Screen.kt:24` · **verified**

`URLEncoder.encode(workoutName, "UTF-8")` produces `+` for spaces
(`application/x-www-form-urlencoded`), but Navigation-Compose decodes path args with
`Uri.decode`, which leaves `+` untouched. "Sweet Spot" arrives as `Sweet+Spot` and is
persisted to `SessionData.workoutName`.

**Fix:** encode with `Uri.encode(workoutName)` (matches Nav's decode).

### FIT distance/elapsed values built with 32-bit Int math — overflow on long rides
`domain/fit/FitEncoder.kt:271, 287-289, 208`

`(m * 100).toInt()` for distance-cm overflows at ~214 km; `totalElapsedSec * 1000`
overflows at ~24.8 days. The wrapped negative Int is then clamped to 0.

**Fix:** keep these in `Long` all the way into `encodeValue`.

### XXE in `.zwo` / `.gpx` parsers
`domain/parser/ZwoParser.kt:18`, `domain/parser/GpxParser.kt:31`

`DocumentBuilderFactory.newInstance()` with default settings (DOCTYPE / external
entities enabled) on untrusted user-imported files → external-entity exfiltration or
billion-laughs DoS. Low likelihood for a single user, but trivial to harden.

**Fix:** `factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)`
before parse.

### Battery-level read crashes the app on empty payload
`ble/FtmsManager.kt:84-88` + `ble/BleConnection.kt:295`

The parse lambda `it[0].toInt() and 0xFF` runs *outside* `read()`'s try/catch, off a
bare `scope.launch` on `Dispatchers.Main`. An empty payload → uncaught
`IndexOutOfBoundsException` on the main thread → crash (`SupervisorJob` doesn't catch
it).

**Fix:** guard `if (it.isEmpty()) null else ...`, and/or move `parse` inside the try.

### `ProfileRepository.updateProfile()` lost-update race + plaintext API-key storage
`data/repository/ProfileRepository.kt:20-25` (race), `:12-13` (storage)

Non-atomic read-modify-write can silently drop a just-synced FTP or just-entered API
key when two coroutines update concurrently. Separately, the intervals.icu API key
(a bearer-equivalent secret) sits unencrypted in `MODE_PRIVATE` `SharedPreferences`
with `allowBackup=true`.

**Fix:** guard with `Mutex.withLock` / `MutableStateFlow.update {}`; store the key with
`EncryptedSharedPreferences` or exclude it from backup.

### ERG/MRC percent auto-detection misclassifies header-less files
`domain/parser/ErgMrcShared.kt:65`

`if (dataPoints.first().power <= 2) PERCENT else WATTS` — a headerless percent MRC
whose first point is > 2 (e.g. "50" = 50% FTP) is treated as watts → every target
roughly halved.

**Fix:** prefer file extension (.mrc ⇒ PERCENT, .erg ⇒ WATTS) for the fallback, or
detect on the max value.

### `START_STICKY` + redelivered null intent → phantom notification + 6h wakelock
`app/WorkoutForegroundService.kt:26-49`

If the system kills and auto-restarts the service, `onStartCommand` is re-invoked with
a null intent → `else` branch → `isRunning` defaults to `true` → acquires the 6h
wakelock and shows a bogus "0W • 0:00" ongoing notification with nothing to stop it.

**Fix:** return `START_NOT_STICKY`, or detect a null/actionless intent and `stopSelf()`.

### Coach: seek/scrub corrupts segment stats & interval ledger
`domain/coach/AthleteStateModel.kt:143-145` (trigger `LiveCoach.kt:63-65`)

`invalidateWindows()` clears rolling windows but not the current-segment accumulators
(`segPowerSum`, `segInBand`, `segHr*`, confidence flag windows, `recoveryElapsed`).
`handleSegmentChange` early-returns when the index is unchanged, so a > 30 s scrub
within one interval keeps piling samples → wrong `avgPower`, `timeInTargetPct`,
`powerCv`, `hrDriftPct`, and a wrong `IntervalRecord` at the next boundary.

**Fix:** also reset the segment accumulators and flag windows (or force segment
re-init) in `invalidateWindows()`.

### Coach: `CoachEngine.hrDriftPct` measures HR *range*, not drift
`domain/CoachEngine.kt:368-372` (consumed at 214-216, 241)

`((max - min) / min) * 100` over the window measures spread, not drift over time. Any
hard interval spans a wide HR range → over-reports drift → spuriously fires
`AdjustIntensityDown` / `ExtendRecovery` and blocks legitimate `AdjustIntensityUp` on
the still-active v1 suggestion path.

**Fix:** compute drift end-vs-start (regression slope, or last-third minus first-third
mean), matching `AthleteStateModel.hrDriftPct`.

### Coach: `pacing-*` sustain counters never reset across segments
`domain/coach/AnalyticsEngine.kt:37-43, 89-111`

On segment change only `interval:`-prefixed keys reset; `pacing-under`/`pacing-over`
reset only inside the `!ergEnabled && ctx.isWork && !inQuietZone` block, which never
runs during recovery. A partial counter freezes through recovery and resumes in the
next interval, firing ~17 s early (non-ERG only).

**Fix:** clear the `pacing-*` counters in the segment-change reset block, or prefix
them `interval:`.

### FTMS `IndoorBikeData` parsing gaps
`ble/model/IndoorBikeData.kt:41-43, 77, 89`

- Flags bit 0 ("More Data") is ignored; Instantaneous Speed is assumed always present.
  A trainer that sets bit 0 = 1 shifts every subsequent field by 2 bytes (line 41-43).
- Average Power parsed as `readUint16Le` though FTMS defines it SINT16 (line 89).
- Resistance rounding `(raw + 5) / 10` truncates toward zero, wrong for negatives
  (line 77) — `-15` → `-1` instead of `-2`.

**Fix:** read speed only when `flags and 1 == 0`; use `readInt16Le` for avg power;
`Math.round(raw / 10.0)` for resistance.

### ViewModel factory built on the main thread on every recomposition
`ui/TrainerLoopApp.kt:256-262` (also :146, :188; `WorkoutScreen.kt:92`)

`WorkoutViewModelFactory(...)` is constructed inline in the `viewModel()` call, so its
args — `ProfileRepository(context).getProfileSync()` (twice) and
`CoachProfileLoader.load(...)` (JSON asset parse) — run on the main thread on every
recomposition even though `create()` runs once.

**Fix:** build the factory inside `remember(...)`, or load profile/coach data in the
ViewModel off the main dispatcher.

---

## 🟡 Low (cleanup pass)

- **Dead throttle condition** in `FtmsControlManager.setTargetPower` (lines 136-137):
  line 137 makes line 136 unreachable, so *all* target updates are rate-limited to
  900 ms even when the target changes.
- **`POST_NOTIFICATIONS` never requested at runtime** (`MainActivity.kt:40-42`) → FGS
  notification silently suppressed on API 33+.
- **`collectAsState` instead of `collectAsStateWithLifecycle`** (`WorkoutScreen.kt:88-89`,
  `FreeRideScreen.kt:54-55`, others) — collection stays hot when backgrounded.
- **Missing DB indices** on `SessionEntity.startedAt` / `RouteEntity.importedAt`
  (`SessionDao.kt:15`, `RouteDao.kt:14` sort on them → full-table scan each emission).
- **BLE coroutine scopes never cancelled** in `disconnect()` (`BleConnection`,
  `FtmsManager`, `HrManager`, `FtmsControlManager`) — collectors/timeouts leak across
  connect cycles.
- **Multipart `filename="$name.fit"` unsanitized** (CRLF/quote injection),
  `IntervalsIcuClient.kt:57`; also buffers the whole FIT byte array in memory.
- **Unencoded URL path/query params** in `IntervalsIcuClient` (athleteId, date).
- **Non-`@Volatile` deferred vars in `GattCallback`** reassigned on Main, read on the
  binder thread (`GattCallback.kt:20-21, 40-41, 63-64`).
- **`BleScanner` single shared `activeCallback`** orphans a scan if `startScan` is
  called twice; `bluetoothAdapter!!` non-null assertion (`BleScanner.kt:32, 41, 90-108`).
- **Non-deterministic `FeedbackItem.id = UUID.randomUUID()`** undermines replay
  determinism (`FeedbackDecisionEngine.kt:61`).
- **`LiveCoach` mutates shared `ArrayList`/maps with no lock** — safe only because
  `viewModelScope` is `Main.immediate` (`LiveCoach.kt:91/94/106`).
- **`ZwoParser` surfaces raw `SAXParseException`** on malformed XML instead of a wrapped
  message like `GpxParser` (`ZwoParser.kt:18-21`).
- **`ZwoParser.toWatts` treats value > 3 as absolute watts** (`ZwoParser.kt:311`) —
  `Power="3.5"` (350% FTP sprint) encoded as 3.5 W.
- **Disk I/O in `ProfileRepository` constructor** — synchronous `load()` on the
  constructing (likely main) thread (`ProfileRepository.kt:12-15`).

---

## ✅ Verified-correct (no change needed)

- FTMS control opcodes (`0x00/0x05/0x07/0x08`) and the `0x80`/reqOp/`0x01` response
  parsing in `handleResponse`.
- The HeartRateMeasurement parser (uint8/uint16 via flags bit 0, length guards).
- The odd-looking `readInt16Le` (`IndoorBikeData.kt:150-158`) — Kotlin binds `-`
  tighter than the infix `or`, so the sign-extension math is actually correct.
- Virtual-ride physics: formula, units (m/s vs km/h), gradient sign, bisection
  monotonicity, coasting/descent, gear-index bounds (1..14), dt-capping.
- FIT *record* message (msg 20) field numbers, endianness, timestamp epoch
  (631065600000 ms = 1989-12-31), compressed-timestamp rollover in the decoder.
- Locale-independent number parsing (`toDoubleOrNull`) — no comma/locale bug.
- Room migrations 1→2 and 2→3 are complete and column-accurate; **no**
  `fallbackToDestructiveMigration` (no data-loss path).
- Suspend DAO methods + `withContext(Dispatchers.IO)` keep DB/network off the main
  thread; the API key is never logged.
- No `Context`/`Activity` leaks in ViewModels; `onCleared()` teardown is correct in
  `WorkoutViewModel`, `FreeRideViewModel`, `DevicesViewModel`.
- `ManagerProvider` singleton handles double-connect reasonably (tears down previous
  connection; cleanup guarded by identity check).

---

## Suggested remediation order

1. **FIT encoder (#1, #2, and the Int-overflow Medium)** — self-contained,
   test-covered, and every exported ride is currently malformed. Extend the golden
   test with a real FIT validator / field-number assertions.
2. **intervals.icu status handling (#3)** — a few lines; prevents silent corrupt
   imports and no-op syncs.
3. **BLE Android-13+ write + timeouts + gatt leak (#4, #5, #6)** — correctness of the
   core control path on modern devices.
4. **Session persistence / config-change loss + nav name encoding** (Medium) — prevents
   losing completed rides.
5. Free-ride foreground service (#8), then the remaining Medium/Low items.
