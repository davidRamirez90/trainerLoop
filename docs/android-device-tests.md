# Pixel 2 XL (Android 11 / API 30) Device Test Checklist

Tested on: _________
Date: _________

## BLE Permissions (Task 6.1)

| # | Test | Expected | Result |
|---|---|---|---|
| 1 | Launch app with location denied | Shows permission dialog, app continues gracefully | ☐ |
| 2 | Grant `ACCESS_FINE_LOCATION` | Scan button becomes available | ☐ |
| 3 | Location services off + permissions granted | App surfaces "enable location" hint | ☐ |

## BLE Scanning (Task 6.2)

| # | Test | Expected | Result |
|---|---|---|---|
| 4 | Tap Scan | Trainer and HR sensors appear within 10s | ☐ |
| 5 | Trainer name and RSSI visible | Card shows name, address, RSSI | ☐ |
| 6 | HR sensor visible | Separate section with HR sensor listed | ☐ |

## GATT Connection (Task 6.3)

| # | Test | Expected | Result |
|---|---|---|---|
| 7 | Connect to trainer | Status changes to connected, card highlights | ☐ |
| 8 | Connect to HR sensor | Status changes to connected | ☐ |
| 9 | Disconnect trainer | Status returns to idle | ☐ |

## FTMS Telemetry (Task 6.4)

| # | Test | Expected | Result |
|---|---|---|---|
| 10 | Trainer connected → Indoor Bike Data | Power and cadence update in UI | ☐ |
| 11 | Trainer stops transmitting | Dropout flag set, last values held | ☐ |

## Heart Rate (Task 6.5)

| # | Test | Expected | Result |
|---|---|---|---|
| 12 | HR sensor connected | BPM values update in UI | ☐ |
| 13 | HR sensor out of range | Value freezes at last known | ☐ |

## ERG Control (Task 6.6)

| # | Test | Expected | Result |
|---|---|---|---|
| 14 | Start workout → ERG enabled | Trainer resistance matches target power | ☐ |
| 15 | Ramp segment | Target power updates smoothly | ☐ |
| 16 | Pause | Trainer enters pause/ready state | ☐ |
| 17 | Resume | Target power restored | ☐ |
| 18 | Intensity +5% | Target adjusts upward accordingly | ☐ |
| 19 | Intensity -5% | Target adjusts downward accordingly | ☐ |

## Auto-Reconnect (Task 6.7)

| # | Test | Expected | Result |
|---|---|---|---|
| 20 | Power-cycle trainer mid-workout | Auto-reconnects within 30s, ERG resumes | ☐ |
| 21 | Explicit disconnect | Does NOT auto-reconnect | ☐ |

## Workout Playback (Task 8.3)

| # | Test | Expected | Result |
|---|---|---|---|
| 22 | Load "Sweet Spot" workout | Segments visible in timeline | ☐ |
| 23 | Tap Start | Timer begins, target power updates | ☐ |
| 24 | Pause / Resume | Timer stops/starts | ☐ |
| 25 | Seek to 50% | Jumps to midpoint of workout | ☐ |
| 26 | Complete all segments | Completion flag set | ☐ |

## Coach Engine (Task 8.4)

| # | Test | Expected | Result |
|---|---|---|---|
| 27 | Drop power below 85% of target | Suggestion appears: "Decrease intensity" | ☐ |
| 28 | Accept suggestion | Intensity offset applied | ☐ |
| 29 | Reject suggestion | Dismissed, coach records rejection | ☐ |

## Session Summary & FIT (Task 8.5)

| # | Test | Expected | Result |
|---|---|---|---|
| 30 | Stop after partial workout | Summary shows duration, avg/max power, HR | ☐ |
| 31 | Share FIT | Shares to file manager / Strava | ☐ |
| 32 | Open FIT file on another device | Valid FIT file opens | ☐ |

## Foreground Service (Task 11.1)

| # | Test | Expected | Result |
|---|---|---|---|
| 33 | Start workout | Notification appears with power/time | ☐ |
| 34 | Lock screen | Timer continues, BLE stays connected (5+ min) | ☐ |
| 35 | Notification Stop button | Workout stops, notification dismissed | ☐ |

## General Reliability

| # | Test | Expected | Result |
|---|---|---|---|
| 36 | 30 min continuous workout | No ANR, no crash, no unexpected disconnect | ☐ |
| 37 | App backgrounded and restored | State preserved, no data loss | ☐ |
| 38 | Rapid start/stop/restart (3x) | No crashes, session IDs increment | ☐ |
| 39 | Import a .zwo file | Parses correctly, segments display | ☐ |
| 40 | Build release APK | APK ≤ 10 MB, installs and runs | ☐ |

## Full Workout Flow Verification (Pixel 2 XL)

1. Home dashboard shows user header and connected-device cards.
2. Tap a device card → opens Devices screen.
3. Scan, connect trainer and HR → status shows Connected with battery/live HR.
4. Return Home; tap Workout Library.
5. Library shows filters, search, and mini-chart cards.
6. Select AE-2 Endurance → Workout Detail shows full chart and intervals.
7. Tap Start Workout → Workout Player opens.
8. Big metrics (Power, HR, Cadence, Time to Interval) update every second.
9. Live chart shows target band and actual power line.
10. Intensity buttons adjust ERG target; Pause/Skip work.
11. Stop or complete → Workout Complete screen with TSS/IF/NP and chart tabs.
12. Tap Share FIT → chooser opens; file opens in Garmin Connect / Strava.
13. Tap Save → session persisted to Room; return Home shows it in Recent Workouts.
