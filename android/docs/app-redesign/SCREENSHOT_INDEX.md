# Trainer Loop screenshot index

Captured from the USB-connected Android device on 2026-07-14. The API-key-visible capture was intentionally skipped at the user's request.

## Captured screens

| File | Screen / state |
| --- | --- |
| `screenshots/01-home-dashboard.png` | Home dashboard with disconnected trainer and today's plan loading state |
| `screenshots/02-workouts-library.png` | Workout library with categories and built-in workouts |
| `screenshots/03-history-list.png` | History list |
| `screenshots/04-profile-settings.png` | Profile and settings, top section |
| `screenshots/05-workout-builder.png` | Workout Builder with live preview and first interval |
| `screenshots/06-workout-detail.png` | Sweet Spot workout preview/detail |
| `screenshots/07-workout-player-pre-start.png` | Workout player before starting |
| `screenshots/08-workout-player-active.png` | Active workout player |
| `screenshots/09-workout-player-controls-sheet.png` | Workout player controls sheet |
| `screenshots/10-workout-finish-ready.png` | Workout at 30:00 elapsed and 0:00 remaining with Finish Workout visible; the summary transition requires recorded telemetry samples |
| `screenshots/11-routes-library.png` | GPX routes entry/library state |
| `screenshots/15-devices-empty.png` | Devices sheet with no connected devices |
| `screenshots/16-devices-scanning.png` | Devices sheet while scanning, with a discovered trainer |
| `screenshots/17-devices-connected.png` | Devices sheet after connecting the trainer |
| `screenshots/18-devices-device-details-sheet.png` | Device details / connection state sheet |
| `screenshots/19-profile-connections-filled.png` | Profile Connections section with masked intervals.icu credentials |
| `screenshots/21-profile-power-zones-dialog.png` | Power Zones dialog |
| `screenshots/22-profile-heart-rate-zones-dialog.png` | Heart Rate Zones dialog |
| `screenshots/23-profile-coach-profile-dialog.png` | Coach Profile picker dialog |
| `screenshots/25-profile-simulation-collapsed.png` | Simulation settings collapsed |
| `screenshots/26-profile-simulation-advanced-expanded.png` | Simulation advanced physics expanded |
| `screenshots/26-profile-about-dialog.png` | About dialog |
| `screenshots/35-home-dark.png` | Home dashboard in dark mode |
| `screenshots/36-profile-settings-dark.png` | Profile/settings in dark mode |
| `screenshots/37-workout-player-dark.png` | Workout player in dark mode |
| `screenshots/38-history-dark.png` | History in dark mode |

## Not captured

- `20-profile-api-key-visible.png` — explicitly skipped.
- Workout complete summary — the real player reached the finish-ready state, but this run had no usable telemetry samples, so the app did not emit the completion event.
- Session detail — requires a completed history record.
- Route detail and free ride — no safely selectable route fixture was available during this run.
- Empty-library, permission-error, Bluetooth-disabled, location-disabled, and sync-error variants — these require controlled app/device data or system-state mutation and were not inferred from another screen.

The older files in this directory with shorter names (`01-home.png`, `02-workouts.png`, and similar) are retained as existing workspace artifacts; the numbered descriptive files above are the approved documentation set.
