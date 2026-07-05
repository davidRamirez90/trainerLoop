# FTP Ramp Test — Design

Date: 2026-07-05
Status: Approved pending user review

## Summary

Add a built-in "FTP Ramp Test" workout mode to the Android app. It is not synced
from intervals.icu and not user-created: tapping it generates a ramp workout on
the fly, runs it in the existing workout player, auto-detects exhaustion,
computes a new FTP (75% of best 1-minute power), and shows a result card on the
completion screen where the user can accept or discard the new FTP. The ride
itself uploads to intervals.icu exactly like any other session. Accepting the
FTP updates the local profile and — if intervals.icu is configured — offers to
push the new FTP there too.

## Protocol

- **Warmup:** 5 minutes, ramp from 50W to 100W (`WorkoutSegment.Ramp`, phase WARMUP).
- **Steps:** 1-minute `Step` segments starting at **100W**, each **+20W**
  (100, 120, 140, …), phase WORK.
- **Ceiling:** steps are pre-generated up to `max(2.5 × currentFtp, currentFtp + 200)` watts —
  a level nobody reaches; the test always ends early via failure detection or
  the stop button. This keeps the workout a plain fixed segment list.
- **No cooldown segment:** when the test ends the player finishes as it does
  today for a stopped workout; the rider can spin down freely afterwards.
- **New FTP = round(0.75 × best rolling 60-second average power)** over all
  recorded samples.
- Result is only meaningful if at least one full step past the warmup was
  ridden; if the session has less than 60s of samples after warmup start, the
  result card shows "Test too short — no FTP calculated" instead of a value.

## Architecture (approach A — pre-generated workout, existing player)

The ramp test is an ordinary `Workout` with a well-known id
(`ftp-ramp-test`). Everything downstream (ERG control, telemetry, FIT
encoding, session save, intervals.icu upload) is reused unchanged.

### New: `domain/RampTest.kt`

Single object with:

- `WORKOUT_ID = "ftp-ramp-test"`
- `generate(currentFtp: Int): Workout` — builds warmup + steps per the protocol.
- `computeFtp(samples: List<TelemetrySample>): Int?` — best rolling 60s average
  power × 0.75, rounded; `null` if fewer than 60 seconds of samples exist.
- `isRampTest(workoutId: String): Boolean`

Unit tests cover segment generation (start power, step size, ceiling) and FTP
computation (known sample series, short-session null case).

### Entry point: Workouts tab

A pinned "FTP Ramp Test" card at the top of `WorkoutLibraryScreen`, visually
distinct from synced/imported workouts. Tapping it generates the workout from
the current profile FTP and starts the player the same way library workouts do
today. It is not stored in the library/imported store and never syncs.

### Player changes: `WorkoutViewModel`

Only active when `RampTest.isRampTest(workoutId)`:

- **Failure auto-detect:** during WORK steps, if measured power stays below
  50% of the current step target for 5 consecutive seconds, trigger the same
  finish path as `stop()`. Warmup is exempt.
- The normal stop button remains the manual "End test".
- Everything else (pause/resume, intensity trim, coach) behaves as today;
  coach suggestions are suppressed during a ramp test (a test is not a workout
  to be coached through).

### Result: `WorkoutCompleteScreen` / `WorkoutCompleteViewModel`

The existing completion screen gains a ramp-test result card, shown only when
the finished session's workout id is the ramp test:

- **Display:** new FTP (large), previous FTP, absolute and % delta
  (e.g. "265W — up 15W (+6%) from 250W").
- **Accept:** writes the new FTP to the local profile
  (`ProfileRepository`). If intervals.icu credentials are configured, a
  follow-up prompt appears: "Set FTP on intervals.icu now?" → Yes (calls the
  new client method) / Later (do nothing; user sets it manually on icu).
- **Discard FTP:** dismisses the card; the value is not stored anywhere. The
  session itself is still saved locally and uploaded to intervals.icu as a
  normal ride (independent of the existing discard-session button, which keeps
  its current behavior).
- Push result (success/failure) is surfaced in the existing status/error
  fields of the screen.

The FTP decision is one-shot per completion screen; there is no later
"re-apply" flow. (If the user discards by mistake, they re-run the test or set
FTP manually — not worth extra state.)

### intervals.icu: `IntervalsIcuClient`

Add `updateFtp(athleteId: String, ftp: Int): Boolean` — a PUT to the athlete
endpoint with the new FTP value. The exact endpoint/payload
(`PUT /api/v1/athlete/{id}` vs sport-settings) is verified against the
intervals.icu API during implementation; the client already authenticates the
same way for reads.

Ride upload is untouched: `uploadActivity` runs for ramp tests exactly as for
any workout, regardless of the FTP accept/discard decision.

## Data flow

1. Workouts tab → tap ramp test card → `RampTest.generate(profile.ftp)` →
   navigate to player with the generated workout.
2. Player runs segments in ERG as usual; failure detection or stop button ends it.
3. Existing finish flow → `WorkoutComplete` screen: session saved, FIT built,
   uploaded to intervals.icu (if configured).
4. Completion VM sees ramp-test id → `RampTest.computeFtp(samples)` → result
   card with accept/discard.
5. Accept → local profile FTP updated → optional icu push prompt → optional
   `updateFtp` call.
6. Discard → nothing stored; card dismissed.

## Error handling

- **Too-short test:** result card shows "no FTP calculated"; no accept option.
- **icu push fails:** local FTP stays updated; card shows push failure and the
  user can set it manually on intervals.icu (no retry loop).
- **icu not configured:** accept updates local FTP only; no push prompt.
- **Upload of the ride:** existing behavior/error surface, unchanged.

## Out of scope

- Ramp Test Lite / configurable step size or start power.
- Cooldown segments after failure.
- Storing FTP history / trends.
- Re-applying a discarded result later.
- Syncing the ramp test workout definition to intervals.icu.

## Testing

- `RampTestTest`: generation constants (warmup, 100W start, +20W steps,
  ceiling) and `computeFtp` (known series → expected FTP, <60s → null).
- `WorkoutViewModelTest`: failure auto-detect triggers finish after 5s below
  50% of step target; warmup exempt; non-ramp workouts unaffected.
- Completion-screen logic (accept updates profile, discard doesn't) covered at
  the ViewModel level.
