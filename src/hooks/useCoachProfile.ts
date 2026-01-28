/**
 * Coach Profile Loader
 * Loads coach profiles from JSON files and provides selection functionality.
 */

import { useState, useCallback, useEffect } from 'react';

export type CoachProfile = {
  schemaVersion: number;
  id: string;
  name: string;
  description: string;
  author?: string;
  tags?: string[];
  voice: {
    tone: 'calm' | 'firm' | 'encouraging';
    style: 'concise' | 'detailed';
  };
  philosophy: {
    priority: string[];
    riskTolerance: 'low' | 'medium' | 'high';
    intensityBias: 'conservative' | 'moderate' | 'aggressive';
    recoveryBias: 'maintain' | 'extend_if_needed';
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
    encouragement: string[];
    suggestions: {
      adjust_intensity_up: string[];
      adjust_intensity_down: string[];
      extend_recovery: string[];
      skip_remaining_on_intervals: string[];
    };
    completion: string[];
  };
};

// Default built-in coach profiles
const defaultProfiles: Record<string, CoachProfile> = {
  'tempo-traditionalist': {
    schemaVersion: 1,
    id: 'tempo-traditionalist',
    name: 'Coach Tempo',
    description: 'Focus on consistency and gradual progression.',
    author: 'Built-in',
    tags: ['base', 'conservative'],
    voice: {
      tone: 'calm',
      style: 'concise',
    },
    philosophy: {
      priority: ['consistency', 'aerobic_base'],
      riskTolerance: 'low',
      intensityBias: 'conservative',
      recoveryBias: 'extend_if_needed',
      notes: 'Emphasize steady state and gradual adaptation.',
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
        'Nice work keeping power steady.',
        'Smooth cadence. Keep it up.',
        'Solid effort on this interval.',
      ],
      suggestions: {
        adjust_intensity_up: [
          'This looks comfortable. Want to raise targets by {{percent}}%?',
        ],
        adjust_intensity_down: [
          'Power is below target and HR drift is up. Reduce by {{percent}}%?',
        ],
        extend_recovery: [
          'HR is still elevated. Extend recovery by {{seconds}} seconds?',
        ],
        skip_remaining_on_intervals: [
          "You've fought hard. Skip remaining on-intervals and cool down?",
        ],
      },
      completion: [
        'Session complete. Solid consistency today.',
        'Great work. Rest up for tomorrow.',
      ],
    },
  },
  'threshold-pusher': {
    schemaVersion: 1,
    id: 'threshold-pusher',
    name: 'Coach Threshold',
    description: 'Push boundaries while maintaining form.',
    author: 'Built-in',
    tags: ['threshold', 'performance'],
    voice: {
      tone: 'firm',
      style: 'detailed',
    },
    philosophy: {
      priority: ['threshold_power', 'form_maintenance'],
      riskTolerance: 'medium',
      intensityBias: 'aggressive',
      recoveryBias: 'maintain',
      notes: 'Challenge limits but respect recovery signals.',
    },
    rules: {
      targetAdherencePct: { warn: 85, intervene: 75 },
      hrDriftPct: { warn: 5, intervene: 8 },
      cadenceVarianceRpm: { warn: 10, intervene: 15 },
      minElapsedSecondsForSuggestions: 240,
      cooldownSeconds: 180,
    },
    interventions: {
      intensityAdjustPct: { step: 3, min: -10, max: 15 },
      recoveryExtendSec: { step: 15, max: 90 },
      allowSkipRemainingOnIntervals: true,
    },
    messages: {
      encouragement: [
        'Right on target. Push through.',
        'Form looks solid. Keep grinding.',
        'Almost there. Give it everything.',
      ],
      suggestions: {
        adjust_intensity_up: [
          "You're handling this well. Bump up {{percent}}%?",
        ],
        adjust_intensity_down: [
          'Form starting to break down. Drop {{percent}}%?',
        ],
        extend_recovery: [
          'HR climbing too fast. Add {{seconds}}s recovery?',
        ],
        skip_remaining_on_intervals: [
          'Solid session. Cool down early?',
        ],
      },
      completion: [
        'Crushing it! Rest and recover.',
        'Session done. Great threshold work.',
      ],
    },
  },
  'supportive-guide': {
    schemaVersion: 1,
    id: 'supportive-guide',
    name: 'Coach Support',
    description: 'Encouraging approach focused on enjoyment and sustainability.',
    author: 'Built-in',
    tags: ['beginner', 'endurance', 'supportive'],
    voice: {
      tone: 'encouraging',
      style: 'detailed',
    },
    philosophy: {
      priority: ['enjoyment', 'sustainability', 'gradual_progress'],
      riskTolerance: 'low',
      intensityBias: 'conservative',
      recoveryBias: 'extend_if_needed',
      notes: 'Focus on making every session positive.',
    },
    rules: {
      targetAdherencePct: { warn: 80, intervene: 70 },
      hrDriftPct: { warn: 3, intervene: 5 },
      cadenceVarianceRpm: { warn: 6, intervene: 10 },
      minElapsedSecondsForSuggestions: 420,
      cooldownSeconds: 300,
    },
    interventions: {
      intensityAdjustPct: { step: 5, min: -20, max: 5 },
      recoveryExtendSec: { step: 45, max: 180 },
      allowSkipRemainingOnIntervals: true,
    },
    messages: {
      encouragement: [
        'You are doing amazing!',
        'Every pedal stroke counts. Proud of you!',
        'Great rhythm. You got this!',
        'Look at you go! Keep shining.',
      ],
      suggestions: {
        adjust_intensity_up: [
          'Feeling strong? Try {{percent}}% more?',
        ],
        adjust_intensity_down: [
          'Take it easy. Drop {{percent}}% and find your flow.',
        ],
        extend_recovery: [
          'No rush. Add {{seconds}} seconds to recover better.',
        ],
        skip_remaining_on_intervals: [
          'You have already achieved so much. Cool down when ready?',
        ],
      },
      completion: [
        'Wonderful session! You should feel proud.',
        'Amazing work today. Rest well!',
      ],
    },
  },
};

