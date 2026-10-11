# TrainerLoop

Personal Android indoor-cycling app for structured training with a Bluetooth
FTMS smart trainer. Single user, single device; no app accounts or server backend.
intervals.icu is an optional external integration.

## Focus navigation

Today, Workouts, and History are the three primary tabs. Open Profile from the
Today avatar; its back action returns to Today. Devices remains a modal setup
flow from Today. Opening a workout enters idle Player; Play starts recording.

## Current functionality

- Structured ERG workouts with stepped, ramp, and untargeted segments. Start,
  pause/resume, stop, skip intervals, toggle ERG, adjust intensity in 1% steps
  (±20%), reset intensity, and extend recovery by 30 seconds.
- Live power/target range, cadence, HR, interval countdown and on-target progress;
  workout profile and ride statistics. Landscape charts support zoom, panning,
  and automatic following. Ride averages include coasting and exclude dropouts.
- Bluetooth FTMS trainer and HR-sensor scanning/connection. Trainer control
  observes readiness so late attachment/reconnection applies the current target.
  Untargeted intervals send zero watts rather than retaining the previous load.
- Interval beeps, spoken interval/target announcements, haptics, optional completion
  sound, screen keep-awake, and foreground service. These cues are independent of
  the removed coaching system.
- Workout library: Sweet Spot (88–94% FTP work intervals), Power Pyramid, and a
  structured Endurance Ride, with built-in targets scaled to FTP. Import `.zwo`, `.mrc`, `.erg`, and `.json`;
  search, category filters, favorites, duplication, and deletion of stored workouts.
  Existing workouts created with the retired builder remain available.
- FTP ramp test: five-minute warm-up, one-minute +20 W steps, low-power exhaustion
  detection (sensor dropouts are excluded), estimate from 75% of best rolling
  one-minute power. Accept/discard FTP and optionally push it to intervals.icu.
- Optional generated virtual route overlay during workouts: simulated speed,
  distance, elevation and gradient. Profile controls rider/bike weight, rolling
  resistance and aerodynamic drag. This overlay does not change ERG targets.
- Completion: ride statistics, telemetry chart, Save/Discard, FIT sharing. Save
  persists full telemetry locally and initiates upload if credentials are set.
- History: saved sessions and sync/completion status; six-week **riding-duration**
  chart; session detail with power-zone time distribution and telemetry chart.
- intervals.icu: today's workout preview/Open workout and library sync, FTP/weight
  sync, FIT upload on Save, manual upload for completed unsynced sessions, and
  optional FTP write-back after a ramp test. Sync covers today, not a week calendar.
- Profile: rider name, FTP, weight, max HR/zone tables, default ERG bias, simulation
  options, completion sound, integration credentials, and About/version. Power-zone
  tables use the same six bands and exact watt boundaries as charts and history.

## Removed functionality and compatibility

Workout builder, standalone free ride, GPX import/route riding, live coaching,
coach profiles and summaries, route difficulty, and Zwift Click/volume-key gearing
have been removed. Untargeted segments inside imported workouts remain supported.
The hardcoded Trainer statistics page was removed rather than presented as live data.

The Room v4 schema is retained: historical route rows and legacy session metadata
are kept for compatibility, but there is no route DAO or active route/coaching flow.
Existing saved sessions are preserved. Obsolete preference keys are ignored.

## Architecture

Native single-activity Jetpack Compose app with MVVM, Coroutines/Flow, Room,
and kotlinx-serialization. `minSdk 30`, `targetSdk/compileSdk 35`, JVM 17.

- `app/`: application manager wiring, activity and foreground service.
- `ble/`: scanning, shared trainer GATT connection, FTMS data/control, and HR.
- `data/`: models, profile/session repositories, Room, and intervals.icu REST client.
- `domain/`: workout clock/math, telemetry, workout parsers, FIT, and optional
  workout-route simulation. Android-independent logic is unit-tested here.
- `ui/`: Home, Workouts/Preview/Player/Complete, History/Detail, Devices, Profile.

Only one GATT connection is opened to a trainer and shared by the telemetry and
control managers. Application-owned manager StateFlows survive navigation;
ViewModels observe changes instead of capturing manager snapshots.

## Theme architecture

Colors are a **two-layer system**, both in `ui/theme/`:

1. **Focus foundation** (`Color.kt`) — warm paper, forest, ink and sage tokens.
   Older palette constants remain compatibility references for historical/debug
   code; active Material and app-specific roles use Focus colors.
