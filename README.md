# Trainer Loop

Trainer Loop is a browser-first, Bluetooth-connected interval training controller
that closes the loop between a planned workout and real-time physiology. It
drives smart trainers (ERG or resistance targets), streams live telemetry, and
adds a "live coach" layer that suggests adaptations based on compliance and
strain.

## Documentation

- Implementation plan: `docs/implementation-plan.md` - Phases, MVP scope, and
  future extensions (including intervals.icu).
- Data model: `docs/data-model.md` - Core entities for workouts, sessions,
  telemetry, coach profiles, and notes.
- Coach rules: `docs/coach-rules.md` - Rule definitions and trigger logic for
  actionable suggestions.
- UX flow: `docs/ux-flow.md` - User flows for running workouts and accepting or
  rejecting coach feedback.
