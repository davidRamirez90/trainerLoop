# Host-side Compose visual verification

The mandatory development pipeline and requirements by task type are defined in
[root AGENTS.md](../AGENTS.md). This guide documents the renderer and artifacts.

Render the **production Home screen content** with fixed representative state,
TrainerLoopTheme, Material 3, and a real interval chart. No emulator, device, BLE
manager, database, or remote service is started. The Robolectric application is
plain `android.app.Application`, deliberately avoiding TrainerLoopApplication.

## Commands

From the repository root, with the existing JDK 17 / Android SDK environment.
In the prepared Codex Cloud workspace, use `source /workspace/toolchains/activate.sh`
if that environment is not already active; do not rerun its installation script.

```bash
./scripts/compose-screenshots.sh record   # create/update references intentionally
./scripts/compose-screenshots.sh compare  # render current UI and produce diffs
./scripts/compose-screenshots.sh verify   # fail on differences or missing references
```

The script invokes real Roborazzi 1.9.0 tasks, filtering to the screenshot package:

```bash
cd android
./gradlew :app:recordRoborazziDebug --tests 'com.trainerloop.ui.screenshots.*'
./gradlew :app:compareRoborazziDebug --tests 'com.trainerloop.ui.screenshots.*'
./gradlew :app:verifyRoborazziDebug --tests 'com.trainerloop.ui.screenshots.*'
```

The existing script propagates Gradle failures, preserves PNGs, and prints absolute
image/report paths after success. Comparisons are retained between runs; use the
current run's report and file timestamps to avoid confusing stale comparison images
with newly rendered output. Unchanged comparisons may produce no new actual/diff
PNG; `record` renders reference PNGs when a fresh standalone capture is needed.

`verifyComposeScreenshots` is not a built-in task and has not been invented here.
Plain unit tests do not record references. Keep reference PNGs in Git; do not
record to make an unexplained verification failure disappear.

## Locate and inspect

- References: `android/app/src/test/screenshots/home-planned-{light,dark}.png`.
- Comparison images: alongside references as `*_actual.png` and `*_compare.png`
  (Git ignored). Roborazzi uses the configured `src/test/screenshots` output directory.
- Roborazzi report: `android/app/build/reports/roborazzi/index.html`.
- JUnit results: `android/app/build/test-results/testDebugUnitTest/`.

Future Codex agents must open the actual PNG with `view_image` using its absolute
workspace path. A passing task or pixel count alone is **not visual inspection**.
Before editing, record/copy a known reference. Change the production composable,
run `compare`, open both reference and actual/diff PNGs, and confirm the intended
change. Only then record the approved new references and run `verify`.

## Extend coverage

Add JUnit 4 tests under `com.trainerloop.ui.screenshots`, following
`HomeScreenshotTest`. Use native graphics, API 35, deterministic state, locale,
font scale, theme, and phone dimensions. Keep callback implementations inert.
Where screens collect a ViewModel, separate the state-driven content from the
service-owning route and have both production and tests call the same content.
Do not duplicate the UI in the test. Add explicit fixtures for loading, errors,
long text, larger fonts, scrolling, and other screens as needed.

## Tool selection and fidelity

