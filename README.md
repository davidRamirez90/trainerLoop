# TrainerLoop

TrainerLoop is a native Android app that turns a Bluetooth smart trainer into an
indoor-cycling computer. It supports structured ERG workouts, an FTP ramp test, FIT recording, and
intervals.icu sync. A virtual route overlay during workouts remains optional.
Workout building, standalone free riding, GPX routes, and live coaching were
removed in the October 2026 scope cleanup.

The earlier React/Vite proof of concept has been retired. Android is the only
supported application.

## Repository layout

- [`android/`](android/) — Android Gradle project and application source.
- [`android/README.md`](android/README.md) — architecture, features, setup, and
  development commands.
- [`docs/plans/`](docs/plans/) — Android product and implementation plans.
- [`rides/`](rides/) — FIT fixtures used by Android replay and decoder tests.

## Development

Requirements:

- JDK 17
- Android SDK
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
