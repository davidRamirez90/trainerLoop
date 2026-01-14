# Workout View Polish Spec (Fixed Layout, Phase Emphasis)

## Goals
- Keep the workout view stable while making the most important metrics pop per phase.
- Emphasize time remaining and power compliance during work intervals.
- Surface recovery context (next target + interval averages) without reflow.
- Add a cadence gauge beside the main chart that always shows 3s cadence.

## Non-Goals
- No new navigation pages or mode switches.
- No redesign of the coach feed or device connection cards.
- No changes to workout logic or segment timing.

## Fixed Layout (No Reflow)
- Header: workout name, subtitle, live state.
- Chart row: timeline chart with a slim cadence gauge docked on the right edge.
- Primary metrics row (fixed slots):
  - Interval Time
  - Power vs Target
  - Heart Rate
  - Cadence Value
- Secondary metrics row (fixed slots):
  - Avg Power
  - NP
  - IF/TSS
  - kJ
  - Interval Count
- Progress + devices row unchanged.

## Data Inputs and Derivations
- `power3sAvg`: rolling 3-5s average of power (use 3s for responsiveness).
- `cadence3sAvg`: rolling 3s average of cadence.
- `targetRange`: current segment `targetRange` (watts).
- `cadenceRange` (optional): current segment cadence target range.
- `intervalAvgPower`: running average power for the current segment.
- `phase`: warmup | work | recovery | cooldown.
- `isWork`: `true` for work segments.

## Phase Emphasis Rules (Visual Only)
These are emphasis changes (size, weight, contrast, opacity), not layout changes.

### Work
- Interval Time: primary emphasis, large type, high contrast.
- Power vs Target: primary emphasis, large type, compliance color.
- Heart Rate: visible, medium emphasis; zone color band on steady work only.
- Cadence Value: visible, medium emphasis.
- Secondary metrics row: dimmed.

### Recovery
- Interval Time: large remaining time, medium contrast.
- Power vs Target: subtitle shows next interval target + time to start.
- Heart Rate: visible, medium emphasis.
- Cadence Value: visible, medium emphasis.
- Secondary metrics row: brightened; show interval avg power prominently.

### Warmup
- Power vs Target: primary emphasis.
- Interval Time: medium emphasis, show elapsed.
- Secondary metrics row: normal.

### Cooldown
- Power vs Target: subtitle "Cooldown target" + remaining time.
- Secondary metrics row: brightened.
- Interval Count: de-emphasized.

## Workout Type Heuristics (Only Affects Emphasis)
- Micro intervals: average work segment duration < 120s.
  - HR zone color muted during work.
  - Cadence gauge accent color more prominent.
- Steady work: average work segment duration >= 480s.
  - HR zone color visible during work.
  - Interval avg power remains visible in secondary row even during work.
- Threshold/tempo: work segments >= 300s and target variance low.
  - Tight compliance banding on power tile.

## Primary Metrics Behavior
- Interval Time:
  - Large value shows remaining time in the current segment.
  - Small label shows elapsed time.
- Power vs Target:
  - Main value: `power3sAvg`.
  - Range: `targetRange` (low-high).
  - Compliance color: in-range vs out-of-range (no extra numbers).
- Heart Rate:
  - Main value: current HR.
  - Zone band: simple color strip; label only on steady work and recovery.
- Cadence Value:
  - Main value: `cadence3sAvg`.
  - Unit label optional (rpm).

## Cadence Gauge (Always Visible)
- Placement: docked to the right edge of the main chart row.
- Orientation: vertical mini gauge with moving indicator and numeric label.
- Baseline range:
  - Default: 70-100 rpm if no target is provided.
  - If athlete profile defines cadence range, use that instead.
- Target range overlay:
  - If `cadenceRange` exists for the segment, show a highlighted band.
  - Label the band with `low-high` in small text.
- Indicator:
  - Shows `cadence3sAvg` as a moving marker.
  - Color states:
    - Below target: cool.
    - In target: positive.
    - Above target: warm.
- No extra prompts or alerts during work.

## RPE Capture (Recovery Only)
- Show a small prompt in the secondary row during the final 20-30s of recovery.
- One-tap 0-10 choices; non-blocking.
- If missed, show again at the start of cooldown.

## Copy Guidelines
- Keep labels short and action-free.
- Avoid extra guidance on work segments.
- Use concise tags: "Next", "Cooldown", "Avg", "Target".

## Acceptance Criteria
- Work segments emphasize time remaining + power compliance without reflow.
- Recovery segments surface next target and interval avg power.
- Cadence gauge is always visible and reflects 3s cadence with optional target band.
- No additional pages, toggles, or modes introduced.
