# TrainerLoop: fix stop/save, charts, intervals.icu, redesign

## Context

The app controls a bike trainer (ERG via FTMS) and streams power/HR, but **stopping a workout silently discards it** — no session saved, no FIT file. Root cause confirmed at `WorkoutViewModel.kt:224-235`: `stop()` resets the recorder, wipes `uiState.samples`, and sets `_finishEvent.value = null` instead of emitting the finish event. The Complete screen (`WorkoutCompleteViewModel.init`, lines 55-59) is the **only** place that saves to Room and writes the FIT file — it is only reached via `finishEvent`, so a manual stop never saves. Both stop entry points (Stop button `WorkoutScreen.kt:317`, top-bar back `WorkoutScreen.kt:95`) route through this one function.

Additional asks: stop confirmation dialog, visible charts (interval blocks + power line barely visible, HR never drawn), intervals.icu integration (auto-upload FIT, import workout of the day, sync FTP), and a general visual modernization.

Test device: Pixel 2 via USB (API 30 = minSdk; no dynamic color).

## Phase 1 — Fix stop/save bug (critical)

Files: `ui/workout/WorkoutViewModel.kt`, `ui/workout/WorkoutScreen.kt`, `ui/TrainerLoopApp.kt`, `ui/components/FitShareHelper.kt`, `res/xml/file_paths.xml`, `WorkoutViewModelTest.kt`

1. `stop()`: call `maybeEmitFinish()` (WorkoutViewModel.kt:353) **before** `recorder.value?.reset(...)` and before clearing samples; delete the `_finishEvent.value = null` line. `maybeEmitFinish()` already early-returns on empty samples.
2. Zero-sample stop must still exit: add `onExit: () -> Unit` to `WorkoutScreen`, wired to `popBackStack()` in `TrainerLoopApp.kt`. Stop handler: empty samples → `onExit()`, else `stop()` (navigation then happens via the existing `finishEvent` → `onSessionFinished` chain, TrainerLoopApp.kt:158-168 — unchanged).
3. `FitShareHelper.createFitFile`: `cacheDir` → `filesDir` (cache is evictable) and fixed filename → timestamped from `startTimeMs` (`trainer_loop_yyyyMMdd_HHmmss.fit`), so files persist and don't overwrite each other. Add matching `<files-path>` to `file_paths.xml` so the existing FileProvider share keeps working. Skipped MediaStore/Downloads — share sheet + Phase 4 auto-upload covers access; add if user wants file-manager visibility.

Tests: extend `WorkoutViewModelTest` — `stop()` with samples emits `finishEvent` carrying them; `stop()` with no samples leaves it null.

## Phase 2 — Stop confirmation (same PR as Phase 1)

File: `WorkoutScreen.kt` only.

- `showStopConfirm` state; Stop button + top-bar back set it instead of stopping (skip dialog when `elapsedSec == 0` → exit directly).
- `BackHandler(enabled = elapsedSec > 0) { showStopConfirm = true }` (androidx.activity.compose, already on classpath) — fixes gesture-back discarding rides.
- Plain M3 `AlertDialog`: "End workout? Your ride will be saved." Confirm → stop handler. No save/discard choice here — Complete screen already has Save/Discard (`WorkoutCompleteScreen.kt:163-182`).

## Phase 3 — Charts (keep hand-rolled Canvas, no chart lib)

Files: `ui/components/WorkoutChart.kt`, `ui/components/WorkoutMiniChart.kt`, delete `ui/components/IntervalTimeline.kt` (dead code — grep-verify first), caller updates in `WorkoutScreen.kt` / `WorkoutDetailScreen.kt` / `WorkoutLibraryScreen.kt`.

1. **Interval blocks**: replace thin translucent target bands (WorkoutChart.kt:55-77) with TrainerRoad-style full-height-from-zero blocks per segment (ramps subdivided), colored by power zone. New pure `fun zoneColor(targetWatts: Int, ftp: Int): Color` (gray <55% FTP, blue <75, green <90, yellow <105, orange <120, red above), alpha ~0.55. Add `ftp` param, wired from `ProfileRepository.getProfileSync().ftp`.
2. **Power line**: current stroke is `3f` raw px (~hairline). Use `2.5.dp.toPx()`, drawn on top of blocks in high-contrast color.
3. **HR line**: new red path scaled to its own 40–200 bpm axis; break path where `hrBpm == 0` so dropouts don't draw a floor. (HR was simply never implemented.)
4. **Y axis**: auto-scale `max(peak target, peak power) * 1.1` instead of fixed 400 W; two faint gridlines at FTP and FTP/2.
5. **Cursor**: theme `onSurface` at alpha 0.7, `1.5.dp` (currently hardcoded white — invisible in light theme).
6. `WorkoutMiniChart`: reuse `zoneColor()` for the profile fill; dp stroke.

