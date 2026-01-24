import { render, screen } from '@testing-library/react';

import { CoachPanel } from '../components/CoachPanel';
import type { CoachEvent, CoachProfile, CoachSuggestion } from '../types/coach';

describe('CoachPanel', () => {
  it('renders coach profile and suggestions', () => {
    const profile: CoachProfile = {
      schemaVersion: 1,
      id: 'coach-test',
      name: 'Coach Test',
      description: 'Test profile.',
      rules: {
        targetAdherencePct: { warn: 90, intervene: 80 },
        hrDriftPct: { warn: 4, intervene: 7 },
        cadenceVarianceRpm: { warn: 8, intervene: 12 },
        minElapsedSecondsForSuggestions: 300,
        cooldownSeconds: 240,
      },
      interventions: {
        intensityAdjustPct: { step: 5, min: -10, max: 10 },
        recoveryExtendSec: { step: 30, max: 120 },
        allowSkipRemainingOnIntervals: true,
      },
      messages: {
        encouragement: ['Nice work.'],
        suggestions: {
          adjust_intensity_up: ['Increase by {{percent}}%.'],
          adjust_intensity_down: ['Reduce by {{percent}}%.'],
          extend_recovery: ['Extend by {{seconds}} seconds.'],
          skip_remaining_on_intervals: ['Skip remaining intervals.'],
        },
        completion: ['Session complete.'],
      },
    };
    const suggestions: CoachSuggestion[] = [
      {
        id: 'suggest-1',
        action: 'adjust_intensity_down',
        payload: { percent: 5, segmentIndex: 2 },
        message: 'Reduce by 5%.',
        createdAtSec: 120,
        status: 'pending',
      },
    ];
    const events: CoachEvent[] = [
      {
        id: 'event-1',
        kind: 'suggestion',
        timestampSec: 120,
        message: 'Reduce by 5%.',
        suggestionId: 'suggest-1',
        action: 'adjust_intensity_down',
      },
    ];

    render(
      <CoachPanel
        profile={profile}
        profiles={[profile]}
        selectedProfileId={profile.id}
        onSelectProfile={() => {}}
        events={events}
        suggestions={suggestions}
        onAcceptSuggestion={() => {}}
        onRejectSuggestion={() => {}}
      />
    );

    expect(screen.getByText('Coach Test')).toBeInTheDocument();
    expect(screen.getByText('Test profile.')).toBeInTheDocument();
    expect(screen.getByText('Reduce by 5%.')).toBeInTheDocument();
  });
});
