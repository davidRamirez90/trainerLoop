# App flow and view documentation

**Capture status:** The [Focus migration gallery](../design/focus-migration/index.html)
contains production Compose screenshots for the retained app destinations and
supporting states, rendered with Roborazzi without an emulator and visually
inspected. See [the migration specification](../design/focus-migration/README.md)
and [the Compose screenshot guide](../../android/COMPOSE_SCREENSHOTS.md).
The device-capture instructions below remain an optional route for OS-owned
pickers/permission windows; that full emulator run has not succeeded and
`screenshots/current/` has no device captures.

The current product documentation is [app-flow-report.html](app-flow-report.html).
It maps the navigation destinations, player states, dialogs, menus and Android
system views. The screenshot embedding script will make the completed gallery portable as a
single HTML file, retaining original PNGs and a capture manifest alongside it.

## Planned device capture conditions

- Current debug app, version 0.1.0; Android 11 (API 30), Pixel 6 software emulator.
- Portrait resolution 720×1600, density 320; landscape 1600×720.
- Actual production Compose screens and Android-owned UI, captured with
  `UiAutomation.takeScreenshot` or `adb exec-out screencap -p`. No mockup images,
  HTML recreations, Compose previews, or old pre-cleanup screenshots are used.
- No physical trainer or HR sensor is attached. Live Player captures therefore
  show unavailable sensor readings.
- History, Session Detail, Completion, FIT sharing and FTP results use synthetic
  fixture telemetry: 1,200 one-second samples, 180–269 W, 84–90 rpm, 138–155 bpm.
  These screenshots demonstrate the UI, not an actual ride or measured FTP.
- One locally stored documentation workout is seeded for library menu coverage.
- Placeholder credentials expose the inline FTP push prompt; no FTP is pushed
  externally and no real integration credentials are used. Network access is
  disabled on the capture emulator.
- The completion routes are initialized with their normal telemetry-file payload
  by the capture fixture. The remaining app screenshots are reached through the
  navigation host and actual UI interactions. System pickers are opened by the
  app's real actions.

## Reproduce

Use a disposable emulator: the fixture seeds profile settings, a sample ride and
a sample workout into that emulator's app data. Do not run it on a personal
installation whose settings/history you want to preserve.

1. Build from `android/` with JDK 17:

   ```bash
   ./gradlew assembleDebug assembleDebugAndroidTest
   ```

2. Start an API 30 emulator and configure the display:

   ```bash
   adb shell wm size 720x1600
   adb shell wm density 320
   adb shell svc wifi disable
   adb shell svc data disable
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
   ```

3. Before granting location permission, launch the app and capture its actual
   Android location permission prompt to `permission.png`. Android 11 requires
   location permission for Bluetooth scanning. Then grant it for unattended capture:

   ```bash
   adb shell pm grant com.trainerloop.app android.permission.ACCESS_FINE_LOCATION
   ```

   On Android 12+, Bluetooth scanning/connection have their own permissions;
   Android 13+ also has a notification prompt. Capture those prompts separately
   when updating this report on a newer OS image.

4. Run the opt-in capture fixture. This class skips itself during ordinary test
   runs unless `captureFlows=true` is supplied:

   ```bash
   adb shell am instrument -w \
     -e class com.trainerloop.documentation.FlowScreenshotCaptureTest \
     -e captureFlows true \
     com.trainerloop.app.test/androidx.test.runner.AndroidJUnitRunner
   ```

   Fixture source:
   `android/app/src/androidTest/java/com/trainerloop/documentation/FlowScreenshotCaptureTest.kt`.
   PNGs are written to the app's external files directory, `flow-captures/`.

5. Pull the PNGs into this report's screenshot directory. Keep the permission
   capture alongside them. Then, from the repository root, refresh the report:

   ```bash
   python3 docs/reports/tools/embed_screenshots.py
   ```

6. Open the HTML in a browser. Verify the three maps, all gallery images, the
   click-to-enlarge dialog, view inspector, search, mobile layout and Print/PDF.

Android's picker and permission styling can vary by OS image. This report records
one reproducible device configuration; it does not assert physical Bluetooth or
intervals.icu end-to-end validation.

## Home redesign proposals

The [Home design comparison](../design/home-redesigns/index.html) contains three
native Compose concepts, an offline screenshot gallery, proposed launch flows,
and research/specifications. See the [design spec](../design/home-redesigns/DESIGN_SPEC.md).
These remain the original review artifacts. Option 1 (Focus) is now applied to
production on `feat/focus-design`; the migration gallery documents the resulting
app separately from the proposals.
