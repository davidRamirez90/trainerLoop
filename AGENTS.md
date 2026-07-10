# Repository Guidelines

## Project structure

- `android/` is the Android Gradle project; the application module is `android/app`.
- `android/app/src/main/java/com/trainerloop/domain/` contains the pure-Kotlin core (coaching, parsing, FIT, and ride simulation).
- `android/app/src/main/java/com/trainerloop/ui/` contains the Jetpack Compose UI and ViewModels; `data/` contains models, repositories, and Room/remote sources.
- `android/README.md` documents the app architecture, setup, and useful commands. Product and implementation notes live in `docs/plans/`.

## Build and test

Run commands from `android/` with JDK 17:

```bash
./gradlew testDebugUnitTest lint
```

Use `./gradlew assembleDebug` for a debug APK. Instrumentation tests require an Android device or emulator.

## Kotlin and tests

- Use Kotlin/Compose function components and 2-space indentation.
- Do not use wildcard imports; keep imports explicit and follow existing naming conventions.
- Use JUnit 4 for tests, with MockK and Turbine where appropriate. Keep Android-independent logic in `domain/` so it remains unit-testable.

## Commits and documentation

Use concise conventional prefixes such as `feat:`, `fix:`, `test:`, `chore:`, and `chg:`. Update `android/README.md` or the relevant `docs/plans/` note when architecture, setup, or delivery scope changes.
