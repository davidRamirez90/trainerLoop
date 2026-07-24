
import { useMemo } from 'react';

import type { CoachEvent, CoachSuggestion } from '../types/coach';
import { formatDuration } from '../utils/time';

type CoachPanelProps = {
  events: CoachEvent[];
  suggestions: CoachSuggestion[];
  onAcceptSuggestion: (suggestionId: string) => void;
  onRejectSuggestion: (suggestionId: string) => void;
};

const formatEventLabel = (event: CoachEvent) => {
  switch (event.kind) {
    case 'suggestion':
      return 'SUGGESTION';
    case 'decision':
      return 'DECISION';
    case 'completion':
      return 'COMPLETION';
    default:
      return 'COACH';
  }
};

const getSuggestionIcon = (action: string): string => {
  switch (action) {
    case 'adjust_intensity_up':
      return '⚡';
    case 'adjust_intensity_down':
      return '⚡';
    case 'extend_recovery':
      return '🕐';
    case 'skip_remaining_on_intervals':
      return '✕';
    default:
      return '💡';
  }
};

const formatSuggestionAction = (suggestion?: CoachSuggestion) => {
  if (!suggestion) {
    return '';
  }
  const percent = suggestion.payload?.percent;
  const seconds = suggestion.payload?.seconds;
  switch (suggestion.action) {
    case 'adjust_intensity_up':
      return percent ? `Increase targets by ${percent}%` : 'Increase targets';
    case 'adjust_intensity_down':
      return percent ? `Reduce targets by ${percent}%` : 'Reduce targets';
    case 'extend_recovery':
      return seconds ? `Extend recovery by ${seconds}s` : 'Extend recovery';
    case 'skip_remaining_on_intervals':
      return 'Skip remaining intervals';
    default:
      return '';
  }
};

const formatImpactPreview = (suggestion: CoachSuggestion): string => {
  const percent = suggestion.payload?.percent;
  const seconds = suggestion.payload?.seconds;
  
  switch (suggestion.action) {
    case 'adjust_intensity_up':
    case 'adjust_intensity_down': {
      const direction = suggestion.action === 'adjust_intensity_up' ? '+' : '-';
      return percent 
        ? `Power targets: ${direction}${percent}% for remaining work intervals`
        : 'Power targets will be adjusted for remaining work intervals';
    }
    case 'extend_recovery':
      return seconds
        ? `Recovery: +${seconds}s (${seconds}s → ${seconds * 2}s total)`
        : 'Recovery duration will be extended';
    case 'skip_remaining_on_intervals':
      return 'Jump to cooldown phase immediately';
    default:
      return '';
  }
};

export const CoachPanel = ({
  events,
  suggestions,
  onAcceptSuggestion,
  onRejectSuggestion,
}: CoachPanelProps) => {
  const suggestionById = useMemo(() => {
    const map = new Map<string, CoachSuggestion>();
    suggestions.forEach((item) => map.set(item.id, item));
    return map;
  }, [suggestions]);

  const pendingSuggestions = useMemo(() => {
    return suggestions.filter((s) => s.status === 'pending');
  }, [suggestions]);

  return (
    <section>
      {pendingSuggestions.length > 0 && (
        <div className="coach-card pending-suggestions">
          <div className="coach-title">
            <span className="coach-icon pulse" />
            PENDING SUGGESTIONS ({pendingSuggestions.length})
          </div>
          <div className="coach-body">
            {pendingSuggestions.map((suggestion) => (
              <div key={suggestion.id} className="suggestion-item">
                <div className="suggestion-header">
                  <span className="suggestion-icon">
                    {getSuggestionIcon(suggestion.action)}
                  </span>
                  <span className="suggestion-action">
                    {formatSuggestionAction(suggestion)}
                  </span>
                  <span className="coach-timestamp">
                    {formatDuration(suggestion.createdAtSec)}
                  </span>
                </div>
                
                <div className="suggestion-message">{suggestion.message}</div>
                
                {suggestion.rationale && (
                  <div className="suggestion-rationale">
                    <strong>Why:</strong> {suggestion.rationale}
                  </div>
                )}
                
                <div className="suggestion-impact">
                  <strong>Impact:</strong> {formatImpactPreview(suggestion)}
                </div>
                
                <div className="session-actions">
                  <button
                    className="session-button primary"
                    type="button"
                    onClick={() => onAcceptSuggestion(suggestion.id)}
                  >
                    Accept
                  </button>
                  <button
                    className="session-button danger"
                    type="button"
                    onClick={() => onRejectSuggestion(suggestion.id)}
                  >
                    Reject
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {events.length > 0 && (
        <div className="coach-feed">
          {events.map((event) => {
          const suggestion = event.suggestionId
            ? suggestionById.get(event.suggestionId)
            : undefined;
          const isPending = suggestion?.status === 'pending';
          
          // Skip pending suggestions in the feed (they're shown above)
          if (isPending) return null;
          
          return (
            <div className="coach-card" key={event.id}>
              <div className="coach-title">
                <span className="coach-icon" />
                {formatEventLabel(event)}
                <span className="coach-timestamp">
                  {formatDuration(event.timestampSec)}
                </span>
              </div>
              <div className="coach-body">
                <div>{event.message}</div>
                {suggestion ? (
                  <div className="coach-action-summary">
                    {formatSuggestionAction(suggestion)} • 
                    <span className={`status-${suggestion.status}`}>
                      {suggestion.status}
                    </span>
                  </div>
                ) : null}
              </div>
            </div>
          );
        })}
        </div>
      )}
    </section>
  );
};
