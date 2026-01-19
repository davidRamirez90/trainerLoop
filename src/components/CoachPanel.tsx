import { useMemo } from 'react';
import {
  useCoachProfileSelector,
  getProfileById,
  type CoachProfile,
} from '../hooks/useCoachProfile';
import {
  useCoachWithProfile,
  type CoachEvent,
} from '../hooks/useCoach';

type CoachPanelProps = {
  compliance: number;
  strain: number;
  targetAdherencePct?: number;
  hrDriftPct?: number;
  cadenceVarianceRpm?: number;
  elapsedSeconds?: number;
  onAcceptSuggestion?: (suggestionId: string, event: CoachEvent) => void;
  onRejectSuggestion?: (suggestionId: string, event: CoachEvent) => void;
  onCoachEvent?: (event: CoachEvent) => void;
};

const formatTone = (tone: string): string => {
  switch (tone) {
    case 'firm':
      return 'Direct';
    case 'encouraging':
      return 'Supportive';
    default:
      return 'Calm';
  }
};

const renderPhilosophy = (profile: CoachProfile): string => {
  const { philosophy } = profile;
  const parts = [
    `Priority: ${philosophy.priority.join(', ')}`,
    `Risk: ${philosophy.riskTolerance}`,
    `Intensity: ${philosophy.intensityBias}`,
  ];
  if (philosophy.notes) {
    parts.push(`"${philosophy.notes}"`);
  }
  return parts.join(' | ');
};

const CoachProfileSelector = ({
  availableProfiles,
  selectedProfileId,
  onSelect,
}: {
  availableProfiles: { profile: CoachProfile; source: string }[];
  selectedProfileId: string | null;
  onSelect: (id: string) => void;
}) => (
  <div className="coach-profile-selector">
    <label htmlFor="coach-select">Coach:</label>
    <select
      id="coach-select"
      value={selectedProfileId || ''}
      onChange={(e) => onSelect(e.target.value)}
    >
      {availableProfiles.map((entry) => (
        <option key={entry.profile.id} value={entry.profile.id}>
          {entry.profile.name} ({entry.source})
        </option>
      ))}
    </select>
  </div>
);

const CoachInfo = ({ profile }: { profile: CoachProfile }) => (
  <div className="coach-card">
    <div className="coach-title">
      <span className="coach-icon" />
      COACH PROFILE
    </div>
    <div className="coach-body">
      <div className="coach-name">{profile.name}</div>
      <div className="coach-description">{profile.description}</div>
      <div className="coach-meta">
        <span>Style: {formatTone(profile.voice.tone)}</span>
        <span>Tags: {profile.tags?.join(', ') || 'none'}</span>
      </div>
      <div className="coach-philosophy">{renderPhilosophy(profile)}</div>
    </div>
  </div>
);

const SuggestionCard = ({
  suggestion,
  onAccept,
  onReject,
}: {
  suggestion: {
    id: string;
    message: string;
    tone: string;
    category: string;
    intervention?: { type: string; value: number };
  };
  onAccept: () => void;
  onReject: () => void;
}) => (
  <div className="coach-card" key={suggestion.id}>
    <div className="coach-title">
      <span className="coach-icon" />
      {`${formatTone(suggestion.tone)} ${suggestion.category.toUpperCase()}`}
    </div>
    <div className="coach-body">{suggestion.message}</div>
    {suggestion.intervention && (
      <div className="coach-intervention">
        Intervention: {suggestion.intervention.type.replace(/_/g, ' ')} (
        {suggestion.intervention.value}
        {suggestion.intervention.type.includes('intensity') ? '%' : 's'})
      </div>
    )}
    <div className="session-actions">
      <button
        className="session-button primary"
        type="button"
        onClick={onAccept}
      >
        Accept
      </button>
      <button
        className="session-button danger"
        type="button"
        onClick={onReject}
      >
        Reject
      </button>
    </div>
  </div>
);

