import { render, screen } from '@testing-library/react';

import { CoachPanel } from '../components/CoachPanel';
import type { CoachEvent, CoachSuggestion } from '../types/coach';

describe('CoachPanel', () => {
  it('renders coach suggestions', () => {
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
        events={events}
        suggestions={suggestions}
        onAcceptSuggestion={() => {}}
        onRejectSuggestion={() => {}}
      />
    );

    expect(screen.getByText('Reduce by 5%.')).toBeInTheDocument();
  });
});
