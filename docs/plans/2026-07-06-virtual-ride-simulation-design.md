# Virtual Ride Simulation — Design

**Date:** 2026-07-06
**Status:** Approved design, pending implementation plan
**Scope:** Phase 1 of the ride-simulation stretch goal. Phase 2 (GPX upload, FTMS SIM free-ride) is out of scope and only sketched at the end.

## Goal

Make ERG interval workouts more interactive by simulating a virtual route under the workout: a terrain profile generated from the workout structure is overlaid on the workout chart, and a physics model computes realistic speed, distance, and ascent from the rider's actual power. ERG mode keeps full control of resistance — the simulation never touches trainer control.

This also fixes a real defect: in ERG mode the FTMS `speedKph` reported by the trainer (`IndoorBikeData`) is meaningless flywheel speed. Virtual speed replaces it everywhere speed is shown or recorded.

## Decisions made

- **Both modes, phased.** Phase 1 = virtual route + ERG (this doc). Phase 2 = real FTMS SIM mode with GPX (later, separate design).
- **Terrain generated from workout structure**, not random and not GPX: hard intervals become climbs, recovery becomes descents.
- **Time-domain terrain.** Grade is a function of workout time, so the elevation overlay aligns 1:1 with the workout chart's time axis. No distance-domain route, no warping.
- **Recorded into FIT.** Virtual speed/distance/altitude go into the session and the FIT upload, so intervals.icu shows realistic speed/distance/climbing for trainer rides. No fake GPS coordinates.
- **Advanced settings** expose the physics constants (CdA, Crr, bike weight) so speed calculation can be tuned without code changes.

## Components

### 1. Terrain generation — `domain/sim/RouteGenerator.kt`

Pure function from a `Workout` to a grade-vs-time track.

- Each segment's grade derives from its target power as a fraction of FTP:
  recovery ≈ −2%, endurance ≈ 0–1%, tempo ≈ 2–3%, threshold ≈ 4–5%, VO2+ ≈ 6–8%.
- `WorkoutSegment.Ramp` segments produce linearly ramping grades.
- Light noise, seeded by workout id — the same workout always generates the same "route" (deterministic; important for tests and for the route feeling like a place).
- Grade transitions smoothed over ~10 s at segment boundaries (no step changes).
- Output: `List<GradePoint(timeSec, gradePercent)>` at 1 Hz (or per-second lookup), plus a precomputed **expected elevation profile** integrated at target power, used only for the chart overlay. The actually-recorded altitude integrates real power and may differ slightly; that is fine.

### 2. Physics — `domain/sim/VirtualSpeed.kt`

Pure function: `(powerWatts, gradePercent, params) -> speedMps`.

Solves for v in:

```
P = Crr·m·g·v + m·g·sin(atan(grade))·v + ½·ρ·CdA·v³
```

by bisection (the function is monotonic in v). Defaults:

| Param | Default | Source |
|---|---|---|
| rider mass | `ProfileRepository.weightKg` | existing setting |
| bike mass | 8 kg | advanced setting |
| Crr | 0.005 | advanced setting |
| CdA | 0.32 m² | advanced setting |
| ρ (air density) | 1.226 kg/m³ | constant |

Descent behavior: at low/zero power on a negative grade, speed is the coasting terminal velocity for that grade (solve with P = 0; gravity term drives v). No freewheeling model beyond that.

Per-tick integration (in `WorkoutViewModel`, where `TelemetrySample`s are produced):

- `distance += v · dt`
- `altitude += v · dt · grade` (grade as fraction)

### 3. Recording + FIT

- `TelemetrySample` gains nullable `virtualSpeedKph`, `virtualAltitudeM`, `gradePercent` with `null` defaults. Samples are persisted as JSON (`SessionEntity.samplesJson`), so old sessions deserialize with nulls — no Room migration needed.
- `WorkoutSummaryMath` adds total distance (km) and total ascent (m).
- `FitEncoder` record messages add standard FIT record fields:
  - speed — field 6, uint16, m/s × 1000
  - distance — field 5, uint32, cm
  - altitude — field 2, uint16, (m + 500) × 5
- `IcuActivityUploader` rebuilds FIT from samples, so uploads pick this up with no further changes.
- Round-trip verified via existing `FitDecoder`.

### 4. UI

- **Workout screen:** soft elevation-profile area rendered behind/below the existing power chart, same time axis, 1:1 aligned. Progress marker rides the profile. Stats pager gains current grade (%) and virtual speed.
- **Session detail / history:** distance and ascent in the summary.
- **Settings:**
  - Toggle: "Virtual ride (simulated route + speed)" — default **on**.
  - **Advanced (collapsed section):** bike weight (kg), rolling resistance Crr, drag area CdA — numeric fields prefilled with defaults above, with a "reset to defaults" action. Persisted in `ProfileRepository` prefs. Inputs clamped to sane ranges (bike 5–15 kg, Crr 0.002–0.010, CdA 0.15–0.60) — they feed a physics solver, so garbage in must not hang or explode the bisection.

When the toggle is off: no overlay, no virtual fields in samples, FIT omits speed/distance/altitude (current behavior).

## Error handling

- Physics solver bisection has fixed iteration cap and clamped inputs; can't hang or NaN.
- Grade track lookup past workout end (overtime riding) holds the last grade.
- Missing power (dropout samples) → hold previous speed for the tick, same as other metrics during dropout.

## Testing

- `VirtualSpeed`: known cases (e.g. 250 W, 0% grade, 83 kg → ~33–36 km/h), monotonic in power, monotonic in grade, coasting on descent > 0, clamped params respected.
- `RouteGenerator`: deterministic per workout id, grades within bounds, smooth transitions (bounded delta per second), ramps ramp.
- FIT: encode samples with virtual fields → decode with `FitDecoder` → values survive round-trip.
- Summary math: distance/ascent accumulate correctly over a synthetic ride.

## Phase 2 sketch (not designed)

- GPX upload → distance-domain course → FTMS SIM mode (opcode 0x11, Set Indoor Bike Simulation Parameters) drives resistance; a free-ride workout type, separate from ERG intervals.
- Possible "ride a GPX in ERG" hybrid: warp GPX elevation onto workout time as the terrain source for Phase 1's generator.
