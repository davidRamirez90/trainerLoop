import { useMemo } from 'react';

export type CoachSuggestion = {
  id: string;
  message: string;
  tone: 'supportive' | 'direct';
};

export type CoachState = {
  compliance: number;
  strain: number;
  suggestions: CoachSuggestion[];
  coachProfile: CoachProfile;
};

type CoachInputs = {
  compliance: number;
  strain: number;
};

type CoachProfile = {
  id: string;
  name: string;
  title: string;
  style: 'supportive' | 'direct';
  focus: string[];
};

const sampleCoachProfile: CoachProfile = {
  id: 'coach-ari',
  name: 'Ari Mendoza',
  title: 'Performance Coach',
  style: 'supportive',
  focus: ['consistency', 'recovery', 'progressive overload'],
};

const buildSuggestions = ({
  compliance,
  strain,
  tone,
}: CoachInputs & { tone: CoachSuggestion['tone'] }): CoachSuggestion[] => {
  const suggestions: CoachSuggestion[] = [];

  if (compliance < 0.8) {
    suggestions.push({
      id: 'reduce-intensity',
      message:
        'Dial back intensity for the next block and rebuild consistency.',
      tone,
    });
  }

  if (strain > 0.8) {
    suggestions.push({
      id: 'prioritize-recovery',
      message: 'Your strain is high. Prioritize recovery and easy volume.',
      tone,
    });
  }

  if (suggestions.length === 0) {
    suggestions.push({
      id: 'steady-course',
      message: 'Stay the course. Keep stacking consistent sessions.',
      tone,
    });
  }

  return suggestions;
};

export const useCoach = ({ compliance, strain }: CoachInputs): CoachState => {
  const suggestions = useMemo(
    () =>
      buildSuggestions({
        compliance,
        strain,
        tone: sampleCoachProfile.style,
      }),
    [compliance, strain],
  );

  return {
    compliance,
    strain,
    suggestions,
    coachProfile: sampleCoachProfile,
  };
};
