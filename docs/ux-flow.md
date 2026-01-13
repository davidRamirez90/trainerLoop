# UX Flow

## Pre-Workout

1. Choose workout source (manual or imported file).
2. Review workout preview (segments, duration, targets).
3. Select coach profile.
4. Connect devices (trainer + HR).
5. Start session.

## In-Workout Screen

- Primary view: current target, power, cadence, HR, and interval timer.
- Secondary view: timeline strip and upcoming segments.
- Coach feed: scrollable list of coach cards with timestamps.

## Coach Feedback Cards

- Encouragement: auto-dismiss after a short delay; still logged to notes.
- Suggestions: persistent until accepted or rejected.
- Each suggestion includes a short rationale and an action summary.

## Accept/Reject Flow

1. Suggestion card appears with "Accept" and "Reject".
2. Accept applies the change immediately (ERG target or recovery extension).
3. Reject makes no changes, logs the decision, and suppresses similar prompts
   for the configured cooldown.
4. All actions are saved to session notes.

## Session Notes

- Notes view shows chronological coach events with user responses.
- Notes are available during and after the workout.
- Export includes a summary plus the notes list.

## End of Session

1. Workout completion summary (stars + compliance score).
2. Coach highlights and any skipped intervals.
3. Offer export (CSV) and save session locally.

## Error and Safety States

- Device disconnect: pause workout, prompt reconnect.
- Loss of telemetry: show warning, continue with last known targets.
- Manual stop: confirm and save partial session with notes intact.
