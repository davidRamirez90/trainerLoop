import { useMemo, useCallback, useState } from 'react';
import type { CoachProfile } from './useCoachProfile';

export type CoachSuggestion = {
  id: string;
  message: string;
  tone: 'supportive' | 'direct';
  category: 'encouragement' | 'suggestion' | 'completion';
  intervention?: {
    type: 'intensity_adjust' | 'recovery_extend' | 'skip_remaining';
    value: number;
  };
};

export type CoachEvent = {
  id: string;
  timestamp: number;
  type: 'suggestion' | 'accepted' | 'rejected' | 'encouragement' | 'completion';
  suggestionId?: string;
  message: string;
  category: CoachSuggestion['category'];
};

export type CoachState = {
  compliance: number;
  strain: number;
  suggestions: CoachSuggestion[];
  coachProfile: CoachProfile;
  events: CoachEvent[];
  acceptSuggestion: (suggestionId: string) => void;
  rejectSuggestion: (suggestionId: string) => void;
  addEncouragement: () => void;
  clearEvents: () => void;
};

type CoachInputs = {
  compliance: number;
  strain: number;
  targetAdherencePct?: number;
  hrDriftPct?: number;
  cadenceVarianceRpm?: number;
  elapsedSeconds?: number;
};

// Legacy profile type for backward compatibility
export type LegacyCoachProfile = {
  id: string;
  name: string;
  title: string;
  style: 'supportive' | 'direct';
  focus: string[];
};

// Convert legacy profile to new format
const convertLegacyProfile = (legacy: LegacyCoachProfile): CoachProfile => ({
  schemaVersion: 1,
  id: legacy.id,
  name: legacy.name,
  description: `Legacy profile: ${legacy.title}`,
  voice: {
    tone: legacy.style === 'direct' ? 'firm' : 'encouraging',
    style: 'concise',
  },
  philosophy: {
    priority: legacy.focus,
    riskTolerance: 'low',
    intensityBias: 'moderate',
    recoveryBias: 'maintain',
  },
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
    encouragement: [
      'Keep up the great work!',
      'You are making progress.',
      'Stay consistent.',
    ],
    suggestions: {
      adjust_intensity_up: [
        'Consider increasing intensity by {{percent}}%.',
      ],
      adjust_intensity_down: [
        'Dial back intensity by {{percent}}%.',
      ],
      extend_recovery: [
        'Extend recovery by {{seconds}} seconds.',
      ],
      skip_remaining_on_intervals: [
        'Skip remaining intervals and cool down?',
      ],
    },
    completion: [
      'Session complete. Great work!',
    ],
  },
});

const sampleLegacyProfile: LegacyCoachProfile = {
  id: 'coach-ari',
  name: 'Ari Mendoza',
  title: 'Performance Coach',
  style: 'supportive',
  focus: ['consistency', 'recovery', 'progressive overload'],
};

