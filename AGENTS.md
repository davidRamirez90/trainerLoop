# Repository Guidelines

## Project structure

- `android/` is the Android Gradle project; the application module is `android/app`.
- `android/app/src/main/java/com/trainerloop/domain/` contains the pure-Kotlin core (workouts, parsing, FIT, and workout-route simulation).
- `android/app/src/main/java/com/trainerloop/ui/` contains the Jetpack Compose UI and ViewModels; `data/` contains models, repositories, and Room/remote sources.
- `android/README.md` documents the app architecture, setup, and useful commands. Current product scope is documented in `android/README.md` and `android/ROADMAP.md`; `docs/plans/` contains historical implementation notes.

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

## Autonomous development workflow

1. **Understand:** Inspect the request, relevant Compose/state/navigation code,
   existing tests, and screenshot coverage. Identify affected screens/components;
   preserve established application patterns. Make a brief plan for complex work.
2. **Implement:** Keep changes focused; reuse TrainerLoop components and Material
   3 conventions. Preserve navigation/state management and avoid unrelated design
   changes or refactoring.
3. **Build:** Use the existing JDK 17 / SDK environment. Run an appropriate Gradle
   compilation task (usually `./gradlew assembleDebug` from `android/`) and fix
   compilation errors. Do not reinstall the verified environment.
4. **Render (UI tasks):** Use the existing Roborazzi/Robolectric infrastructure,
   not a competing framework. Render actual affected screens with deterministic,
   representative state. Reuse coverage or add the smallest necessary fixture.
5. **Inspect (UI tasks):** Actually open every relevant generated PNG with
   `view_image` or an available image-inspection tool, using absolute paths.
   Compare with the request, prior UI where useful, TrainerLoop design conventions,
   and basic usability/accessibility. Check clipping, overlap, truncation,
   spacing, alignment, unintended scrolling, contrast, Material 3 consistency,
   hierarchy, empty space, missing elements, and typography. Passing tests or
   pixel statistics do not establish visual inspection; never claim otherwise.
6. **Iterate (UI tasks):** Correct defects introduced by the change or unmet
   visual requirements, render again, and reopen the images. Continue until the
   requirements are met or a concrete limitation blocks progress. Use a reasonable
   budget (normally three correction cycles, then reassess); do not loop endlessly
   or silently accept broken UI. Report unresolved defects and the actual blocker.
7. **Validate:** Run relevant unit tests, screenshot regression verification for
   UI changes, lint where appropriate, and check for unintended diffs. Use targeted
   checks while iterating and broader checks before completion; substantial or
   cross-cutting changes require the full unit suite. Verify the final build.
8. **Deliver:** Concisely report changes and affected screens, build/test results,
   screenshot generation **and actual inspection**, and limitations. Link final
   screenshots/artifacts using verified absolute paths or supported attachments.
   If images cannot be attached, give real generated paths and retrieval steps.
   Verify artifact availability before saying it is accessible.

### Screenshot commands and fixtures

From the repository root:

```bash
./scripts/compose-screenshots.sh compare  # render and compare with references
./scripts/compose-screenshots.sh verify   # check visual regressions
./scripts/compose-screenshots.sh record   # intentionally update inspected references
```

These invoke `:app:compareRoborazziDebug`, `:app:verifyRoborazziDebug`, and
`:app:recordRoborazziDebug`, filtered to `com.trainerloop.ui.screenshots.*`.
See [the screenshot guide](android/COMPOSE_SCREENSHOTS.md) for exact Gradle
commands, PNG/report locations, fixture conventions, and renderer limitations.
References and retained `*_actual.png` / `*_compare.png` files are in
`android/app/src/test/screenshots/`; generated comparisons are Git ignored.

Preserve/copy existing references before editing; inspect reference and current
actual/diff images. Only record updated references after inspecting and accepting
the intended result, then run `verify`. For a new screen without references,
record its initial fixture to obtain a PNG, inspect/correct/re-record it, and then
verify. Do not overwrite baselines merely to hide regression failures. A new
reference may be accepted within the user's authorized UI task; this workflow
adds no separate permission requirement.

Current coverage includes Home light/dark via `HomeScreenshotTest`, proposals via
`HomeProposalScreenshotTest`, and the retained Focus destinations and supporting
states via `FocusMigrationScreenshotTest`. See `docs/design/focus-migration` for
the production gallery. Extend fixtures only for affected screens/states. Use deterministic workouts, activity metrics, empty/populated
history, loading/errors, or other app-specific data as relevant. Avoid real user
data, accounts, network services, and live BLE. Keep service-owning routes intact;
where necessary extract state-driven content used by both production and tests.
Do not duplicate screen UI in test code or render every screen without need.
Navigation, dialogs, sheets, progress, colors, typography, spacing, buttons, and
shared components all count as visible UI; cover their affected contexts/states.

### Requirements by task type

- **UI:** Compile, render affected screens, open and inspect actual PNGs, iterate
  on visual defects, run appropriate tests, and verify final references.
- **Business logic:** Compile and run relevant unit tests plus broader checks as
  appropriate. Screenshots are required when visible UI behavior changes.
- **BLE:** Compile and run available mocked/simulated unit tests; identify
  hardware-dependent behavior. Never claim physical BLE functionality was verified
  without actual hardware testing. Host screenshots of connected states are fixtures,
  not connectivity evidence.
- **Database/persistence:** Validate relevant Room entities, DAOs, migrations, and
  persistence behavior with appropriate tests and compilation. Screenshots are
  required only if visible UI behavior changes. Device-required tests must be
  identified explicitly; compiling them is not evidence they ran.
