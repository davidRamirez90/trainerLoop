# Battery & Performance Analysis — Trainer Loop (Pixel 2XL target)

Analysis of the running-workout hot path, ranked by real battery impact on the
target device. Every finding cites a file:line and the concrete change.

## Device context

Pixel 2XL: Snapdragon 835 (2017), 4 GB RAM, **1440×2880 P-OLED** panel, and by
now a chemically aged battery. Two things dominate power on this hardware during
a workout:

1. **The display** — a 1440p OLED at workout brightness is the single largest
   draw. OLED power is roughly proportional to lit pixels × brightness, so
   *what colors you paint* matters, not just that the screen is on.
2. **The BLE radio** — every packet sent/received wakes the radio. Redundant
   writes cost real mA.

CPU wakeups (per-second recomposition, logging, list copies) matter less in
absolute mA but keep the big cores from idling and add up over a 60–90 min ride.
The findings below are ordered accordingly.

---

## 1. ERG target power is written to the trainer **every second** — HIGH

`WorkoutViewModel.kt:207-218` builds a `combine(...)` whose **first source is
`clock.elapsedSec`**, which emits every second. `combine` re-emits whenever *any*
source emits, so `handleControlTick` (`:332`) runs once per second for the whole
ride. When ERG is on it unconditionally issues a BLE write:

```kotlin
ergWriteJob = viewModelScope.launch { control.setTargetPower(target.coerceIn(0, 2000)) }
```

But the target only actually changes at **segment boundaries** (or on an
intensity/ERG toggle). So on a 60 min ride you send ~**3,600 GATT writes** when
~20–50 would do. Each write wakes the BLE radio and the trainer's controller.
This is the biggest avoidable radio drain in the app.

**Fix (small):** drop `clock.elapsedSec` from the `combine` — the tick only needs
to react to `running`, `ergEnabled`, and `targetRange` changing. Those three are
already `distinctUntilChanged`. If a periodic keep-alive re-assert is genuinely
needed for some trainers, gate it to e.g. every 10 s, not every 1 s.

```kotlin
combine(
  _uiState.map { it.isRunning }.distinctUntilChanged(),
  _uiState.map { it.isErgEnabled }.distinctUntilChanged(),
  _uiState.map { it.targetRange }.distinctUntilChanged()
) { running, erg, target -> WorkoutControlTick(running, erg, target) }
  .collect { handleControlTick(it) }
```

---

## 2. Foreground-service notification is rebuilt & re-posted every second — HIGH

`WorkoutScreen.kt:153`:

```kotlin
LaunchedEffect(uiState.currentPowerWatts, uiState.elapsedSec) {
  if (uiState.isRunning) WorkoutForegroundService.update(context, ..., timeStr, true)
}
```

`elapsedSec` changes every second, so every second this does
`context.startService(intent)` → a marshaled Binder IPC to `system_server` →
`onStartCommand` → a fresh `NotificationCompat.Builder(...).build()` →
`startForeground` re-post. That is per-second IPC + object allocation + a
NotificationManager round trip, all to change one "W • m:ss" string.

**Fix:**
- Throttle to every ~2–3 s, or only when the displayed power changes by a
  meaningful delta. `elapsedSec` alone shouldn't trigger a rebuild.
- Better architecturally: let the **service own** a reference to the clock /
  telemetry flows and update its own notification via
  `NotificationManager.notify(id, ...)` (no `startService` IPC, reuse one
  `Builder`). The Activity then only starts/stops the service, not per-second
  pokes. This also decouples the notification from the Activity lifecycle.

---

## 3. Unconditional hot-path logging with eager string allocation — MEDIUM-HIGH

`BleLog` (`ble/BleLog.kt`) calls `android.util.Log` directly with **no
`BuildConfig.DEBUG` guard**, and it's called on every BLE packet:

