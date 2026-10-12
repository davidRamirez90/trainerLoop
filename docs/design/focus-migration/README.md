# Focus migration

Branch: feat/focus-design. Selected direction: [Focus proposal](../home-redesigns/DESIGN_SPEC.md).

## Design contract

Focus is a structured-training app with a quiet editorial shell: warm paper, forest ink, sage actions, serif page/section headings, sans-serif controls, and stable tabular ride metrics. Training data remains factual: device connection does not imply recovery/readiness, imported plans do not become adaptive recommendations, and saved history does not become a future calendar.

Light canvas is #F5F4ED, primary forest #163E32, body ink #172B25, hero action #DFEDC8. Dark canvas is #101C17 with graduated green surfaces and light sage actions. The Home hero remains forest in both modes. Shared semantic colors cover chart series, status, warnings, and errors. The six power-zone colors retain their functional distinction in training charts and distributions. Power and heart-rate reference dialogs use the quiet paper/forest/sage shell with grouped rows, readable ranges and effort descriptions. Theme-dependent chart colors now follow the explicit app theme, including deterministic previews.

Phone gutters are 24 dp, cards 24 dp radius with 20–24 dp padding, primary actions at least 56 dp tall, and compact actions at least 48 dp. Player metrics keep sans-serif tabular numerals rather than inheriting editorial heading typography.

## Screen migration

| Destination | Applied design and retained behavior |
|---|---|
| Today | Brand/avatar, local date, One good ride headline, plan-first forest card, actual workout profile, compact equipment setup, alternative workout, persistent latest saved ride. No plan has a library invitation. Loading disables launch; failure offers retry. |
| Primary navigation | Today, Workouts, History; quiet labeled selection mark. Profile moves to avatar and has a back action. Tab state restoration and existing routes remain. |
| Workouts | Editorial app bar, Focus search/filter/sync/import styling, neutral assessment card and workout cards, actual interval mini profiles, favorites and overflow actions. All supported imports and FTP ramp test remain. |
| Workout preview | Editorial headings, Focus metrics/cards/chart and persistent Open workout action. Opens idle Player; recording begins on Play. |
| Player portrait | Focus shell, cards, sheet, buttons, chart series, warnings, and top bar. Numeric effort readings remain primary. Transport, intensity, ERG, skip, recovery extension, stats pager, and virtual overlay remain. |
| Player landscape | Same Focus surfaces/actions and readable numeric hierarchy in the existing landscape layout. |
| Player controls/stop | Rounded Focus sheet, semantic action colors and themed stop confirmation. Runtime lifecycle, BLE managers, foreground service, completion cues and stop semantics remain in the route. |
| Completion | Ride complete editorial heading, Focus summary/metric cards, FIT sharing/save/discard/Done actions, FTP result and intervals.icu push states. Save does not navigate away. |
| History | Focus editorial headers, six-week ride-time chart and saved-session cards; empty-state styling. |
| Session detail | Focus summary, zone distribution, metric rows, sample chart and upload action. Historical TSS/IF still use current FTP; the redesign does not alter this model. |
| Profile | Detail page with a back action; Focus grouped fields, sliders, switches, athlete/HR/ride preferences, integrations and About. Existing profile persistence is retained. |
| Devices | Focus modal sheet, permission explanation, connected/available cards, scan/connect/disconnect actions. Android's actual permission dialog stays OS-owned. |
| Supporting dialogs | Power/HR zones share a paper-backed editorial guide with a grouped surface, subtle sage zone badges, forest ranges and effort descriptions. About, workout overflow, errors, FTP result/push use the same Material roles, typography and shapes. OS file picker/FIT chooser retain Android styling. |

## Verification approach

Native Compose layouts are captured with Roborazzi/Robolectric, without an emulator. The fixture class is FocusMigrationScreenshotTest. Home, library, history, session detail and player presentation use the exact production content functions. Settings/Devices/Completion fixtures supply mocked ViewModels with deterministic flows; they do not create actual BLE managers or accounts. The player route keeps real service/audio/haptics lifecycle while its presentation accepts state and explicit actions.

The fixtures cover all retained primary screens, dark-mode examples, no-plan/loading/error Home, a 360 dp Home at 150% text scaling, empty history/library, player idle/running/paused/landscape, stop/zone/About dialogs and overflow interaction assertions, device permissions, and FTP result and the integration push prompt. These are host UI fixtures, not hardware or live intervals.icu verification.

Original pre-migration Home references are preserved in android/visual-verification/focus-before. Proposal artifacts remain separate from production migration captures.

## Reproduce

Source /workspace/toolchains/activate.sh, then run scripts/compose-screenshots.sh record or verify with the Gradle flags documented in android/COMPOSE_SCREENSHOTS.md. Record is for deliberately accepting inspected references; verify is for routine regressions.

The migration gallery in this directory embeds inspected native screenshots for offline review. Public sharing still requires a permitted hosting/tunnel environment.

The host popup renderer cropped the wrong window for the workout overflow menu. Its Duplicate/Delete actions are checked with Compose visibility assertions; the misleading image is excluded from the gallery. FTP result content and the follow-up integration prompt now use vertical layouts inside AnimatedContent, fixing overlapping children discovered during actual visual inspection.

## Validation on 2026-10-11

- Debug APK assembled successfully.
- Full unit suite: 247 tests, zero failures or skips.
- Android lint: zero errors, 12 existing warnings.
- Screenshot regression verification: all 36 screenshot/interaction tests passed against the inspected references.
- All 29 production gallery captures opened and visually inspected, including the corrected FTP result/push layouts. Original Home light/dark captures were also inspected.
- Offline HTML gallery checked at 390 px width: no horizontal overflow, 29 screenshots, enlargement dialog works.
- Physical BLE, live integration calls, and Android-owned pickers/permission dialogs were not exercised by the host fixtures.
