import type { CoachProfile, CoachSuggestion } from '../types/coach';

interface CriticalSuggestionModalProps {
  suggestion: CoachSuggestion;
  profile: CoachProfile;
  metrics: {
    adherencePct: number;
    hrDriftPct: number;
    cadenceVariance: number;
    rejectedSuggestionsCount: number;
    failedIntervalsCount: number;
  };
  onAccept: () => void;
  onReject: () => void;
}

const getImpactDescription = (suggestion: CoachSuggestion): string => {
  const seconds = suggestion.payload?.seconds;
  const percent = suggestion.payload?.percent;
  
  switch (suggestion.action) {
    case 'adjust_intensity_up':
      return percent 
        ? `Target power will increase by ${percent}% for remaining work intervals`
        : 'Target power will increase for remaining work intervals';
    case 'adjust_intensity_down':
      return percent
        ? `Target power will decrease by ${percent}% for remaining work intervals`
        : 'Target power will decrease for remaining work intervals';
    case 'extend_recovery':
      return seconds
        ? `Current recovery extended by ${seconds} seconds`
        : 'Current recovery extended';
    case 'skip_remaining_on_intervals':
      return 'Jump to cooldown phase immediately';
    default:
      return '';
  }
};

const getButtonLabels = (profile: CoachProfile, action: string): { accept: string; reject: string } => {
  const voice = profile.voice?.tone ?? 'supportive';
  
  if (action === 'skip_remaining_on_intervals') {
    switch (voice) {
      case 'professional':
      case 'educational':
        return { accept: 'Skip to Cooldown', reject: 'Continue Workout' };
      case 'firm':
      case 'direct':
      case 'authoritative':
        return { accept: 'End Session', reject: 'Keep Going' };
      case 'motivational':
        return { accept: 'Cool Down Now', reject: 'Fight Through' };
      default:
        return { accept: 'Skip Intervals', reject: 'Continue' };
    }
  }
  
  return { accept: 'Accept', reject: 'Reject' };
};

export const CriticalSuggestionModal = ({
  suggestion,
  profile,
  metrics,
  onAccept,
  onReject,
}: CriticalSuggestionModalProps) => {
  const buttons = getButtonLabels(profile, suggestion.action);
  const impact = getImpactDescription(suggestion);

  return (
    <div
      className="modal-overlay"
      onClick={(e) => {
        if (e.target === e.currentTarget) {
          onReject();
        }
      }}
    >
      <div className="modal-container critical-modal">
        <div className="modal-header">
          <span className="modal-icon">⚠️</span>
          <h3>Critical Decision</h3>
        </div>
        
        <div className="modal-body">
          <div className="suggestion-message">
            {suggestion.message}
          </div>
          
          {suggestion.rationale && (
            <div className="rationale-section">
              <h4>Why this is suggested:</h4>
              <p>{suggestion.rationale}</p>
            </div>
          )}
          
          <div className="metrics-section">
            <h4>Current metrics:</h4>
            <ul>
              <li>Power adherence: {Math.round(metrics.adherencePct)}%</li>
              <li>HR drift: {metrics.hrDriftPct.toFixed(1)}%</li>
              <li>Cadence variance: {Math.round(metrics.cadenceVariance)} RPM</li>
              {metrics.rejectedSuggestionsCount > 0 && (
                <li>Rejected suggestions: {metrics.rejectedSuggestionsCount}</li>
              )}
              {metrics.failedIntervalsCount > 0 && (
                <li>Intervals showing fatigue: {metrics.failedIntervalsCount}</li>
              )}
            </ul>
          </div>
          
          <div className="impact-section">
            <h4>What will happen:</h4>
            <p>{impact}</p>
          </div>
        </div>
        
        <div className="modal-actions">
          <button
            type="button"
            className="session-button danger"
            onClick={onReject}
          >
            {buttons.reject}
          </button>
          <button
            type="button"
            className="session-button primary"
            onClick={onAccept}
          >
            {buttons.accept}
          </button>
        </div>
      </div>
    </div>
  );
};