- `GattCallback.onCharacteristicChanged` (`:112`) logs every notification and
  computes `value.toHex()` — a `joinToString` + `%02X` format per byte — on
  **every FTMS/HR packet** (~1–4 Hz). The argument is built eagerly *before*
  `d()` runs, so gating inside `d()` wouldn't even help.
- `TelemetryRecorder` (`:98`) logs a formatted string every 1 Hz tick.

In a shipped/release ride this is pure waste: string building + logd IPC on
every packet, keeping the CPU from idling.

**Fix:**
- Guard call sites with `if (BuildConfig.DEBUG)` (the `toHex()` cost is the
  point — it must not run in release), **or** make `BleLog.d` take a lambda:
  `fun d(msg: () -> String) { if (BuildConfig.DEBUG) safe { Log.d(TAG, msg()) } }`
  so the string is never built in release.
- Turn on R8/minify for release too (`app/build.gradle.kts` currently has
  `isMinifyEnabled = false`) so log calls and unused code get stripped.

---

## 4. O(n²) sample list growth + repeated full-list rescans — MEDIUM

`TelemetryRecorder.kt:95` appends with a full copy each second:

```kotlin
_samples.value = existing + sample   // copies the whole list every tick
```

Over an hour that's 3,600 allocations of ever-growing arrays (~O(n²) total copy
work). That full list is then pushed into `uiState.samples` every second, and
each consumer re-scans **the entire list**:

- `CoachEngine.evaluateWorkSegment` computes **three** windows per tick, each via
  `samples.filter { it.timeSec in fromSec..toSec }` (`CoachEngine.kt:360`) — a
  full-list scan ×3–4 every second, even though it only ever needs the last
  30/90/120 s.
- `WorkoutChart` filters the full list again (`WorkoutChart.kt:181, 124`).

Samples are strictly time-ordered, so this is all avoidable.

**Fix:**
- Keep samples in a mutable append structure (or a `MutableStateList`) and avoid
  the per-tick full copy.
- In `CoachEngine`, since `timeSec` is sorted, take a tail window with
  `subList` / binary search instead of `filter` over the whole history. Windows
  are ≤120 s, so per-tick work becomes bounded rather than growing with ride
  length.

---

## 5. Live chart recomputes static geometry every frame — MEDIUM

`WorkoutChart` redraws every second (its `samples` + `elapsedSec` change), and
each redraw recomputes things that **don't change during the ride**:

- `peakTarget` — a 100-step scan over the whole plan (`:122`).
- The interval "zone blocks" — a ~200-step loop calling
  `WorkoutMath.targetRangeAt(...)` (`:149-164`).

These depend only on `segments` (and the zoom window), not on the new sample.
Recomputing them 3,600× per ride on the main thread pointlessly burns the 835.

**Fix:**
- `remember(segments, winStart, winEnd)` the zone-block geometry and
  `peakTarget`. Only the power/HR line's last point is genuinely new each tick.
