import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';

import { CoachPanel } from '../components/CoachPanel';
import type { CoachEvent, CoachSuggestion } from '../types/coach';

describe('CoachPanel - Enhanced Features', () => {
  it('renders pending suggestions section with rationale', () => {
    const suggestions: CoachSuggestion[] = [
      {
        id: 'suggest-1',
        action: 'adjust_intensity_up',
        payload: { percent: 5, segmentIndex: 2 },
        message: 'Increase by 5%.',
        rationale: 'Power is stable, you can push more.',
        createdAtSec: 300,
        status: 'pending',
      },
    ];
    const events: CoachEvent[] = [
      {
        id: 'event-1',
        kind: 'suggestion',
        timestampSec: 300,
        message: 'Increase by 5%.',
        suggestionId: 'suggest-1',
        action: 'adjust_intensity_up',
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

    // Check pending suggestions section appears
    expect(screen.getByText('PENDING SUGGESTIONS (1)')).toBeInTheDocument();
    
    // Check suggestion message
    expect(screen.getByText('Increase by 5%.')).toBeInTheDocument();
    
    // Check rationale is displayed
    expect(screen.getByText(/Why:/)).toBeInTheDocument();
    expect(screen.getByText(/Power is stable, you can push more./)).toBeInTheDocument();
    
    // Check impact preview is displayed
    expect(screen.getByText(/Impact:/)).toBeInTheDocument();
    expect(screen.getByText(/Power targets: \+5% for remaining work intervals/)).toBeInTheDocument();
    
    // Check Accept/Reject buttons
    expect(screen.getByText('Accept')).toBeInTheDocument();
    expect(screen.getByText('Reject')).toBeInTheDocument();
  });

  it('displays recovery extension impact correctly', () => {
    const suggestions: CoachSuggestion[] = [
      {
        id: 'suggest-1',
        action: 'extend_recovery',
        payload: { seconds: 30, segmentId: 'seg-1' },
        message: 'Extend by 30 seconds.',
        rationale: 'Recovery HR is still elevated.',
        createdAtSec: 300,
        status: 'pending',
      },
    ];
    const events: CoachEvent[] = [
      {
        id: 'event-1',
        kind: 'suggestion',
        timestampSec: 300,
        message: 'Extend by 30 seconds.',
        suggestionId: 'suggest-1',
        action: 'extend_recovery',
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

    // Check recovery extension impact
    expect(screen.getByText(/Recovery: \+30s/)).toBeInTheDocument();
  });

  it('does not show accepted suggestions in pending section', () => {
    const suggestions: CoachSuggestion[] = [
      {
        id: 'suggest-1',
        action: 'adjust_intensity_up',
        payload: { percent: 5, segmentIndex: 2 },
        message: 'Increase by 5%.',
        createdAtSec: 300,
        status: 'accepted',
      },
    ];
    const events: CoachEvent[] = [
      {
        id: 'event-1',
        kind: 'suggestion',
        timestampSec: 300,
        message: 'Increase by 5%.',
        suggestionId: 'suggest-1',
        action: 'adjust_intensity_up',
      },
      {
        id: 'event-2',
        kind: 'decision',
        timestampSec: 305,
        message: 'Accepted: Increase by 5%.',
        suggestionId: 'suggest-1',
        action: 'adjust_intensity_up',
        decision: 'accepted',
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

    // Pending section should not appear
    expect(screen.queryByText('PENDING SUGGESTIONS')).not.toBeInTheDocument();
    
    // Accepted decision should appear in feed
    expect(screen.getByText(/Accepted: Increase by 5%/)).toBeInTheDocument();
  });

  it('calls onAcceptSuggestion when Accept button is clicked', () => {
    const mockAccept = vi.fn();
    const suggestions: CoachSuggestion[] = [
      {
        id: 'suggest-1',
        action: 'adjust_intensity_down',
        payload: { percent: 5, segmentIndex: 2 },
        message: 'Reduce by 5%.',
        createdAtSec: 300,
        status: 'pending',
      },
    ];
    const events: CoachEvent[] = [
      {
        id: 'event-1',
        kind: 'suggestion',
        timestampSec: 300,
        message: 'Reduce by 5%.',
        suggestionId: 'suggest-1',
        action: 'adjust_intensity_down',
      },
    ];

    render(
      <CoachPanel
        events={events}
        suggestions={suggestions}
        onAcceptSuggestion={mockAccept}
        onRejectSuggestion={() => {}}
      />
    );

    fireEvent.click(screen.getByText('Accept'));
    expect(mockAccept).toHaveBeenCalledWith('suggest-1');
  });

  it('calls onRejectSuggestion when Reject button is clicked', () => {
    const mockReject = vi.fn();
    const suggestions: CoachSuggestion[] = [
      {
        id: 'suggest-1',
        action: 'adjust_intensity_down',
        payload: { percent: 5, segmentIndex: 2 },
        message: 'Reduce by 5%.',
        createdAtSec: 300,
        status: 'pending',
      },
    ];
    const events: CoachEvent[] = [
      {
        id: 'event-1',
        kind: 'suggestion',
        timestampSec: 300,
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
        onRejectSuggestion={mockReject}
      />
    );

    fireEvent.click(screen.getByText('Reject'));
    expect(mockReject).toHaveBeenCalledWith('suggest-1');
  });
});
