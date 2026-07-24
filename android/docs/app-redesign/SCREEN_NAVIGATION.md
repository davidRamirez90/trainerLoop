# Trainer Loop screen navigation

## Primary navigation

The persistent bottom navigation contains four destinations:

```text
Home <-> Workouts <-> History <-> Profile
```

Each tab preserves its own navigation state. Home is the root destination.

## Home relationships

```text
Home
├── Manage connections -> Devices sheet
├── Start Free Ride -> Workout Player -> Workout Complete
├── Today's Plan -> Workout Detail -> Workout Player -> Workout Complete
├── Workout Builder -> Workout Builder
└── GPX Routes -> Routes Library
```

The Home connection card reflects trainer and heart-rate device state. The same state is managed from the Devices sheet and is consumed by the workout player.

## Workout relationships

```text
Workouts Library
├── Workout card -> Workout Detail
│   └── Start Workout -> Workout Player
├── FTP Ramp Test -> Workout Player
├── Sync -> intervals.icu import/sync state
├── Import workout file -> Android file picker/import flow
└── Workout Builder -> save -> Workouts Library
```

The Workout Detail screen is a read-only preview of duration, intensity, TSS, description, and intervals. The player owns live telemetry, coaching, controls, and completion routing.

## Route relationships

```text
Home -> GPX Routes -> Route Detail -> Free Ride -> Workout Complete
```

Route Detail supplies the elevation profile and route metadata. Free Ride uses the selected route and the athlete/simulation settings from Profile.

## History relationships

```text
History -> Session Detail
```

Workout Player and Free Ride persist completed session data. History reads those records and opens Session Detail for a selected session.

## Profile relationships

```text
Profile
├── Athlete metrics -> FTP / weight used by workout calculations
├── Power Zones -> Power Zones dialog
├── Heart Rate -> Heart Rate Zones dialog
├── Coaching -> Coach Profile dialog and coaching behavior in Player
├── Simulation -> advanced physics controls used by virtual rides
├── Connections -> intervals.icu credentials used by sync/import
└── App -> About dialog
```

The API key remains masked in the captured documentation set. The visible-key variant was intentionally excluded.

## Device relationships

```text
Devices
├── Scan -> discovered Bluetooth devices
├── Connect -> connected trainer / heart-rate state
└── Device details -> capabilities and connection controls
```

The connection state appears on Home and is consumed by the Workout Player for trainer control and metrics availability.
