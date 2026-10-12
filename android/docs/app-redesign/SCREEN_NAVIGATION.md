# Current TrainerLoop navigation

Updated October 2026. Persistent tabs: Today (Home route), Workouts, History. Profile is a detail destination opened from the Today avatar.

[Interactive HTML screen and flow report](../../../docs/reports/app-flow-report.html) maps the nine navigation destinations and supporting views,
with entry actions, return paths, player states, and completion decisions.
The [Focus migration gallery](../../../docs/design/focus-migration/index.html) shows the migrated screens.
Current renders and the emulator-free capture workflow are documented in
[the Compose screenshot guide](../../COMPOSE_SCREENSHOTS.md).

```text
Home
├── Choose workout / Choose a different workout -> Workouts
├── Last ride -> Session Detail / View history -> History
├── Manage connections -> Devices dialog
├── Today's Plan / Open workout -> Workout Player -> Workout Complete
└── Profile avatar -> Profile -> Back to Today

Workouts
├── Workout card -> Workout Preview -> Workout Player -> Workout Complete
├── FTP Ramp Test -> Workout Player -> Workout Complete
├── Sync today's workouts from intervals.icu
└── Import workout -> Android file picker

History -> Session Detail
Profile -> Power Zones / Heart Rate Zones / About dialogs
Devices -> Scan / Connect / Disconnect trainer and HR sensor
```

Player controls: start/pause/resume/stop, skip, ERG toggle, intensity adjustment
and reset, +30s recovery. Completion saves/discards/shares FIT and can accept FTP.
There are no builder, standalone free-ride, GPX-route, or coach destinations.
The workout player retains optional generated virtual terrain overlays and interval
announcements. Legacy saved sessions remain accessible through History.
