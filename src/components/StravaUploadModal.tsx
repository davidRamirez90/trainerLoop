import { useState, useCallback, useEffect } from 'react';
import { useStravaAuth } from '../hooks/useStravaAuth';
import { uploadActivityToStrava, fitFileToBase64, checkUploadStatus } from '../utils/stravaApi';
import { generateWorkoutName } from '../utils/workoutNaming';
import { generateWorkoutDescription } from '../utils/workoutDescription';
import type { WorkoutPlan, WorkoutSegment } from '../data/workout';
import type { TelemetrySample } from '../types';

interface StravaUploadModalProps {
  isOpen: boolean;
  onClose: () => void;
  plan: WorkoutPlan | null;
  segments: WorkoutSegment[];
  samples: TelemetrySample[];
  fitData: Uint8Array | null;
  adherencePercent?: number;
}

type UploadStatus = 'idle' | 'connecting' | 'uploading' | 'processing' | 'success' | 'error';

export function StravaUploadModal({
  isOpen,
  onClose,
  plan,
  segments,
  samples,
  fitData,
  adherencePercent = 0,
}: StravaUploadModalProps) {
  const { authenticated, athlete, initiateAuth } = useStravaAuth();
  const [status, setStatus] = useState<UploadStatus>('idle');
  const [error, setError] = useState<string>('');
  const [activityUrl, setActivityUrl] = useState<string>('');
  const [workoutName, setWorkoutName] = useState<string>('');
  const [description, setDescription] = useState<string>('');

  // Generate workout name and description when modal opens
  useEffect(() => {
    if (isOpen && plan && segments.length > 0) {
      const name = generateWorkoutName(plan, segments);
      const desc = generateWorkoutDescription(plan, segments, samples, adherencePercent);
      setWorkoutName(name);
      setDescription(desc);
    }
  }, [isOpen, plan, segments, samples, adherencePercent]);

  const handleConnect = async () => {
    setStatus('connecting');
    const success = await initiateAuth();
    if (success) {
      setStatus('idle');
    } else {
      setStatus('error');
      setError('Failed to connect to Strava. Please try again.');
    }
  };

  const handleUpload = async () => {
    if (!fitData || !authenticated) return;

    setStatus('uploading');
    setError('');

    try {
      const fileData = fitFileToBase64(fitData);
      const result = await uploadActivityToStrava({
        fileData,
        name: workoutName,
        description,
        sportType: 'Ride',
        deviceName: 'Trainer Loop',
      });

      setStatus('processing');

      // Poll for upload status
      pollUploadStatus(result.id);
    } catch (err) {
      setStatus('error');
      setError(err instanceof Error ? err.message : 'Upload failed');
    }
  };

  const pollUploadStatus = useCallback(async (id: number) => {
    const maxAttempts = 30;
    let attempts = 0;

    const checkStatus = async () => {
      try {
        const status = await checkUploadStatus(id);

        if (status.status === 'Your activity is ready.') {
          setStatus('success');
          if (status.activityId) {
            setActivityUrl(`https://www.strava.com/activities/${status.activityId}`);
          }
          return;
        }

        if (status.error) {
          setStatus('error');
          setError(status.error);
          return;
        }

        attempts++;
        if (attempts < maxAttempts) {
          setTimeout(checkStatus, 2000);
        } else {
          setStatus('error');
          setError('Upload timed out. Please check Strava directly.');
        }
      } catch (err) {
        setStatus('error');
        setError(err instanceof Error ? err.message : 'Status check failed');
      }
    };

    checkStatus();
  }, []);

  const handleDownload = () => {
    if (!fitData) return;

    const blob = new Blob([fitData.buffer as ArrayBuffer], { type: 'application/octet-stream' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${workoutName.replace(/[^a-z0-9]/gi, '_').toLowerCase()}.fit`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  if (!isOpen) return null;

  return (
    <div className="modal-overlay strava-modal-overlay" onClick={onClose}>
      <div className="modal-container strava-modal" onClick={(e) => e.stopPropagation()}>
        <h2 className="strava-modal-title">Export to Strava</h2>

        {error && <div className="strava-error-message">{error}</div>}

        {!authenticated ? (
          <div className="strava-connect-section">
            <p className="strava-connect-text">
              Connect your Strava account to upload this workout directly.
            </p>
            <button
              onClick={handleConnect}
              disabled={status === 'connecting'}
              className="session-button strava-button primary"
            >
              {status === 'connecting' ? 'Connecting...' : 'Connect to Strava'}
            </button>
          </div>
        ) : status === 'success' ? (
          <div className="strava-success-message">
            <svg
              width="48"
              height="48"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              className="strava-success-icon"
            >
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
              <polyline points="22 4 12 14.01 9 11.01" />
            </svg>
            <p className="strava-success-text">Upload Complete!</p>
            {activityUrl && (
              <a
                href={activityUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="strava-activity-link"
              >
                View on Strava
              </a>
            )}
          </div>
        ) : (
          <>
            {athlete && (
              <div className="strava-athlete-section">
                {athlete.profile && (
                  <img
                    src={athlete.profile}
                    alt={`${athlete.firstname} ${athlete.lastname}`}
                    className="strava-athlete-avatar"
                  />
                )}
                <div className="strava-athlete-info">
                  <div className="strava-athlete-name">
                    {athlete.firstname} {athlete.lastname}
                  </div>
                  <div className="strava-athlete-status">Connected</div>
                </div>
              </div>
            )}

            <div className="strava-form-section">
              <label className="strava-form-label">Workout Name</label>
              <input
                type="text"
                value={workoutName}
                onChange={(e) => setWorkoutName(e.target.value)}
                className="strava-form-input"
                disabled={status === 'uploading' || status === 'processing'}
              />
            </div>

            <div className="strava-form-section">
              <label className="strava-form-label">Description</label>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                className="strava-form-textarea"
                disabled={status === 'uploading' || status === 'processing'}
              />
            </div>

            {(status === 'uploading' || status === 'processing') && (
              <div className="strava-loading-section">
                <div className="strava-loading-spinner" />
                <p className="strava-loading-text">
                  {status === 'uploading' ? 'Uploading to Strava...' : 'Processing...'}
                </p>
              </div>
            )}

            <div className="strava-button-group">
              <button
                onClick={handleUpload}
                disabled={status === 'uploading' || status === 'processing'}
                className={`session-button strava-button primary ${
                  status === 'uploading' || status === 'processing' ? 'disabled' : ''
                }`}
              >
                {status === 'uploading'
                  ? 'Uploading...'
                  : status === 'processing'
                  ? 'Processing...'
                  : 'Upload to Strava'}
              </button>
              <button onClick={handleDownload} className="session-button strava-button secondary">
                Download FIT
              </button>
            </div>
          </>
        )}

        <button
          onClick={onClose}
          className="session-button strava-button secondary strava-close-button"
        >
          Close
        </button>
      </div>
    </div>
  );
}

export default StravaUploadModal;