const CoachEventsLog = ({ events }: { events: CoachEvent[] }) => {
  if (events.length === 0) return null;

  return (
    <div className="coach-events-log">
      <div className="coach-title">
        <span className="coach-icon" />
        COACH EVENTS
      </div>
      <ul className="events-list">
        {events.map((event) => (
          <li key={event.id} className={`event-${event.type}`}>
            <span className="event-time">
              {new Date(event.timestamp).toLocaleTimeString()}
            </span>
            <span className="event-type">{event.type}</span>
            <span className="event-message">{event.message}</span>
          </li>
        ))}
      </ul>
    </div>
  );
};

export const CoachPanel = ({
  compliance,
  strain,
  targetAdherencePct,
  hrDriftPct,
  cadenceVarianceRpm,
  elapsedSeconds,
  onAcceptSuggestion,
  onRejectSuggestion,
  onCoachEvent,
}: CoachPanelProps) => {
  const {
    selectedProfileId,
    availableProfiles,
    selectProfile,
  } = useCoachProfileSelector();

  const profile = useMemo(
    () => getProfileById(selectedProfileId, availableProfiles),
    [selectedProfileId, availableProfiles]
  );

  const {
    suggestions,
    events,
    acceptSuggestion,
    rejectSuggestion,
    addEncouragement,
    clearEvents,
  } = useCoachWithProfile({
    compliance,
    strain,
    targetAdherencePct,
    hrDriftPct,
    cadenceVarianceRpm,
    elapsedSeconds,
    // eslint-disable-next-line @typescript-eslint/no-unnecessary-condition
    profile: profile || {
      schemaVersion: 1,
      id: 'default',
      name: 'Default Coach',
      description: 'Default coaching profile',
      voice: { tone: 'calm', style: 'concise' },
      philosophy: { priority: [], riskTolerance: 'low', intensityBias: 'moderate', recoveryBias: 'maintain' },
      rules: { targetAdherencePct: { warn: 90, intervene: 80 }, hrDriftPct: { warn: 4, intervene: 7 }, cadenceVarianceRpm: { warn: 8, intervene: 12 }, minElapsedSecondsForSuggestions: 300, cooldownSeconds: 240 },
      interventions: { intensityAdjustPct: { step: 5, min: -15, max: 10 }, recoveryExtendSec: { step: 30, max: 120 }, allowSkipRemainingOnIntervals: true },
      messages: { encouragement: ['Keep going!'], suggestions: { adjust_intensity_up: [], adjust_intensity_down: [], extend_recovery: [], skip_remaining_on_intervals: [] }, completion: [] },
    },
  });

  // Handle accept with callback
  const handleAccept = (suggestionId: string) => {
    acceptSuggestion(suggestionId);
    const event = events.find((e) => e.suggestionId === suggestionId);
    if (event) {
      onAcceptSuggestion?.(suggestionId, event);
      onCoachEvent?.(event);
    }
  };

  // Handle reject with callback
  const handleReject = (suggestionId: string) => {
    rejectSuggestion(suggestionId);
    const event = events.find((e) => e.suggestionId === suggestionId);
    if (event) {
      onRejectSuggestion?.(suggestionId, event);
      onCoachEvent?.(event);
    }
  };

  // Handle encouragement
  const handleEncouragement = () => {
    addEncouragement();
  };

  if (!profile) {
    return (
      <section className="coach-panel">
        <div className="coach-error">No coach profile selected</div>
      </section>
    );
  }

  return (
    <section className="coach-panel">
      <CoachProfileSelector
        availableProfiles={availableProfiles}
        selectedProfileId={selectedProfileId}
        onSelect={selectProfile}
      />

      <CoachInfo profile={profile} />

      {suggestions.map((suggestion) => (
        <SuggestionCard
          key={suggestion.id}
          suggestion={suggestion}
          onAccept={() => handleAccept(suggestion.id)}
          onReject={() => handleReject(suggestion.id)}
        />
      ))}

      <div className="coach-actions">
        <button
          className="session-button"
          type="button"
          onClick={handleEncouragement}
        >
          Give Encouragement
        </button>
        <button
          className="session-button secondary"
          type="button"
          onClick={clearEvents}
        >
          Clear Events
        </button>
      </div>

      <CoachEventsLog events={events} />
    </section>
  );
};

export type { CoachPanelProps };
