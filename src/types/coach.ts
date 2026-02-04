export type CoachAction =
  | 'adjust_intensity_up'
  | 'adjust_intensity_down'
  | 'extend_recovery'
  | 'skip_remaining_on_intervals';

export type CoachSuggestionStatus = 'pending' | 'accepted' | 'rejected';

export type CoachSuggestion = {
  id: string;
  action: CoachAction;
  payload?: {
    percent?: number;
    seconds?: number;
    segmentId?: string;
    segmentIndex?: number;
  };
  message: string;
  rationale?: string;
  createdAtSec: number;
  status: CoachSuggestionStatus;
};

export type CoachEventKind =
  | 'suggestion'
  | 'decision'
  | 'completion';

export type CoachEvent = {
  id: string;
  kind: CoachEventKind;
  timestampSec: number;
  message: string;
  suggestionId?: string;
  action?: CoachAction;
  payload?: CoachSuggestion['payload'];
  decision?: CoachSuggestionStatus;
};

export type CoachProfile = {
  schemaVersion: number;
  id: string;
  name: string;
  description: string;
  tagline?: string;
  author?: string;
  tags?: string[];
  voice?: {
    tone?: string;
    style?: string;
  };
  philosophy?: {
    priority?: string[];
    riskTolerance?: string;
    intensityBias?: string;
    recoveryBias?: string;
    notes?: string;
  };
  rules: {
    targetAdherencePct: { warn: number; intervene: number };
    hrDriftPct: { warn: number; intervene: number };
    cadenceVarianceRpm: { warn: number; intervene: number };
    minElapsedSecondsForSuggestions: number;
    cooldownSeconds: number;
  };
  interventions: {
    intensityAdjustPct: { step: number; min: number; max: number };
    recoveryExtendSec: { step: number; max: number };
    allowSkipRemainingOnIntervals: boolean;
  };
  messages: {
    suggestions: {
      adjust_intensity_up: string[];
      adjust_intensity_up_rationale: string[];
      adjust_intensity_down: string[];
      adjust_intensity_down_rationale: string[];
      extend_recovery: string[];
      extend_recovery_rationale: string[];
      skip_remaining_on_intervals: string[];
      skip_remaining_on_intervals_rationale: string[];
    };
    completion: string[];
  };
};
