# Coach Profiles

Coach profiles are JSON files that describe a coaching philosophy, threshold
rules, intervention limits, and messaging style. They are loaded at session
start and persisted with the session.

## Location and Naming

- Store profiles in `trainer-loop/profiles/`.
- File name should match `id` and use kebab-case, for example:
  `profiles/tempo-traditionalist.json`.
- Keep `id` stable so historical sessions can reference it.

## Schema (v1)

Required fields:

- `schemaVersion` (number)
- `id`, `name`, `description` (strings)
- `rules` (thresholds and timing guards)
- `interventions` (what changes can be suggested)
- `voice` (tone and verbosity)

Optional fields:

- `author`, `tags`, `philosophy`, `messages`

## Interesting Attributes to Research

- Training ideology: base-first, threshold focus, polarized, etc.
- Risk tolerance: when to recommend stopping vs. pushing.
- Intensity bias: conservative vs. aggressive progression.
- Recovery bias: prefers extending recovery or maintaining plan.
- Suggestion cadence: cooldown time and minimum effort before suggestions.
- Feedback tone: calm vs. firm, concise vs. detailed.
- Metrics emphasis: tight adherence vs. HR drift vs. cadence stability.

## Template (copy/paste)

```json
{
  "schemaVersion": 1,
  "id": "coach_slug",
  "name": "Coach Name",
  "description": "Short description of this coach's approach.",
  "author": "Source or inspiration (optional).",
  "tags": ["base", "conservative"],
  "voice": {
    "tone": "calm",
    "style": "concise"
  },
  "philosophy": {
    "priority": ["consistency", "aerobic_base"],
    "riskTolerance": "low",
    "intensityBias": "conservative",
    "recoveryBias": "extend_if_needed",
    "notes": "Any short notes that capture the coach's ideology."
  },
  "rules": {
    "targetAdherencePct": { "warn": 90, "intervene": 80 },
    "hrDriftPct": { "warn": 4, "intervene": 7 },
    "cadenceVarianceRpm": { "warn": 8, "intervene": 12 },
    "minElapsedSecondsForSuggestions": 300,
    "cooldownSeconds": 240
  },
  "interventions": {
    "intensityAdjustPct": { "step": 5, "min": -15, "max": 10 },
    "recoveryExtendSec": { "step": 30, "max": 120 },
    "allowSkipRemainingOnIntervals": true
  },
  "messages": {
    "encouragement": [
      "Nice work keeping power steady.",
      "Smooth cadence. Keep it up."
    ],
    "suggestions": {
      "adjust_intensity_up": [
        "This looks comfortable. Want to raise targets by {{percent}}%?"
      ],
      "adjust_intensity_down": [
        "Power is below target and HR drift is up. Reduce by {{percent}}%?"
      ],
      "extend_recovery": [
        "HR is still elevated. Extend recovery by {{seconds}} seconds?"
      ],
      "skip_remaining_on_intervals": [
        "You've fought hard. Skip remaining on-intervals and cool down?"
      ]
    },
    "completion": [
      "Session complete. Solid consistency today."
    ]
  }
}
```
