# TrainerLoop scope and roadmap

Updated after the October 2026 training-focused cleanup. The Android app is the
only supported application. See [README.md](README.md) for current functionality.

## Delivered and retained

- Structured workouts and FTP ramp test.
- FTMS trainer and HR sensor connection, telemetry and ERG control.
- Live ride metrics/charts, interval audio/haptic cues and background continuity.
- Workout imports, search/filter/favorites/duplicate/delete and FTP-scaled built-ins.
- Manual intensity adjustment, ERG toggle, skip, and +30s recovery.
- Optional generated virtual route overlay during structured workouts.
- Explicit Save/Discard, FIT recording/sharing, local history and weekly ride time.
- Today's intervals.icu plan/sync, FTP/weight sync, FIT uploads and FTP write-back.

## Removed

Workout builder, standalone free ride, GPX route library/riding, live coaching,
coach personalities/adaptive suggestions/summaries, and route gearing/shifters.
Previously saved rides and stored workouts remain readable.

## Deferred, not currently supported

- Next-seven-days plan calendar.
- RPE/notes write-back and lap markers.
- Process-interruption session recovery.
- Trainer calibration/spindown.
- Per-session FTP snapshots and local FTP/TSS/compliance trends.

Accounts, onboarding, multi-user, monetization and Play Store distribution remain
out of scope. The Android Gradle plugin is aligned with SDK 35 (8.6.1); broad dependency
upgrades remain separate from this functional cleanup.
