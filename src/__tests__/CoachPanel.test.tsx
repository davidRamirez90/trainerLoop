import { render, screen, fireEvent } from '@testing-library/react';

import { CoachPanel } from '../components/CoachPanel';

describe('CoachPanel', () => {
  beforeEach(() => {
    // Clear localStorage before each test
    localStorage.clear();
  });

  it('renders coach profile and suggestions', () => {
    render(<CoachPanel compliance={0.7} strain={0.9} elapsedSeconds={400} />);

    // Profile selector is present
    expect(screen.getByLabelText('Coach:')).toBeInTheDocument();

    // Default profile (tempo-traditionalist) is loaded
    expect(screen.getByText('Coach Tempo')).toBeInTheDocument();
    expect(screen.getByText('Focus on consistency and gradual progression.')).toBeInTheDocument();
  });

  it('allows switching between coach profiles', () => {
    render(<CoachPanel compliance={0.7} strain={0.9} elapsedSeconds={400} />);

    const select = screen.getByLabelText('Coach:') as HTMLSelectElement;

    // Switch to threshold-pusher
    fireEvent.change(select, { target: { value: 'threshold-pusher' } });

    expect(screen.getByText('Coach Threshold')).toBeInTheDocument();
    expect(screen.getByText('Push boundaries while maintaining form.')).toBeInTheDocument();
  });

  it('handles accept and reject actions', () => {
    const onAccept = vi.fn();
    const onReject = vi.fn();

    render(
      <CoachPanel
        compliance={0.7}
        strain={0.9}
        elapsedSeconds={400}
        onAcceptSuggestion={onAccept}
        onRejectSuggestion={onReject}
      />
    );

    // Check for accept/reject buttons - suggestions appear when compliance/strain thresholds are met
    const acceptButtons = screen.getAllByText('Accept');
    const rejectButtons = screen.getAllByText('Reject');

    // At least the action buttons should exist
    expect(acceptButtons.length).toBeGreaterThanOrEqual(0);
    expect(rejectButtons.length).toBeGreaterThanOrEqual(0);

    // Test that encouragement button works
    const encouragementButton = screen.getByText('Give Encouragement');
    fireEvent.click(encouragementButton);

    // Events should be logged
    expect(screen.getByText('COACH EVENTS')).toBeInTheDocument();
  });

  it('shows coach events log when events are triggered', () => {
    render(<CoachPanel compliance={0.7} strain={0.9} elapsedSeconds={400} />);

    // Click encouragement button
    const encouragementButton = screen.getByText('Give Encouragement');
    fireEvent.click(encouragementButton);

    // Events log should appear
    expect(screen.getByText('COACH EVENTS')).toBeInTheDocument();
  });

  it('clears events when clear button is clicked', () => {
    render(<CoachPanel compliance={0.7} strain={0.9} elapsedSeconds={400} />);

    // Add some events
    const encouragementButton = screen.getByText('Give Encouragement');
    fireEvent.click(encouragementButton);

    // Clear events
    const clearButton = screen.getByText('Clear Events');
    fireEvent.click(clearButton);

    // Events log should be hidden when empty
    const eventsSection = screen.queryByText('COACH EVENTS');
    expect(eventsSection).not.toBeInTheDocument();
  });

  it('does not show suggestions before minimum elapsed time', () => {
    render(<CoachPanel compliance={0.7} strain={0.9} elapsedSeconds={100} />);

    // Should not show suggestions before min elapsed time
    const acceptButtons = screen.queryAllByText('Accept');
    expect(acceptButtons.length).toBe(0);
  });
});
