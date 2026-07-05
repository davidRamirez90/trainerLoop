# TrainerLoop — Roadmap

Personal Android app (Pixel 2 XL) that controls a bike trainer over BLE in ERG
mode and logs structured workouts. Single user, not published.

Status: ✅ done · 🔨 partial · ⬜ planned · ⏸ intentionally paused

---

## A. intervals.icu integration

- ✅ **Today's planned workout → Home "Quick Start" card** (fetch event, tap →
  download `.zwo` → parse → straight into the player).
- ✅ **Auto-sync FTP / weight** from the athlete endpoint on Home load.
- ✅ **Cache today's planned workout name** (prefs) so the card shows instantly
  and survives an offline launch.
- ✅ **Auto-upload FIT after a ride** — already wired in the Workout Complete flow.
- ⬜ **This week's calendar strip** (next 7 days, not just today). Client already
  takes an `oldest`/`newest` range; needs a small horizontal UI.
- ⬜ **Write back RPE / notes** to the uploaded activity. *Deferred:* needs a new
  API surface — `uploadActivity` currently returns `Boolean`, not the new
  activity id, so there's nothing to `PUT` notes onto. Add an id-returning
  upload + an update call first.

## B. During-the-ride experience

- ✅ **ERG on/off toggle** mid-ride.
- ✅ **Intensity bias** ±1% / ±5% (clamped ±20%) rescaling targets live.
- ✅ **Skip interval.**
- ✅ **Live target vs. actual** (target band + zone-colored power + chart).
- ✅ **Audio cue on interval change** (`ToneGenerator` beep).
- ✅ **Voice call-outs** — `TextToSpeech` announces the next interval + target watts.
- ✅ **Time-in-zone bar** — seconds on-target within the current interval.
- ✅ **Auto-reconnect BLE** — already handled in `FtmsControlManager.connect()`
  via `connection.addReconnectHandler` (re-arms control + re-requests on reconnect).
- ⬜ **Extend current recovery (+30s).** *Deferred:* `WorkoutClock` fixes segment
  boundaries at construction, so this needs a mutable/rebuildable clock — bigger
  than a button. The coach engine already models `recoveryExtendStepSec`; wire
  that path when the clock supports it.
- ⬜ **Resume an interrupted session** after a long BLE drop (clock keeps running
  today; no explicit "reconnect & continue" UX). Low value; the trainer control
  re-arms automatically.
- ⬜ **Lap marker** button. *Deferred:* needs lap-message support in `FitEncoder`.

## C. Workout creation & library

- ✅ **Search** (name/description) and **category filter** — already present.
- ✅ **Favorites** — star a workout; favorites sort to the top (prefs-backed).
- ✅ **Duplicate** any workout (built-in or imported) into the editable store.
- 🔨 **Workout Builder** — functional for stepped intervals (duration + watt
  range). Ramps / free-ride / editing an existing workout still only come via
  file import. Extend the builder UI when needed.
- ⬜ **Tags** on workouts (beyond the auto category). Low priority.

## D. Insights (post-ride, all local)

- ⬜ **Trend charts**: FTP over time, weekly TSS / load, target compliance.
  *Deferred:* a new screen + cross-session aggregation over
  `SessionRepository.summaries()`. Straightforward but sizeable; next up if you
  want offline trends.
- ⏸ **Personal records** — intervals.icu already tracks these; only build locally
  if you want them without a network.

## E. Practical / hardware polish

- ✅ **Keep-awake during a ride** — `keepScreenOn` toggled with the running clock.
- ⬜ **Trainer calibration / spindown.** *Deferred:* requires the FTMS Spin-Down
  Control opcode (0x13) + reading the spin-down status characteristic, and
  genuinely needs the physical trainer to validate. Do this with the bike in
  front of you.
- ⬜ **Persist the real session id into the player route** (currently hardcoded
  `sessionId = 1L`). Harmless today; blocks resume / history-linking later.

---

### Explicitly out of scope

Accounts, onboarding, cloud sync, multi-user, monetization, Play Store polish.
