import type { CoachProfile } from '../types/coach';

const DEFAULT_PROFILE: CoachProfile = {
  schemaVersion: 1,
  id: 'default-coach',
  name: 'Default Coach',
  description: 'A steady, supportive coach.',
  tagline: 'Balanced and supportive',
  voice: { tone: 'supportive', style: 'concise' },
  rules: {
    targetAdherencePct: { warn: 90, intervene: 80 },
    hrDriftPct: { warn: 4, intervene: 7 },
    cadenceVarianceRpm: { warn: 8, intervene: 12 },
    minElapsedSecondsForSuggestions: 300,
    cooldownSeconds: 240,
  },
  interventions: {
    intensityAdjustPct: { step: 5, min: -15, max: 10 },
    recoveryExtendSec: { step: 30, max: 120 },
    allowSkipRemainingOnIntervals: true,
  },
  messages: {
    suggestions: {
      adjust_intensity_up: ['Looking good. Increase by {{percent}}%?'],
      adjust_intensity_up_rationale: ['Power and cadence are stable with minimal HR drift. You have capacity for more.'],
      adjust_intensity_down: ['Let us back off by {{percent}}% to stay on track.'],
      adjust_intensity_down_rationale: ['Power is below target with elevated HR drift or cadence variance. Reducing intensity preserves quality.'],
      extend_recovery: ['Extend recovery by {{seconds}} seconds?'],
      extend_recovery_rationale: ['Recovery HR is still elevated from the previous effort. Extra recovery ensures quality in upcoming intervals.'],
      skip_remaining_on_intervals: ['Skip the remaining intervals and cool down?'],
      skip_remaining_on_intervals_rationale: ['Multiple intervals show declining performance with elevated fatigue markers. Terminating now preserves long-term progress.'],
    },
    completion: ['Session complete. Great work.'],
  },
};

const isNumber = (value: unknown): value is number =>
  typeof value === 'number' && Number.isFinite(value);

const normalizeProfile = (input: unknown): CoachProfile | null => {
  if (!input || typeof input !== 'object') {
    return null;
  }
  const data = input as Partial<CoachProfile>;
  if (!data.id || !data.name || !data.description || !data.rules || !data.interventions || !data.messages) {
    return null;
  }

  return {
    ...DEFAULT_PROFILE,
    ...data,
    rules: {
      ...DEFAULT_PROFILE.rules,
      ...data.rules,
      targetAdherencePct: {
        ...DEFAULT_PROFILE.rules.targetAdherencePct,
        ...data.rules?.targetAdherencePct,
      },
      hrDriftPct: {
        ...DEFAULT_PROFILE.rules.hrDriftPct,
        ...data.rules?.hrDriftPct,
      },
      cadenceVarianceRpm: {
        ...DEFAULT_PROFILE.rules.cadenceVarianceRpm,
        ...data.rules?.cadenceVarianceRpm,
      },
      minElapsedSecondsForSuggestions: isNumber(
        data.rules?.minElapsedSecondsForSuggestions
      )
        ? data.rules!.minElapsedSecondsForSuggestions
        : DEFAULT_PROFILE.rules.minElapsedSecondsForSuggestions,
      cooldownSeconds: isNumber(data.rules?.cooldownSeconds)
        ? data.rules!.cooldownSeconds
        : DEFAULT_PROFILE.rules.cooldownSeconds,
    },
    interventions: {
      ...DEFAULT_PROFILE.interventions,
      ...data.interventions,
      intensityAdjustPct: {
        ...DEFAULT_PROFILE.interventions.intensityAdjustPct,
        ...data.interventions?.intensityAdjustPct,
      },
      recoveryExtendSec: {
        ...DEFAULT_PROFILE.interventions.recoveryExtendSec,
        ...data.interventions?.recoveryExtendSec,
      },
      allowSkipRemainingOnIntervals:
        data.interventions?.allowSkipRemainingOnIntervals ??
        DEFAULT_PROFILE.interventions.allowSkipRemainingOnIntervals,
    },
    messages: {
      ...DEFAULT_PROFILE.messages,
      ...data.messages,
      suggestions: {
        ...DEFAULT_PROFILE.messages.suggestions,
        ...data.messages?.suggestions,
      },
      completion:
        data.messages?.completion?.length
          ? data.messages.completion
          : DEFAULT_PROFILE.messages.completion,
    },
  };
};

export const getCoachProfiles = (): CoachProfile[] => {
  const modules = import.meta.glob('../../profiles/*.json', { eager: true });
  const profiles = Object.values(modules)
    .map((mod) => {
      const data =
        typeof mod === 'object' && mod && 'default' in mod
          ? (mod as { default: CoachProfile }).default
          : (mod as CoachProfile);
      return normalizeProfile(data);
    })
    .filter((profile): profile is CoachProfile => !!profile);

  if (profiles.length === 0) {
    return [DEFAULT_PROFILE];
  }

  return profiles.sort((a, b) => a.name.localeCompare(b.name));
};

export const getCoachProfileById = (
  profiles: CoachProfile[],
  id: string | null
): CoachProfile => {
  if (!profiles.length) {
    return DEFAULT_PROFILE;
  }
  if (!id) {
    return profiles[0];
  }
  return profiles.find((profile) => profile.id === id) ?? profiles[0];
};