Google's [Compose Preview Screenshot Testing](https://developer.android.com/studio/preview/compose-screenshot-testing)
is host-side and its Gradle-only requirements accommodate AGP 8.5 / Kotlin 1.9.
It uses experimental configuration and a separate screenshot source set with
Layoutlib. It is viable, but unnecessary for this state-driven proof of concept.
[Roborazzi](https://github.com/takahirom/roborazzi/tree/1.9.0) integrates with
JUnit/Robolectric and Compose test APIs without upgrading the app toolchain.
Pinned Roborazzi 1.9.0 and Robolectric 4.14.1 preserve Kotlin 1.9 compatibility.

The fixture captures the Home content viewport at 411 × 891 dp / xxhdpi
(1233 × 2673 px), in light and dark themes. It uses actual app composables,
typography, colors, shapes, icons, chart, and state. It excludes the surrounding
navigation scaffold and Android system bars. The connected state is a fixture,
not evidence of device connectivity.

Native host graphics are useful for layout review and repeatable regressions,
but are not proof of pixel-identical output on every physical Android device.
Platform/OEM fonts, GPU effects, system insets, navigation, input, animations,
and hardware behavior need separate coverage. Keep baselines on the same pinned
SDK/tool versions and host platform. This task does not test BLE or hardware.

## Proof-of-concept results

In Codex Cloud on Linux, both 1233 × 2673 PNGs rendered without an emulator and
were opened with `view_image` before and after the production edit. The fixed
state renders rider details, a ready-to-ride hero, connected trainer/heart-rate
tiles, a seven-interval workout chart, actions, and Explore cards. Typography,
icons, rounded Material 3 cards/buttons, spacing, and colors rendered without
obvious overlap, clipped text, or missing content in this fixture. The chart's
stepped outline is visible; device GPU/antialiasing equivalence was not assessed.
The dark-theme Quick Start action has little visible container separation from
its blue card, an existing styling choice worth reviewing separately.

The isolated edit adds `Spacing.xs` (4 dp) between rider name and profile metrics
in `RiderHeader`. To revert the visual change, remove that single Spacer and
intentionally record both references again; the rendering workflow remains.

- [Before (light)](visual-verification/home-planned-light-before.png)
- [Before / diff / after](visual-verification/home-spacing-comparison.png)
- [Final light reference](app/src/test/screenshots/home-planned-light.png)
- [Final dark reference](app/src/test/screenshots/home-planned-dark.png)

The light-theme diff affects 10,954 pixels, confined to x=216..588, y=66..180
(half-open bounding box `(216, 66, 589, 181)`). Name glyph rows moved up 6 px;
metric glyph rows moved down 6 px, increasing their separation by 12 px = 4 dp.
The avatar and all remaining content stayed pixel-identical. The comparison was
also visually inspected, rather than relying only on these measurements.

Measured command wall times (two screenshots per command):

| Operation | Time |
| --- | ---: |
| First record, including dependency resolution and compilation | 171 s |
| Production edit + compile + comparison | 22 s |
| Verify against old references (expected failure, both screenshots) | 14 s |
| Record inspected final references, cached compilation | 11 s |
| Verify inspected final references (both passed) | 11 s |

The first JUnit render suite took 11.7 s; later command times include Gradle
startup and report generation. These are observed timings, not guarantees.
This workflow is practical for autonomous layout iteration and screenshot
regressions. Current coverage is one Home state in two themes, not the whole app.

Final validation: `testDebugUnitTest lint` passed in 88 s: 301 tests, zero
failures/errors/skips (299 existing tests plus two screenshot fixtures). Final
`verifyRoborazziDebug` passed for both references. Verification against the
pre-edit references failed for both, as intended. Shell syntax and
`git diff --check` also passed. No BLE/hardware tests were attempted.

## Persistent-workflow validation

Root `AGENTS.md` preserves the original repository guidance and now makes
screenshot generation, actual inspection, and defect correction mandatory for
visible UI changes, with task-specific requirements for logic/BLE/persistence.
The existing script reports absolute artifact locations and propagates failures;
no second renderer or convenience script was introduced.

The follow-up validation built `assembleDebug testDebugUnitTest lint` successfully
in 124 s (the existing passing 301-test unit result and lint analysis were reused
from Gradle cache). `record` and `verify` each reran both screenshot tests and
passed in 10 s. Both fresh 1233 × 2673 Home PNGs were opened with `view_image`;
rider details, trainer/heart-rate tiles, threshold chart, actions, and Explore
cards were inspected. Their SHA-256 hashes match the prior references. This
workflow task changed no production UI. The debug APK, PNGs, and report were
checked to exist. Shell syntax, invalid-mode handling, Gradle failure propagation,
and `git diff --check` passed.