// Type for profile source
type ProfileSource = 'built-in' | 'custom';

export type CoachProfileEntry = {
  profile: CoachProfile;
  source: ProfileSource;
};

export type CoachProfileSelector = {
  selectedProfileId: string | null;
  availableProfiles: CoachProfileEntry[];
  selectProfile: (id: string) => void;
  loadCustomProfile: (profile: CoachProfile) => void;
  resetToDefault: () => void;
};

export const useCoachProfileSelector = (): CoachProfileSelector => {
  const [selectedProfileId, setSelectedProfileId] = useState<string | null>(
    () => {
      // Try to load from localStorage, default to tempo-traditionalist
      const saved = localStorage.getItem('selectedCoachProfileId');
      return saved && defaultProfiles[saved] ? saved : 'tempo-traditionalist';
    }
  );

  const [availableProfiles, setAvailableProfiles] = useState<CoachProfileEntry[]>(
    () => {
      // Load built-in profiles
      const builtIns: CoachProfileEntry[] = Object.values(defaultProfiles).map(
        (profile) => ({
          profile,
          source: 'built-in' as const,
        })
      );

      // Load custom profiles from localStorage
      try {
        const customData = localStorage.getItem('customCoachProfiles');
        if (customData) {
          const customProfiles: CoachProfile[] = JSON.parse(customData);
          customProfiles.forEach((profile) => {
            builtIns.push({ profile, source: 'custom' });
          });
        }
      } catch {
        // Ignore parsing errors
      }

      return builtIns;
    }
  );

  // Persist selected profile
  useEffect(() => {
    if (selectedProfileId) {
      localStorage.setItem('selectedCoachProfileId', selectedProfileId);
    }
  }, [selectedProfileId]);

  const selectProfile = useCallback((id: string) => {
    setSelectedProfileId(id);
  }, []);

  const loadCustomProfile = useCallback((profile: CoachProfile) => {
    // Validate required fields
    if (!profile.id || !profile.name || !profile.schemaVersion) {
      throw new Error('Invalid profile: missing required fields');
    }

    // Check for duplicate ID
    setAvailableProfiles((prev) => {
      const exists = prev.some((p) => p.profile.id === profile.id);
      if (exists) {
        // Update existing
        return prev.map((p) =>
          p.profile.id === profile.id ? { profile, source: 'custom' as const } : p
        );
      }
      // Add new
      return [...prev, { profile, source: 'custom' as const }];
    });

    // Select the newly loaded profile
    setSelectedProfileId(profile.id);
  }, []);

  const resetToDefault = useCallback(() => {
    setSelectedProfileId('tempo-traditionalist');
  }, []);

  return {
    selectedProfileId,
    availableProfiles,
    selectProfile,
    loadCustomProfile,
    resetToDefault,
  };
};

export const getProfileById = (
  id: string | null,
  availableProfiles: CoachProfileEntry[]
): CoachProfile | null => {
  if (!id) return null;
  const entry = availableProfiles.find((p) => p.profile.id === id);
  return entry?.profile ?? null;
};
