# GPX SIM Free-Ride — Design (Phase 2)

**Date:** 2026-07-06
**Status:** Follow-up — do not implement until Phase 1 (`2026-07-06-virtual-ride-simulation-design.md`) has shipped. This spec depends on Phase 1's `VirtualSpeed` physics and advanced settings.
**Scope:** A new free-ride workout type: upload a GPX route, its grade drives trainer resistance via FTMS SIM mode, ride the route. Not an enhancement to ERG interval workouts — those keep Phase 1's virtual route. Hybrid "GPX terrain under an ERG workout" was considered and deferred.

## Goal

Ride a real outdoor route on the trainer: resistance follows the route's gradient (FTMS Set Indoor Bike Simulation Parameters), position advances along the real track, and the resulting FIT upload includes GPS coordinates so intervals.icu/Strava show the ride on a map — same model as Zwift/Rouvy virtual rides.

## Decisions made

- **Free-ride only.** No interval targets, no ERG, no coach prompts during a free-ride. It is a distinct session type.
- **GPS coordinates recorded.** Lat/lon from the GPX at the simulated position go into the FIT.
- **Position from physics, not the trainer.** Phase 1's `VirtualSpeed` (with the user's tuned Crr/CdA/bike-weight advanced settings) computes speed from actual power and the route grade at the current position; position integrates that speed. The trainer's reported speed is ignored, as in Phase 1. This keeps speed consistent between phases and independent of trainer wheel-speed quirks.
- **Trainer difficulty scaling.** A grade multiplier (0–100 %, default 100 %) scales the grade *sent to the trainer* only — the physics and recorded altitude always use the true grade. Same concept as Zwift's trainer difficulty; lives in the existing Advanced settings section.

## Components

### 1. GPX import — `domain/parser/GpxParser.kt` + route storage

- Parse `<trkpt lat lon><ele>` trackpoints (XmlPullParser, already on Android — no new dependency).
- Build cumulative distance via haversine; drop duplicate/zero-distance points.
- **Smooth elevation** with a distance-windowed moving average (~75 m window) before computing grade — raw GPX elevation is noisy and produces grade spikes that would slam the resistance.
- Resample to a uniform distance grid (10 m) → `RoutePoint(distanceM, lat, lon, elevationM, gradePercent)`.
- Grade clamped to ±20 % after smoothing (bad data guard).
- Storage: new Room table `RouteEntity(id, name, distanceM, ascentM, pointsJson, importedAt)` — same JSON-blob pattern as `SessionEntity.samplesJson`. Room migration 2→3.
- Import UI: system file picker (SAF, `application/gpx+xml` + `*/*` fallback); name defaults to GPX `<name>` or filename. Parse errors surface as a friendly message; nothing partial is saved.

### 2. Free-ride session — engine + screen

- New session type `FREE_RIDE` alongside the existing workout session (session entity gains a type discriminator + `routeId`; part of the same 2→3 migration).
- **Tick loop** (1 Hz, same cadence as the ERG engine):
  1. Look up grade at current route distance.
  2. `v = VirtualSpeed(actual power, grade, params)`; `distance += v · dt`.
  3. Interpolate lat/lon/elevation at the new distance.
  4. Send SIM grade to trainer when it changed ≥ 0.5 % since last send or 3 s elapsed (throttle — FTMS control writes are slow and queue).
- **Route end:** hold 0 % grade, notify ("route complete"), keep recording until the user stops — same stop/save flow as workouts. Stopping early saves the partial ride.
- **Screen:** route elevation profile on a *distance* axis with a position marker, plus tiles for speed, distance remaining, current grade, power, HR, elapsed time. Reuses the chart/tile components; no map view in this phase.
- **Free-ride start flow:** Routes list (imported GPX files) → route detail (profile, distance, ascent) → start ride. Entry point on the home screen next to workout selection.

### 3. FTMS SIM control — `FtmsControlManager`

- New command: `setSimulationParameters(windMps = 0, gradePercent, crr, cw)` — opcode `0x11`, fields per FTMS spec: wind sint16 (0.001 m/s), grade sint16 (0.01 %), Crr uint8 (0.0001), Cw uint8 (0.01 kg/m). Crr/Cw from the advanced settings (Cw = ½·ρ·CdA).
- Grade sent = true grade × difficulty multiplier.
- Reuses the existing control-point write/response machinery; response opcode `0x11` handled like `0x05` (reject → surface error).
- Trainers without SIM support (control feature bits): fall back to ERG-off resistance-free ride with a visible notice — physics/recording still work, only felt resistance is missing.

### 4. Recording + FIT

- `TelemetrySample` (Phase 1 fields reused: `virtualSpeedKph`, `virtualAltitudeM`, `gradePercent`) gains nullable `positionLat`, `positionLon`. JSON storage — no migration for samples.
- `FitEncoder` record messages add position_lat (field 0) and position_long (field 1), sint32 semicircles (`deg × 2³¹ / 180`), written only when present.
- Altitude recorded from the smoothed GPX elevation at position (not integrated) — matches the map.
- Session summary: distance, ascent, route name. `IcuActivityUploader` unchanged (rebuilds FIT from samples).

## Error handling

- GPX with < 2 usable trackpoints, no elevation, or unparseable XML → import rejected with message.
- BLE control write failure mid-ride → same reconnect/error path as ERG target writes; ride keeps recording.
- Power dropout → hold previous speed for the tick (Phase 1 behavior).

## Testing

- `GpxParser`: a small hand-written fixture GPX in test resources (the repo's `rides/` dir has only FIT files) → point count, total distance/ascent within tolerance, grades bounded, smoothing kills single-point elevation spikes.
- Position interpolation: known grid → exact lat/lon/ele at mid-grid distances.
- SIM command encoding: byte-exact vectors for known grade/Crr/Cw values, including negative grades.
- FIT: semicircle conversion round-trip through `FitDecoder`.
- Tick loop: synthetic route + constant power → monotonic distance, route-complete fires at route end.

## Deferred

- Hybrid mode (GPX elevation as terrain for ERG workouts) — decided against for Phase 2.
- Map rendering in-app, turn-by-turn visuals, route editing, wind simulation (wind fixed at 0).
