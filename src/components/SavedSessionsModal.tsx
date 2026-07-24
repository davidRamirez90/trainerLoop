import { useState } from 'react';
import type { SessionData } from '../utils/sessionStorage';
import { loadSessionsFromStorage, deleteSessionFromStorage, clearSessionsFromStorage } from '../utils/sessionStorage';
import { MiniWorkoutChart } from './MiniWorkoutChart';
import { formatDuration } from '../utils/time';
import { buildFitFile } from '../utils/fit';

interface SavedSessionsModalProps {
  isOpen: boolean;
  onClose: () => void;
  profileFtp: number;
}

function downloadFitFile(fitData: Uint8Array, filename: string): void {
  const blob = new Blob([fitData.buffer as ArrayBuffer], { type: 'application/octet-stream' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

function buildFitFilename(planName: string, startTimeMs: number): string {
  const safeName = planName
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
  const date = new Date(startTimeMs);
  const stamp = `${date.getFullYear()}${pad2(date.getMonth() + 1)}${pad2(date.getDate())}-${pad2(date.getHours())}${pad2(date.getMinutes())}`;
  return `${safeName}-${stamp}.fit`;
}

function pad2(value: number) {
  return String(value).padStart(2, '0');
}

export function SavedSessionsModal({ isOpen, onClose, profileFtp }: SavedSessionsModalProps) {
  const [sessions, setSessions] = useState<SessionData[]>([]);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  // Load sessions when modal opens
  if (isOpen && sessions.length === 0) {
    const loaded = loadSessionsFromStorage();
    // Sort by date descending (newest first)
    const sorted = loaded.sort((a, b) => b.startTimeMs - a.startTimeMs);
    setSessions(sorted);
  }

  // Reset when closed
  if (!isOpen && sessions.length > 0) {
    setSessions([]);
    setExpandedId(null);
  }

  const handleExportSession = (session: SessionData) => {
    const fitPayload = buildFitFile({
      startTimeMs: session.startTimeMs,
      elapsedSec: session.durationSec,
      timerSec: session.durationSec,
      samples: session.samples,
    });
    const filename = buildFitFilename(session.workoutType, session.startTimeMs);
    downloadFitFile(fitPayload, filename);
  };

  const handleDeleteSession = (sessionId: string) => {
    deleteSessionFromStorage(sessionId);
    setSessions((prev) => prev.filter((s) => s.id !== sessionId));
    if (expandedId === sessionId) {
      setExpandedId(null);
    }
  };

  const handleClearAll = () => {
    if (confirm('Are you sure you want to delete all saved sessions?')) {
      clearSessionsFromStorage();
      setSessions([]);
      setExpandedId(null);
    }
  };

  const handleExportAll = () => {
    sessions.forEach((session, index) => {
      setTimeout(() => handleExportSession(session), index * 200);
    });
  };

  const formatDate = (dateStr: string) => {
    const date = new Date(dateStr);
    return date.toLocaleDateString(undefined, {
      month: 'short',
      day: 'numeric',
      year: date.getFullYear() !== new Date().getFullYear() ? 'numeric' : undefined,
    });
  };

  const formatTime = (dateStr: string) => {
    const date = new Date(dateStr);
    return date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
  };

  if (!isOpen) return null;

  return (
    <div className="modal-scrim" role="presentation" onClick={onClose}>
      <div
        className="modal saved-sessions-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="saved-sessions-title"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="modal-header">
          <div>
            <div className="modal-title" id="saved-sessions-title">
              Saved Sessions ({sessions.length})
            </div>
            <div className="modal-subtitle">
              Export or manage your workout history
            </div>
          </div>
          <button
            className="modal-close"
            type="button"
            aria-label="Close"
            onClick={onClose}
          >
            ×
          </button>
        </div>

        <div className="modal-body saved-sessions-body">
          {sessions.length === 0 ? (
            <div className="saved-sessions-empty">
              <p>No saved sessions found.</p>
              <p className="saved-sessions-hint">
                Your workouts are automatically saved when you complete or stop a session.
              </p>
            </div>
          ) : (
            <div className="saved-sessions-list">
              {sessions.map((session) => (
                <div
                  key={session.id}
                  className={`saved-session-card ${expandedId === session.id ? 'expanded' : ''}`}
                  onClick={() => setExpandedId(expandedId === session.id ? null : session.id)}
                >
                  <div className="saved-session-header">
                    <div className="saved-session-info">
                      <div className="saved-session-date">
                        {formatDate(session.date)} at {formatTime(session.date)}
                      </div>
                      <div className="saved-session-title">{session.workoutType}</div>
                      <div className="saved-session-stats">
                        <span className="stat">{formatDuration(session.durationSec)}</span>
                        <span className="stat">{session.avgPower}W avg</span>
                        <span className="stat">{session.maxPower}W max</span>
                        {session.completed && <span className="completed-badge">✓ Completed</span>}
                      </div>
                    </div>
                    <div className="saved-session-actions">
                      <button
                        className="session-button"
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          handleExportSession(session);
                        }}
                        title="Export FIT file"
                      >
                        ↓ Export
                      </button>
                      <button
                        className="session-button danger"
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          handleDeleteSession(session.id);
                        }}
                        title="Delete session"
                      >
                        ×
                      </button>
                    </div>
                  </div>

                  <div className="saved-session-chart">
                    <MiniWorkoutChart
                      samples={session.samples}
                      ftpWatts={profileFtp || 200}
                      width={480}
                      height={100}
                    />
                  </div>

                  {expandedId === session.id && (
                    <div className="saved-session-details">
                      {session.coachNotes && (
                        <div className="detail-section">
                          <h4>Coach Notes</h4>
                          <p>{session.coachNotes}</p>
                        </div>
                      )}
                      <div className="detail-grid">
                        <div className="detail-item">
                          <label>Avg Cadence</label>
                          <span className="detail-value">{session.avgCadence > 0 ? `${session.avgCadence} rpm` : '--'}</span>
                        </div>
                        <div className="detail-item">
                          <label>Avg Heart Rate</label>
                          <span className="detail-value">{session.avgHr > 0 ? `${session.avgHr} bpm` : '--'}</span>
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>

        {sessions.length > 0 && (
          <div className="modal-footer saved-sessions-footer">
            <button
              className="session-button"
              type="button"
              onClick={handleExportAll}
            >
              Export All
            </button>
            <button
              className="session-button danger"
              type="button"
              onClick={handleClearAll}
            >
              Clear All
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