Test: small unit test for `zoneColor()` boundaries.

## Phase 4 — intervals.icu integration

HTTP client: **HttpURLConnection + existing kotlinx-serialization** (`Json { ignoreUnknownKeys = true }`). Four endpoints, Basic auth (`API_KEY:<key>`), ~120 lines — no new dependency.

Files:
- `AndroidManifest.xml`: add `INTERNET` permission (currently missing).
- **New** `data/source/remote/IntervalsIcuClient.kt` (only new file): suspend funcs on Dispatchers.IO — `uploadActivity(athleteId, fitBytes, name)` (multipart POST `/api/v1/athlete/{id}/activities`), `getAthlete(id)` (FTP/weight), `getTodaysWorkoutEvents(id, date)` (`/events?oldest&newest&category=WORKOUT`), `downloadZwo(id, eventId)`.
- `UserProfile.kt` + `ProfileRepository.kt`: add `intervalsIcuAthleteId`, `intervalsIcuApiKey` fields + prefs keys + update helper. Plain SharedPreferences like everything else.
- `SettingsScreen.kt` / `SettingsViewModel.kt`: "intervals.icu" section with athlete-ID field + API-key field (`PasswordVisualTransformation`); replaces dead stub rows (`SettingsScreen.kt:142-189` area).
- `WorkoutCompleteViewModel.kt`: after `createFitFile()` succeeds and credentials are set, launch upload; `uploadStatus` line in ui state shown on Complete screen ("Uploading… / Uploaded / Failed: …"). Upload failure never blocks local save. Fire-and-forget; add retry-on-launch only if uploads flake.
- `WorkoutLibraryViewModel.kt` + `WorkoutLibraryScreen.kt`: "Sync" action (visible when credentials set): fetch today's workout events → download ZWO → reuse `WorkoutImporter.import("$name.zwo", content, ftp)` verbatim → persist via existing imported-workouts JSON path (WorkoutLibraryViewModel.kt:135-155). Same sync calls `getAthlete` and updates FTP/weight in profile.

Test: one unit test parsing a canned events-JSON response.

## Phase 5 — Redesign (bounded; no navigation rewrite)

Files: `ui/theme/*`, `res/values/themes.xml`, `TrainerLoopApp.kt`, `HomeScreen.kt`, `SettingsScreen.kt`, new `ui/history/HistoryScreen.kt`, shared `MetricBadge`.

1. **Theme**: proper M3 tonal palette refresh in `Color.kt` (dark scheme with true-dark surfaces — good on handlebars); `themes.xml` parent → `Theme.Material3.DayNight.NoActionBar` (currently ancient `android:Theme.Material.Light`); `enableEdgeToEdge()` in MainActivity. Skip dynamic color (Pixel 2 predates it).
2. **History tab**: replace `PlaceholderScreen` with a `LazyColumn` of session cards from existing `SessionRepository` (date, name, duration, avg power) — biggest visual win, all data exists. Skip session-detail screen for now.
3. **Ride tab**: delete from `Screen.bottomTabs` (stub; Home covers free ride) → clean 4-tab bar.
4. **Consistency**: dedupe `MetricBadge` (duplicated in Home + Settings) into `ui/components/`; Home header card (name + FTP/weight chips + Start CTA); library cards get the zone-colored mini chart from Phase 3; consistent TopAppBar treatment.
5. Workout player: larger tabular numerals for power/HR/cadence, zone-tinted live power. Layout unchanged.

Skipped: animations, custom fonts, tablet layouts, refactoring the Application-singleton workout handoff (works; touch only if it bites).

## Sequencing

1+2 ship first as one small PR (~70 lines) → 3 → 4 → 5. Each phase independently verifiable.

## Verification

- `./gradlew testDebugUnitTest` after each phase (existing suites: WorkoutViewModelTest, TelemetryRecorderTest, SessionRepositoryTest, FitEncoderTest golden-master must stay green).
- `./gradlew assembleDebug && adb install -r` to the connected Pixel 2 after each phase.
- End-to-end (Phase 1/2): start workout, ride ~30 s, Stop → dialog → confirm → Complete screen with summary; verify FIT via `adb shell run-as ... ls files/fit_exports` and open/share the file; hardware back + gesture back also show the dialog.
- Phase 4 (needs user's real credentials in Settings): finish short ride → activity appears on intervals.icu; plan a workout for today on intervals.icu → Sync → appears in library with FTP-scaled targets.
- Phase 3/5: visual pass on device in light + dark.
