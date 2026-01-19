import type { ReactNode } from 'react';

import { useCoach } from '../hooks/useCoach';
import type { CoachSuggestion } from '../hooks/useCoach';

type CoachPanelProps = {
  compliance: number;
  strain: number;
  onAcceptSuggestion?: (suggestionId: string) => void;
};

const formatTone = (tone: CoachSuggestion['tone']) =>
  tone === 'direct' ? 'Direct' : 'Supportive';

const renderCoachFocus = (focus: string[]): ReactNode => {
  if (!focus.length) {
    return 'No focus areas defined.';
  }
  return focus.join(', ');
};

export const CoachPanel = ({
  compliance,
  strain,
  onAcceptSuggestion,
}: CoachPanelProps) => {
  const { coachProfile, suggestions } = useCoach({ compliance, strain });

  return (
    <section>
      <div className="coach-card">
        <div className="coach-title">
          <span className="coach-icon" />
          COACH PROFILE
        </div>
        <div className="coach-body">
          <div>{coachProfile.name}</div>
          <div>{coachProfile.title}</div>
          <div>Style: {formatTone(coachProfile.style)}</div>
          <div>Focus: {renderCoachFocus(coachProfile.focus)}</div>
        </div>
      </div>

      {suggestions.map((suggestion) => (
        <div className="coach-card" key={suggestion.id}>
          <div className="coach-title">
            <span className="coach-icon" />
            {`${formatTone(suggestion.tone)} SUGGESTION`.toUpperCase()}
          </div>
          <div className="coach-body">{suggestion.message}</div>
          <div className="session-actions">
            <button
              className="session-button primary"
              type="button"
              onClick={() => onAcceptSuggestion?.(suggestion.id)}
            >
              Accept
            </button>
            <button className="session-button danger" type="button">
              Reject
            </button>
          </div>
        </div>
      ))}
    </section>
  );
};
