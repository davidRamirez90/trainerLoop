import type { ReactNode } from 'react';
import { useMemo } from 'react';

import type { CoachEvent, CoachProfile, CoachSuggestion } from '../types/coach';
import { formatDuration } from '../utils/time';

type CoachPanelProps = {
  profile: CoachProfile | null;
  profiles: CoachProfile[];
  selectedProfileId: string | null;
  onSelectProfile: (profileId: string) => void;
  events: CoachEvent[];
  suggestions: CoachSuggestion[];
  onAcceptSuggestion: (suggestionId: string) => void;
  onRejectSuggestion: (suggestionId: string) => void;
};

const formatEventLabel = (event: CoachEvent) => {
  switch (event.kind) {
    case 'encouragement':
      return 'ENCOURAGEMENT';
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

const renderFocus = (profile: CoachProfile): ReactNode => {
  const focus = profile.philosophy?.priority?.length
    ? profile.philosophy.priority
    : profile.tags ?? [];
  if (!focus.length) {
    return 'No focus areas defined.';
  }
  return focus.join(', ');
};

const renderVoice = (profile: CoachProfile): string => {
  const tone = profile.voice?.tone ?? 'supportive';
  const style = profile.voice?.style ?? 'concise';
  return `${tone} · ${style}`;
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

export const CoachPanel = ({
  profile,
  profiles,
  selectedProfileId,
  onSelectProfile,
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

  return (
    <section>
      <div className="coach-card">
        <div className="coach-title">
          <span className="coach-icon" />
          COACH PROFILE
        </div>
        <div className="coach-body">
          <label className="coach-field">
            <span>Coach</span>
            <select
              value={selectedProfileId ?? profile?.id ?? ''}
              onChange={(event) => onSelectProfile(event.target.value)}
            >
              {profiles.map((coach) => (
                <option key={coach.id} value={coach.id}>
                  {coach.name}
                </option>
              ))}
            </select>
          </label>
          {profile ? (
            <>
              <div className="coach-meta">{profile.description}</div>
              <div>Voice: {renderVoice(profile)}</div>
              <div>Focus: {renderFocus(profile)}</div>
            </>
          ) : (
            <div>Loading coach profile...</div>
          )}
        </div>
      </div>

      <div className="coach-feed">
        {events.length === 0 ? (
          <div className="coach-card">
            <div className="coach-title">
              <span className="coach-icon" />
              COACH FEED
            </div>
            <div className="coach-body">Coach updates will appear here.</div>
          </div>
        ) : null}
        {events.map((event) => {
          const suggestion = event.suggestionId
            ? suggestionById.get(event.suggestionId)
            : undefined;
          const showActions = event.kind === 'suggestion'
            && suggestion?.status === 'pending';
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
                    {formatSuggestionAction(suggestion)}
                  </div>
                ) : null}
                {showActions ? (
                  <div className="session-actions">
                    <button
                      className="session-button primary"
                      type="button"
                      onClick={() => onAcceptSuggestion(event.suggestionId ?? '')}
                    >
                      Accept
                    </button>
                    <button
                      className="session-button danger"
                      type="button"
                      onClick={() => onRejectSuggestion(event.suggestionId ?? '')}
                    >
                      Reject
                    </button>
                  </div>
                ) : null}
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
};
