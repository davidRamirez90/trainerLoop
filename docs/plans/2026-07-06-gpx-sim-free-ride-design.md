# GPX Free-Ride with Virtual Gears — Design (Phase 2)

**Date:** 2026-07-06
**Status:** Implemented — see `2026-07-07-gpx-free-ride-plan.md`.
**Scope:** A new free-ride workout type: upload a GPX route, ride it with virtual gears. Resistance is produced by driving standard FTMS ERG from a physics model — no FTMS SIM opcode, no proprietary Zwift protocol. Not an enhancement to ERG interval workouts.

## Goal

Ride a real outdoor route on the trainer: grade-realistic resistance, shiftable virtual gears, position advancing along the real track, and a FIT upload with GPS coordinates so intervals.icu/Strava show the ride on a map.

**Why virtual gears are load-bearing:** the target trainer (Zwift Hub One) is single-cog. In plain FTMS SIM mode there is no way to shift, so steep virtual grades become unrideable. Zwift solves this with proprietary virtual shifting; we solve it in software with standard FTMS ERG, which works on any ERG-capable trainer.

## Decisions made

- **ERG-backed virtual gears, no SIM opcode.** Virtual speed comes from cadence × gear ratio; the physics computes the power that speed requires on the current grade; that power is sent as the ERG target. Shifting changes the ratio → required power changes → resistance changes. Known trade-off: ~1 s resistance response lag inherent to ERG.
- **Free-ride only.** No interval targets, no coach prompts. Distinct session type.
- **GPS coordinates recorded** from the GPX at the simulated position.
- **Controls: on-screen shift buttons + phone volume keys.** Zwift Click pairing (user owns a Click v1) is deferred — it needs the reverse-engineered Zwift Play BLE protocol (custom GATT service, protobuf button messages, "RideOn" handshake); firmware-fragile, so it's a follow-up, not part of this phase.
- **Trainer difficulty multiplier** (0–100 %, default 100 %, Advanced settings) scales the *grade used in the target-power calculation* only. Position, recorded speed, and recorded altitude always use the true grade.

## The virtual drivetrain

- **Gear table:** 14 gears, ratios geometrically spaced 1.0 → 4.6 (≈ 34×34 to 50×11 on a real bike), wheel circumference 2.096 m. Start in gear 7. Constants in code — not user-configurable.
- **Pedaling speed:** `v_gear = cadenceRpm / 60 × ratio × circumference`.
- **Freewheel:** real bikes coast — the wheel can spin faster than the pedals drive it. So `v = max(v_gear, v_coast)`, where `v_coast` is the zero-power terminal velocity on the current grade (Phase 1's `VirtualSpeed` with P = 0; zero on flats and climbs).
- **Target power:** `P = (Crr·m·g + m·g·sin(atan(g_eff))) · v + ½·ρ·CdA·v³` with `g_eff = grade × difficulty`, params from Phase 1's advanced settings. Sent via the existing `FtmsControlManager.setTargetPower` path.
- **Stability:** cadence is EMA-smoothed (~3 s) before computing `v_gear`, and targets are re-sent only on ≥ 2 W change or every 2 s — prevents cadence→resistance feedback oscillation and control-point write spam.
- **Coasting/floor:** when `v_gear` ≈ 0 and not descending, target power floors at 0 W (trainer minimum); position simply stops advancing on flats/climbs when you stop pedaling.

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
  2. Compute `v` from the virtual drivetrain (smoothed cadence, current gear, freewheel floor).
  3. `distance += v · dt`; interpolate lat/lon/elevation at the new distance.
  4. Compute target power from `v` and effective grade; send ERG target if changed ≥ 2 W or 2 s elapsed.
- **Shifting:** on-screen up/down buttons (large tap targets near screen edges) + hardware volume keys mapped to shift up/down while the ride screen is active. Current gear number shown prominently.
- **Route end:** hold 0 % grade, notify ("route complete"), keep recording until the user stops — same stop/save flow as workouts. Stopping early saves the partial ride.
- **Screen:** route elevation profile on a *distance* axis with a position marker, plus tiles for gear, speed, distance remaining, current grade, power, HR, elapsed time. Reuses the chart/tile components; no map view in this phase.
- **Free-ride start flow:** Routes list (imported GPX files) → route detail (profile, distance, ascent) → start ride. Entry point on the home screen next to workout selection.

### 3. Recording + FIT

- `TelemetrySample` (Phase 1 fields reused: `virtualSpeedKph`, `virtualAltitudeM`, `gradePercent`) gains nullable `positionLat`, `positionLon`. JSON storage — no migration for samples.
- `FitEncoder` record messages add position_lat (field 0) and position_long (field 1), sint32 semicircles (`deg × 2³¹ / 180`), written only when present.
- Altitude recorded from the smoothed GPX elevation at position (not integrated) — matches the map.
- Session summary: distance, ascent, route name. `IcuActivityUploader` unchanged (rebuilds FIT from samples).

## Error handling

- GPX with < 2 usable trackpoints, no elevation, or unparseable XML → import rejected with message.
- BLE control write failure mid-ride → same reconnect/error path as existing ERG target writes; ride keeps recording.
- Cadence dropout → EMA decays toward 0 naturally; freewheel floor keeps descents sane.

## Testing

- `GpxParser`: a small hand-written fixture GPX in test resources (the repo's `rides/` dir has only FIT files) → point count, total distance/ascent within tolerance, grades bounded, smoothing kills single-point elevation spikes.
- Virtual drivetrain: gear table strictly increasing; `v_gear` vectors (90 rpm in gear 7 → plausible km/h); freewheel floor beats `v_gear` on steep descents at low cadence; target-power vectors for known (v, grade) pairs; difficulty multiplier scales power but not recorded speed/altitude.
- Position interpolation: known grid → exact lat/lon/ele at mid-grid distances.
- FIT: semicircle conversion round-trip through `FitDecoder`.
- Tick loop: synthetic route + constant cadence → monotonic distance, route-complete fires at route end.

## Deferred

- **Zwift Click v1 pairing** as hardware shifter — reverse-engineered Zwift Play BLE protocol (custom GATT service, protobuf messages, "RideOn" handshake). Revisit once the free-ride loop is proven; volume-key shifting covers the interim.
- Hybrid mode (GPX elevation as terrain for ERG workouts) — decided against for Phase 2.
- Map rendering in-app, turn-by-turn visuals, route editing, wind simulation (wind fixed at 0), configurable gear tables.
