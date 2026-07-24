import { useState } from 'react';
import type { CoachProfile } from '../types/coach';

interface CoachSelectorModalProps {
  isOpen: boolean;
  profiles: CoachProfile[];
  selectedProfileId: string | null;
  onSelectProfile: (profileId: string) => void;
  onClose: () => void;
}

const getCoachIcon = (coachId: string): string => {
  const iconMap: Record<string, string> = {
    'aldo-sassi': '📊',
    'michele-ferrari': '⚡',
    'chris-carmichael-cts': '🎯',
    'frank-overton-fascat': '🏔️',
    'javier-sola': '💪',
    'default-coach': '🤝',
  };
  return iconMap[coachId] || '🎯';
};

const getInterventionDescription = (profile: CoachProfile): string => {
  const { interventions, rules } = profile;
  const step = interventions.intensityAdjustPct.step;
  const recoveryMax = interventions.recoveryExtendSec.max;
  const interveneThreshold = rules.targetAdherencePct.intervene;

  switch (profile.id) {
    case 'aldo-sassi':
      return `Strict data-driven approach with small ±${step}% adjustments. Maintains SFR zone integrity with precise power monitoring. Extends recovery up to ${recoveryMax}s when metrics demand it, but never allows skipping intervals. Intervenes when power adherence drops below ${interveneThreshold}%.`;
    case 'michele-ferrari':
      return `Aggressive threshold-focused methodology with large ±${step}% adjustments. Preserves the "never above threshold" principle. Allows skipping when power drops significantly. Short recovery maximum (${recoveryMax}s). Intervenes early when power adherence drops below ${interveneThreshold}%.`;
    case 'chris-carmichael-cts':
      return `Educational approach with moderate ±${step}% adjustments. Teaches you to feel the effort, not just follow numbers. Allows skipping when quality declines. Moderate recovery (${recoveryMax}s max). Fitness First means optimizing stimulus while preserving adaptation.`;
    case 'frank-overton-fascat':
      return `Systematic Sweet Spot training with standard ±${step}% adjustments. Focuses on aerobic base building and CTL management. Allows skipping when fatigue builds. Standard recovery (${recoveryMax}s). Intervenes when power adherence drops below ${interveneThreshold}% to maintain Sweet Spot productivity.`;
    case 'javier-sola':
      return `Progressive strength-centered approach with small ±${step}% adjustments. Strength training is central, not accessory. Does not allow skipping - emphasizes completing the work with quality. Longer recovery allowance (${recoveryMax}s) to protect tomorrow's session.`;
    default:
      return `Balanced approach with ±${step}% adjustments. Supportive guidance that allows skipping when needed. Moderate recovery (${recoveryMax}s max). Intervenes when power adherence drops below ${interveneThreshold}%.`;
  }
};

export const CoachSelectorModal = ({
  isOpen,
  profiles,
  selectedProfileId,
  onSelectProfile,
  onClose,
}: CoachSelectorModalProps) => {
  const [tempSelectedId, setTempSelectedId] = useState<string | null>(
    selectedProfileId
  );

  if (!isOpen) {
    return null;
  }

  const selectedProfile = profiles.find((p) => p.id === tempSelectedId);

  const handleCoachClick = (profileId: string) => {
    setTempSelectedId(profileId);
  };

  const handleConfirm = () => {
    if (tempSelectedId) {
      onSelectProfile(tempSelectedId);
    }
    onClose();
  };

  const handleCancel = () => {
    setTempSelectedId(selectedProfileId);
    onClose();
  };

  return (
    <div className="modal-overlay" onClick={handleCancel}>
      <div
        className="modal-container coach-selector-modal"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="modal-header">
          <div>
            <h3 className="modal-title">Select Coach</h3>
            <p className="modal-subtitle">
              Choose a coaching style that matches your training goals. Each
              coach has different intervention thresholds and adjustment styles
              during workouts.
            </p>
          </div>
          <button
            type="button"
            className="modal-close"
            onClick={handleCancel}
            aria-label="Close modal"
          >
            ×
          </button>
        </div>

        <div className="modal-body">
          <div className="coach-grid">
            {profiles.map((profile) => {
              const isSelected = tempSelectedId === profile.id;
              return (
                <button
                  key={profile.id}
                  type="button"
                  className={`coach-box ${isSelected ? 'selected' : ''}`}
                  onClick={() => handleCoachClick(profile.id)}
                >
                  {/* TODO: Replace with .png image: /assets/coaches/${profile.id}.png */}
                  <div className="coach-icon-placeholder">
                    <span className="coach-emoji">{getCoachIcon(profile.id)}</span>
                  </div>
                  <div className="coach-info">
                    <div className="coach-name">{profile.name}</div>
                    <div className="coach-tagline">
                      {profile.tagline || 'Professional coaching'}
                    </div>
                  </div>
                </button>
              );
            })}
          </div>

          {selectedProfile && (
            <div className="coach-description-panel">
              <div className="coach-description-header">
                <span className="coach-description-icon">
                  {getCoachIcon(selectedProfile.id)}
                </span>
                <span className="coach-description-name">
                  {selectedProfile.name}
                </span>
              </div>
              <p className="coach-intervention-description">
                {getInterventionDescription(selectedProfile)}
              </p>
            </div>
          )}
        </div>

        <div className="modal-footer">
          <button
            type="button"
            className="session-button"
            onClick={handleCancel}
          >
            Cancel
          </button>
          <button
            type="button"
            className="session-button primary"
            onClick={handleConfirm}
          >
            Confirm
          </button>
        </div>
      </div>
    </div>
  );
};
