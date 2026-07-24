# TrainerLoop

TrainerLoop turns a Bluetooth smart trainer into an indoor-cycling computer. The
native Android app supports structured workouts, GPX-based virtual rides, free
rides, live coaching, FIT recording, and intervals.icu sync. A React/Vite web
dashboard remains available for browser development, including repeatable
telemetry simulation and ad-hoc workout logs.

## Repository layout

- [`android/`](android/) — Android Gradle project and application source.
- [`src/`](src/) — React web dashboard source.
- [`android/README.md`](android/README.md) — architecture, features, setup, and
  development commands.
- [`docs/plans/`](docs/plans/) — Android product and implementation plans.
- [`rides/`](rides/) — FIT fixtures used by Android replay and decoder tests.

## Development

Requirements:

- JDK 17
- Android SDK
- Node.js 20+ for the web dashboard
- A physical Android device for BLE and full end-to-end ride testing

Run the standard checks from the repository root:

```bash
cd android
./gradlew testDebugUnitTest lint
```

Build a debug APK:

```bash
cd android
./gradlew assembleDebug
```

See the [Android README](android/README.md) for configuration, architecture, and
device-testing details.

Run the web dashboard:

```bash
npm install
npm run dev
```

Validate the web dashboard with `npm run lint`, `npm test -- --run`, and
`npm run build`. The dashboard's development simulator can feed steady,
fatigue, recovery, and sensor-dropout telemetry through the normal workout
processing path without connected hardware. Workout events are retained
locally and can be opened from the **Workout Logs** dialog.

The planned macOS CoreBluetooth bridge—which lets the browser control a native
FTMS peripheral that Android can pair with—is documented in
[`docs/macos-bluetooth-simulator.md`](docs/macos-bluetooth-simulator.md).
