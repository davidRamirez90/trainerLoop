# Implementation Plan

## Goals

- Provide a reliable browser-first trainer controller with BLE FTMS support.
- Run structured workouts with ERG targets, ramps, and safety bounds.
- Surface real-time telemetry with smoothing and dropout handling.
- Deliver a "live coach" that suggests adjustments and logs feedback.

## MVP Scope

- Devices: BLE FTMS smart trainer + HR sensor (optional cadence if available).
- Workout engine: steps + ramps, ERG targets, pause/stop, safety bounds.
- Telemetry: power, cadence, HR, virtual speed, smoothing, dropout detection.
- Live coach v1: rules-only suggestions with accept/reject controls.
- Coach profiles: file-based JSON, selected per session.
- Session notes: store coach feedback and user responses locally.
- Export: CSV (session summary + coach notes).

## Progress

- Completed: Vite + React + TypeScript scaffold at repo root.
- Completed: First workout screen layout aligned to UI draft.
- Completed: uPlot timeline chart with target ranges and live power trace.
- Completed: Simulated telemetry stream (power, cadence, HR).
- Completed: Mock workout plan data + interval progress display.

## Phased Delivery

### Phase 0 - Foundation

- App shell, state model, routing.
- BLE connection flow, capability discovery, reconnect strategy.
- FTMS read/write scaffolding, base error handling.

### Phase 1 - Workout Player

- Interval timeline model (step + ramp).
- ERG target control loop with bounds and debounce.
- Workout UI: timeline, current target, live charts.

### Phase 2 - Telemetry Pipeline

- Smoothing and lag compensation.
- Dropout detection and gap annotation.
- Session recorder (raw + smoothed streams).

### Phase 2.5 - Coach Profiles and Feedback Stream

- JSON profile loader and selector.
- Coach feedback pipeline (encouragement + suggestions).
- Accept/reject workflow; apply changes safely.
- Coach events saved to session notes.

### Phase 3 - Live Coach Rules v1

- Adherence, HR drift, cadence stability evaluation.
- Suggestions: increase/decrease intensity, extend recovery, skip remaining
  "on" intervals.
- End-of-session completion notes and stars.

### Phase 4 - Export and Polish

- CSV export for session summary + coach notes.
- UX polish: reconnect states, recovery from device drops.

## Future Extensions

- Intervals.icu sync: fetch planned workouts and map to internal model.
- Workout file import: ERG/MRC/ZWO (start here before intervals.icu).
- FIT/TCX export and integrations (Strava, TrainingPeaks).
- AI/ML layer: readiness, fatigue inference, personalized adjustments.