2. **Semantic roles**, built from the ramps and exposed two ways:
   - **Material roles** (`MaterialTheme.colorScheme.*`) — `LightColorScheme` /
     `DarkColorScheme` in `Theme.kt` give full coverage of the Material 3 role
     set (primary, surface, error, outline, the `surfaceContainer*` ladder,
     etc.) so nothing falls back to the Material baseline palette.
   - **`TrainerLoopColors` semantic roles** (`MaterialTheme.trainerLoopColors`,
     defined in `TrainerLoopColors.kt`) — app-specific roles with a single
     responsibility each (hero surfaces/actions, assessment, connection state, warnings,
     staleness, hero actions, chart series). Read via the
     `MaterialTheme.trainerLoopColors` extension, backed by
     `LocalTrainerLoopColors` and swapped light/dark by `TrainerLoopTheme`.

**Rule: screens never reference foundation colors (`Sun80`, `Coral40`, …) or
hex literals directly.** Only `Theme.kt` and `TrainerLoopColors.kt` may import
from `Color.kt`. Screens and shared primitives (`ui/components/`) consume
`MaterialTheme.colorScheme.*` or `MaterialTheme.trainerLoopColors.*` exclusively.

**Zone colors are an independent system.** `ZoneColors.kt` maps power-zone
index (1–6, from `%FTP` via `PowerZoneMath`) to a light/dark `ZoneColorSet`
(fill / onFill / line). This is chart/telemetry data coloring, not part of the
semantic role system above, and is looked up via `zoneColorSet(targetWatts, ftp)`
or `ZoneColors.forZone/forTarget`, never by hand-picking a hex value.

See [Focus migration](../docs/design/focus-migration/README.md) for the active
design contract, screen coverage, and visual gallery. July design specifications
are historical references.

**Validate theme changes:**

```bash
# Unit tests, including WCAG contrast tests for every role pairing
./gradlew testDebugUnitTest --tests "com.trainerloop.ui.theme.*"

# Full unit test suite
./gradlew testDebugUnitTest

# Lint (catches hardcoded colors / other resource issues)
./gradlew lint
```

---

## Getting started

### Prerequisites
- Android Studio (or the Android SDK + command-line tools)
- JDK 17
- An Android device on **API 30+** with Bluetooth LE (a physical smart trainer is
  needed to exercise the ride flow end-to-end)

### Configure the SDK
Create `local.properties` in the project root pointing at your SDK:

```properties
sdk.dir=/Users/you/Library/Android/sdk
```

### intervals.icu (optional)
Add your **athlete ID** and **API key** in the app's Settings screen to enable
planned-workout sync, FTP/weight sync, and FIT upload.

---

## Useful commands

Run from the `android/` directory. Use `./gradlew` (or `gradlew.bat` on Windows).

```bash
# Build the debug APK
./gradlew assembleDebug

# Install onto the connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Build + install in one step
./gradlew installDebug

# Run all JVM unit tests (domain, parsers, FIT, BLE parsers, ViewModels)
./gradlew testDebugUnitTest

# Run a single test class
./gradlew testDebugUnitTest --tests "com.trainerloop.domain.fit.FitEncoderTest"

# Build the minified release APK (ProGuard/R8)
./gradlew assembleRelease

# Lint
./gradlew lint

# Full local verification (compile + unit tests)
./gradlew check

# Clean
./gradlew clean
```

### On-device debugging

```bash
# Stream app logs
adb logcat --pid=$(adb shell pidof -s com.trainerloop.app)

# Inspect exported FIT files (debug builds)
adb shell run-as com.trainerloop.app ls files
```

---

## Verification

Run from `android/` with JDK 17 and Android SDK 35:

```bash
./gradlew testDebugUnitTest lint assembleDebug compileDebugAndroidTestKotlin
```

Unit tests cover retained parsers, FIT, workout math/clock, telemetry, virtual
simulation, BLE packet/control logic, repositories, and ViewModels. Regression
coverage includes late trainer readiness, disconnect recording continuity, HR
clearing, intensity ranges, dropout-aware ramp detection, FTP-scaled workouts, and
skip/pause-safe recorded riding time.
Compose and Room migration tests require an Android device/emulator to execute.
A physical trainer is needed to validate reconnection, load release, ERG toggling,
and cues end to end.

## Known limits

- Imported percentage targets are resolved to watts at import/sync time. Reimport
  or sync after changing FTP to refresh those workouts; built-ins scale at load.
- Historical IF/TSS/zone details currently use the current profile FTP, rather than
  an FTP snapshot saved per session.
- No trainer spindown/calibration, RPE/notes write-back, lap markers, interrupted
  process recovery, future plan calendar, or local FTP/TSS trend charts.
- `docs/plans/`, old implementation/review notes, and screenshot reference documents
  are historical records. This README and `ROADMAP.md` define current scope.

### Compose screenshots without a device

See [COMPOSE_SCREENSHOTS.md](COMPOSE_SCREENSHOTS.md) for host-side production screen
rendering, reference comparison, actual PNG inspection, and adding screens.
From the repository root, run `./scripts/compose-screenshots.sh compare` to render
and compare, or `./scripts/compose-screenshots.sh verify` to check references.
