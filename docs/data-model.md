# Data Model

This is a minimal internal model to support the MVP. Fields are suggestions and
can evolve as the implementation matures.

## CoachProfile

```ts
type CoachProfile = {
  id: string;
  name: string;
  description: string;
  rules: CoachRules;
  interventions: CoachInterventions;
  voice: CoachVoice;
};
```

```ts
type CoachRules = {
  targetAdherencePct: { warn: number; intervene: number };
  hrDriftPct: { warn: number; intervene: number };
  cadenceVarianceRpm: { warn: number; intervene: number };
  minElapsedSecondsForSuggestions: number;
  cooldownSeconds: number;
};
```

```ts
type CoachInterventions = {
  intensityAdjustPct: { step: number; min: number; max: number };
  recoveryExtendSec: { step: number; max: number };
  allowSkipRemainingOnIntervals: boolean;
};
```

```ts
type CoachVoice = {
  tone: "calm" | "firm" | "energetic";
  style: "concise" | "detailed";
};
```

## Workout

```ts
type Workout = {
  id: string;
  name: string;
  description?: string;
  source: "manual" | "imported";
  segments: WorkoutSegment[];
};
```

```ts
type WorkoutSegment =
  | StepSegment
  | RampSegment
  | FreeRideSegment;
```

```ts
type StepSegment = {
  type: "step";
  durationSec: number;
  targetPowerWatts?: number;
  targetCadenceRpm?: number;
  label?: string;
  isOnInterval: boolean;
};
```

```ts
type RampSegment = {
  type: "ramp";
  durationSec: number;
  startPowerWatts: number;
  endPowerWatts: number;
  targetCadenceRpm?: number;
  label?: string;
  isOnInterval: boolean;
};
```

```ts
type FreeRideSegment = {
  type: "free";
  durationSec: number;
  label?: string;
  isOnInterval: false;
};
```

## Session

```ts
type Session = {
  id: string;
  workoutId: string;
  coachProfileId: string;
  startedAt: string;
  endedAt?: string;
  status: "active" | "paused" | "completed" | "aborted";
  summary?: SessionSummary;
  notes: CoachEvent[];
};
```

```ts
type SessionSummary = {
  totalDurationSec: number;
  avgPowerWatts?: number;
  avgCadenceRpm?: number;
  avgHrBpm?: number;
  complianceScore?: number;
  completionStars?: number;
};
```

## Telemetry

```ts
type TelemetrySample = {
  timestamp: string;
  powerWatts?: number;
  cadenceRpm?: number;
  hrBpm?: number;
  speedKph?: number;
  targetPowerWatts?: number;
  targetCadenceRpm?: number;
  isErgMode: boolean;
  dropout: boolean;
};
```

```ts
type TelemetryWindow = {
  start: string;
  end: string;
  avgPowerWatts?: number;
  avgCadenceRpm?: number;
  avgHrBpm?: number;
  adherencePct?: number;
  cadenceVarianceRpm?: number;
  hrDriftPct?: number;
};
```

## Coach Events and Suggestions

```ts
type CoachEvent = {
  id: string;
  sessionId: string;
  timestamp: string;
  type: "encouragement" | "suggestion" | "completion";
  message: string;
  rationale?: string;
  metrics?: TelemetryWindow;
  suggestion?: CoachSuggestion;
  userResponse?: CoachResponse;
};
```

```ts
type CoachSuggestion = {
  action: "adjust_intensity" | "extend_recovery" | "skip_remaining_on_intervals";
  payload: Record<string, unknown>;
  suggestedAtSegmentIndex?: number;
};
```

```ts
type CoachResponse = {
  response: "accepted" | "rejected";
  respondedAt: string;
};
```
