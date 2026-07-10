# TrainerLoop

A personal Android app that turns a smart bike trainer into a full indoor-cycling
computer. It connects to the trainer over Bluetooth, drives it in **ERG mode**
(the app commands a target wattage and the trainer holds it), plays structured,
virtual, and free rides with live coaching, records every ride as a FIT file, and
syncs with [intervals.icu](https://intervals.icu).

> **Scope.** Single-user, single-device app (built and tuned for a Pixel 2 XL).
> Not published to the Play Store. No accounts, no cloud, no multi-user — see
> [`ROADMAP.md`](ROADMAP.md) for what is intentionally out of scope.

---

## What it does

### 🚴 Ride modes
- **Structured workouts** — ERG-controlled interval sessions. The app sends the
  target watts for each segment; the trainer holds them. Live target-vs-actual
  power band, zone-colored power, HR, cadence, and an interval chart.
- **Virtual rides** — ride a GPX route as a simulated course. A physics model
  (gradient, gearing, rolling resistance, aero drag) turns your power into speed
  and position along the route; the trainer resistance follows the terrain.
- **Free rides** — open-ended riding with a virtual drivetrain (shift gears with
  the **volume keys**) and optional gated ERG targets.
- **Ramp test** — progressive step test to estimate FTP.

### 🎙️ Live coaching
A configurable "coach" watches expected-vs-actual effort during a ride, models
athlete state, decides when it's worth speaking, and calls out intervals and
targets via **text-to-speech** (plus a tone beep on interval changes). Coach
personalities are data-driven JSON assets in
[`app/src/main/assets/coach_profiles/`](app/src/main/assets/coach_profiles):
`default`, `mentor`, `drill-sergeant`, `silent-scientist`, `base-builder`.

### 📊 During the ride
- ERG on/off toggle, intensity bias (±1% / ±5%, clamped ±20%), skip interval,
  and extend-current-recovery (+30s).
- Time-in-zone bar, voice call-outs, audio cue on interval change.
- **Landscape immersive chart** with 1×/2×/4×/8× zoom that auto-follows the cursor.
- Runs under a **foreground service** with a partial wake-lock so the ride and BLE
  link survive screen-off; the screen is kept awake while the clock runs.
- Auto-reconnect on BLE drop (re-arms trainer control and re-subscribes).

### 📚 Workouts & routes
- Import workouts from `.zwo` (Zwift), `.mrc`, `.erg`, and `.json`.
- Import routes from `.gpx`.
- Workout library with search, category filter, favorites, duplicate, and a
  (stepped-interval) workout builder.

### ☁️ intervals.icu integration
- Home **Quick Start** card shows today's planned workout; tap to download the
  `.zwo`, parse it, and drop straight into the player.
- Auto-sync **FTP / weight** from the athlete endpoint.
- Auto-**upload the FIT file** after a ride.

### 🗂️ History & data
- Every ride is saved to a local **Room** database with full telemetry.
- History tab with a 6-week calendar strip and per-session detail.
- Rides are encoded to **FIT** files (custom encoder/decoder) for sharing and upload.

---

## Architecture

Single-activity Jetpack Compose app, MVVM, organized by layer:

```
app/src/main/java/com/trainerloop/
├── app/          Application, MainActivity, foreground service, manager wiring
├── ble/          BLE stack: scanning, GATT connection, FTMS control + data, HR
│   └── model/    FTMS IndoorBikeData & Heart Rate Measurement packet parsers
├── data/
│   ├── model/    Domain models (Session, Workout, Route, TelemetrySample, Coach…)
│   ├── repository/  Session, Route, Profile repos + intervals.icu uploader
│   └── source/
│       ├── local/   Room database, DAOs, entities, coach-profile loader
│       └── remote/  intervals.icu REST client
├── domain/
│   ├── coach/    Live coaching engine (expectation, athlete model, messaging)
│   ├── fit/      FIT file encoder / decoder
│   ├── parser/   .zwo / .mrc / .erg / .json / .gpx parsers
│   └── sim/      Virtual-ride & free-ride physics (drivetrain, speed, terrain)
└── ui/           Compose screens + ViewModels (home, workout, freeride, routes,
                  history, library, devices, settings, coach, theme, components)
```

**Key design points**
- **One shared GATT connection** to the trainer (`TrainerLoopApplication`), split
  between a data manager (`FtmsManager`) and a control manager
  (`FtmsControlManager`) — BLE allows only one GATT client per peripheral.
- BLE/trainer managers live on the `Application` as `StateFlow`s so ride state
  survives navigation and rotation.
- Coaching, physics, FIT, and parsing are pure Kotlin in `domain/` — unit-testable
  with no Android dependencies.

**Stack:** Kotlin, Jetpack Compose (Material 3), Navigation-Compose, Coroutines/Flow,
Room, kotlinx-serialization. `minSdk 30`, `targetSdk 35`, JVM 17, Gradle 8.7.

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

## Testing

Unit tests live under [`app/src/test/`](app/src/test) and cover the pure-Kotlin
core: FIT encode/decode (golden-master), workout parsers, virtual/free-ride
physics, workout math and clock, the coaching pipeline (including replay golden
tests and a scripted athlete simulator), repositories, the intervals.icu client,
and the ViewModels (via Turbine + MockK + coroutines-test).

```bash
./gradlew testDebugUnitTest
```

---

## Documentation

- [`ROADMAP.md`](ROADMAP.md) — feature status and what's intentionally out of scope
- [`IMPLEMENTATION_PLAN.md`](IMPLEMENTATION_PLAN.md) — phased build history
- [`BATTERY_OPTIMIZATION.md`](BATTERY_OPTIMIZATION.md) — foreground-service / wake-lock notes
- [`docs/plans/`](../docs/plans) — design docs for major features