- Because `uiState.samples` gets a new list identity every second, the chart
  recomposes even when nothing visible changed. Feeding it an append-only /
  stable structure (finding #4) lets Compose skip redundant recompositions.

---

## 6. Display: full-ride keep-on at 1440p — use OLED-black + dimming — MEDIUM

`WorkoutScreen.kt:109` correctly holds `keepScreenOn` only while running. That's
right — but on this P-OLED panel the *content* is where the savings are:

- **True-black workout theme.** OLED pixels displaying `#000000` are off. A
  workout screen that's mostly pure black with bright metrics can cut panel
  power substantially vs. a light or grey surface. Check `ui/theme/Theme.kt`
  uses `#000000` (not a dark-grey `surface`) for the running screen background.
- **Optional brightness reduction.** Offer a "dim after N seconds of no
  interaction" or a user brightness override for the workout screen — the panel
  dominates the battery budget and the rider mostly glances at it.
- Consider not forcing 1440p: the app can request a lower render resolution for
  the workout Activity, but the theme/black change is the cheaper, safer win.

---

## 7. Wake lock held while paused — LOW-MEDIUM

`WorkoutForegroundService` acquires a 6 h `PARTIAL_WAKE_LOCK` in `onCreate`
(`:86`) and only releases it in `onDestroy`. If the rider **pauses** and steps
away, `keepScreenOn` drops to false (good) but the partial wake lock keeps the
CPU from deep-sleeping, and the service keeps running.

**Fix:** release the wake lock on pause and re-acquire on resume (or acquire it
lazily only while `isRunning`). During pause there's no BLE data to record and no
clock to advance, so nothing needs the CPU held awake.

---

## 8. `uiState` monolith → broad recomposition + redundant copies — LOW-MEDIUM

`WorkoutViewModel` funnels everything through one `WorkoutUiState`, mutated by
~8 separate collectors. Several fire every second and each does
`_uiState.value = _uiState.value.copy(...)`:

- `clock.elapsedSec` → `updateFromClock()` + `updateInZone()` + `tickCoach()`
  (three separate `.copy()` calls per tick, `:175-181`, `:428-441`).
- `recorder.latest` → power copy (`:151`).
- `recorder.samples` → samples copy (`:162`).

Every `.copy()` reallocates the (large, samples-holding) state object and
notifies **all** `collectAsState` observers, so a power-only change recomposes
composables that only read `elapsedSec`, and vice-versa. On the 835 this is
avoidable recomposition churn every second.

**Fix (moderate):**
- Batch the per-second updates into a single `copy()` where possible (combine
  the elapsed-driven collectors).
- Split the genuinely hot, high-churn fields (power, HR, samples) into their own
  small `StateFlow`s so a change to one doesn't recompose consumers of another.
  Compose only redraws what actually reads the changed flow.

---

## 9. BLE scan mode — LOW (note only)

`BleScanner.kt:75` uses `SCAN_MODE_LOW_LATENCY`, the most power-hungry mode.
It's acceptable here because the scan is user-initiated and bounded (10 s, then
`stopScan`). Keep it as-is unless you add any background/auto rescanning — in
that case switch to `SCAN_MODE_BALANCED`. Just make sure `stopScan` always runs
on connect/cancel so the radio doesn't keep scanning.

---

## Quick-win summary

| # | Change | Effort | Battery impact |
|---|--------|--------|----------------|
| 1 | Drop `elapsedSec` from ERG control `combine` → ~3600→~50 BLE writes/ride | S | High (radio) |
| 2 | Throttle / self-drive FGS notification (stop per-second `startService`) | S–M | High (CPU/IPC) |
| 3 | Gate `BleLog`/`toHex` on `BuildConfig.DEBUG`; enable R8 minify | S | Med-High (CPU) |
| 4 | Append-in-place samples; windowed `subList` in CoachEngine | M | Medium (CPU) |
| 5 | `remember` static chart geometry; stable samples for recompose skip | S–M | Medium (CPU/GPU) |
| 6 | True-black OLED workout theme + optional dimming | S–M | Medium (panel) |
| 7 | Release wake lock while paused | S | Low-Med |
| 8 | Split hot state fields / batch per-tick copies | M | Low-Med (CPU) |

**Do these three first:** #1 and #2 (per-second redundant radio + IPC) and #3
(per-packet logging). They're small diffs and remove work that happens hundreds
to thousands of times per ride. #6 is the biggest lever on raw panel draw for a
P-OLED device and is mostly a theming change.

---

## Architectural note

The core structure (clock → recorder → coach → UI as cold-ish flows) is sound;
none of the above requires a rewrite. The recurring theme is **per-second work
that should be per-event work**: the app treats the 1 Hz clock tick as a trigger
to re-send, re-copy, re-scan, and re-post things that only change occasionally.
Deriving each side effect from *its own* change source (target changes, segment
changes, meaningful power deltas) rather than from the clock tick removes the
majority of the waste without changing the architecture.