const generateEventId = () => `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;

const buildSuggestions = ({
  strain,
  targetAdherencePct = 100,
  hrDriftPct = 0,
  cadenceVarianceRpm = 0,
  elapsedSeconds = 0,
  profile,
}: Omit<CoachInputs, 'compliance'> & { profile: CoachProfile }): CoachSuggestion[] => {
  const suggestions: CoachSuggestion[] = [];
  const { rules, messages, interventions } = profile;

  // Check elapsed time threshold
  if (elapsedSeconds < rules.minElapsedSecondsForSuggestions) {
    return [];
  }

  // Check adherence
  if (targetAdherencePct < rules.targetAdherencePct.intervene) {
    suggestions.push({
      id: `low-adherence-${Date.now()}`,
      message: messages.suggestions.adjust_intensity_down[0] || 'Consider reducing intensity.',
      tone: profile.voice.tone === 'firm' ? 'direct' : 'supportive',
      category: 'suggestion',
      intervention: {
        type: 'intensity_adjust',
        value: interventions.intensityAdjustPct.step,
      },
    });
  } else if (targetAdherencePct < rules.targetAdherencePct.warn) {
    suggestions.push({
      id: `moderate-adherence-${Date.now()}`,
      message: 'Power is below target. Consider adjusting.',
      tone: profile.voice.tone === 'firm' ? 'direct' : 'supportive',
      category: 'suggestion',
      intervention: {
        type: 'intensity_adjust',
        value: interventions.intensityAdjustPct.step,
      },
    });
  }

  // Check HR drift
  if (hrDriftPct > rules.hrDriftPct.intervene) {
    suggestions.push({
      id: `hr-drift-high-${Date.now()}`,
      message: messages.suggestions.extend_recovery[0] || 'HR is elevated. Extend recovery?',
      tone: profile.voice.tone === 'firm' ? 'direct' : 'supportive',
      category: 'suggestion',
      intervention: {
        type: 'recovery_extend',
        value: interventions.recoveryExtendSec.step,
      },
    });
  } else if (hrDriftPct > rules.hrDriftPct.warn) {
    suggestions.push({
      id: `hr-drift-moderate-${Date.now()}`,
      message: 'Heart rate drifting. Stay hydrated.',
      tone: profile.voice.tone === 'firm' ? 'direct' : 'supportive',
      category: 'suggestion',
    });
  }

  // Check cadence variance
  if (cadenceVarianceRpm > rules.cadenceVarianceRpm.intervene) {
    suggestions.push({
      id: `cadence-variance-${Date.now()}`,
      message: 'Cadence unstable. Focus on smooth pedaling.',
      tone: profile.voice.tone === 'firm' ? 'direct' : 'supportive',
      category: 'suggestion',
    });
  }

  // Check strain
  if (strain > 0.85) {
    suggestions.push({
      id: `high-strain-${Date.now()}`,
      message: 'Strain is high. Prioritize recovery.',
      tone: profile.voice.tone === 'firm' ? 'direct' : 'supportive',
      category: 'suggestion',
    });
  }

  // If no suggestions but everything is good
  if (suggestions.length === 0 && elapsedSeconds > rules.minElapsedSecondsForSuggestions) {
    suggestions.push({
      id: `steady-${Date.now()}`,
      message: messages.encouragement[0] || 'Stay the course. Keep it up!',
      tone: profile.voice.tone === 'firm' ? 'direct' : 'supportive',
      category: 'encouragement',
    });
  }

  return suggestions;
};

export const useCoach = ({
  compliance,
  strain,
  targetAdherencePct,
  hrDriftPct,
  cadenceVarianceRpm,
  elapsedSeconds,
}: CoachInputs): CoachState => {
  const [events, setEvents] = useState<CoachEvent[]>([]);

  // Use legacy profile for backward compatibility
  const legacyProfile = sampleLegacyProfile;
  const profile = useMemo(
    () => convertLegacyProfile(legacyProfile),
    [legacyProfile]
  );

  const suggestions = useMemo(
    () =>
      buildSuggestions({
        strain,
        targetAdherencePct,
        hrDriftPct,
        cadenceVarianceRpm,
        elapsedSeconds,
        profile,
      }),
    [strain, targetAdherencePct, hrDriftPct, cadenceVarianceRpm, elapsedSeconds, profile]
  );

  const acceptSuggestion = useCallback(
    (suggestionId: string) => {
      const suggestion = suggestions.find((s) => s.id === suggestionId);
      if (!suggestion) return;

      const event: CoachEvent = {
        id: generateEventId(),
        timestamp: Date.now(),
        type: 'accepted',
        suggestionId,
        message: `Accepted: ${suggestion.message}`,
        category: suggestion.category,
      };

      setEvents((prev) => [...prev, event]);
    },
    [suggestions]
  );

  const rejectSuggestion = useCallback(
    (suggestionId: string) => {
      const suggestion = suggestions.find((s) => s.id === suggestionId);
      if (!suggestion) return;

      const event: CoachEvent = {
        id: generateEventId(),
        timestamp: Date.now(),
        type: 'rejected',
        suggestionId,
        message: `Rejected: ${suggestion.message}`,
        category: suggestion.category,
      };

      setEvents((prev) => [...prev, event]);
    },
    [suggestions]
  );

  const addEncouragement = useCallback(() => {
    const encouragement = profile.messages.encouragement;
    const message =
      encouragement[Math.floor(Math.random() * encouragement.length)] ||
      'Keep going!';

    const event: CoachEvent = {
      id: generateEventId(),
      timestamp: Date.now(),
      type: 'encouragement',
      message,
      category: 'encouragement',
    };

    setEvents((prev) => [...prev, event]);
  }, [profile]);

  const clearEvents = useCallback(() => {
    setEvents([]);
  }, []);

  return {
    compliance,
    strain,
    suggestions,
    coachProfile: profile,
    events,
    acceptSuggestion,
    rejectSuggestion,
    addEncouragement,
    clearEvents,
  };
};

// Enhanced version that accepts a profile
export const useCoachWithProfile = (
  inputs: CoachInputs & { profile: CoachProfile }
): CoachState => {
  const [events, setEvents] = useState<CoachEvent[]>([]);
  const { profile } = inputs;

  const suggestions = useMemo(
    () => buildSuggestions({ ...inputs, profile }),
    [inputs, profile]
  );

  const acceptSuggestion = useCallback(
    (suggestionId: string) => {
      const suggestion = suggestions.find((s) => s.id === suggestionId);
      if (!suggestion) return;

      const event: CoachEvent = {
        id: generateEventId(),
        timestamp: Date.now(),
        type: 'accepted',
        suggestionId,
        message: `Accepted: ${suggestion.message}`,
        category: suggestion.category,
      };

      setEvents((prev) => [...prev, event]);
    },
    [suggestions]
  );

  const rejectSuggestion = useCallback(
    (suggestionId: string) => {
      const suggestion = suggestions.find((s) => s.id === suggestionId);
      if (!suggestion) return;

      const event: CoachEvent = {
        id: generateEventId(),
        timestamp: Date.now(),
        type: 'rejected',
        suggestionId,
        message: `Rejected: ${suggestion.message}`,
        category: suggestion.category,
      };

      setEvents((prev) => [...prev, event]);
    },
    [suggestions]
  );

  const addEncouragement = useCallback(() => {
    const encouragement = profile.messages.encouragement;
    const message =
      encouragement[Math.floor(Math.random() * encouragement.length)] ||
      'Keep going!';

    const event: CoachEvent = {
      id: generateEventId(),
      timestamp: Date.now(),
      type: 'encouragement',
      message,
      category: 'encouragement',
    };

    setEvents((prev) => [...prev, event]);
  }, [profile]);

  const clearEvents = useCallback(() => {
    setEvents([]);
  }, []);

  return {
    compliance: inputs.compliance,
    strain: inputs.strain,
    suggestions,
    coachProfile: profile,
    events,
    acceptSuggestion,
    rejectSuggestion,
    addEncouragement,
    clearEvents,
  };
};
