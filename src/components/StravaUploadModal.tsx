import type { CSSProperties } from 'react';
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

  const overlayStyle: CSSProperties = {
    position: 'fixed',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 1000,
  };

  const modalStyle: CSSProperties = {
    backgroundColor: '#1a1a1a',
    borderRadius: '12px',
    padding: '32px',
    maxWidth: '500px',
    width: '90%',
    maxHeight: '90vh',
    overflow: 'auto',
    boxShadow: '0 20px 40px rgba(0, 0, 0, 0.5)',
  };

  const titleStyle: CSSProperties = {
    fontSize: '24px',
    fontWeight: 700,
    marginBottom: '24px',
    color: '#ffffff',
  };

  const sectionStyle: CSSProperties = {
    marginBottom: '24px',
  };

  const labelStyle: CSSProperties = {
    display: 'block',
    fontSize: '12px',
    fontWeight: 600,
    textTransform: 'uppercase' as const,
    letterSpacing: '0.5px',
    color: '#888888',
    marginBottom: '8px',
  };

  const inputStyle: CSSProperties = {
    width: '100%',
    padding: '12px',
    backgroundColor: '#2a2a2a',
    border: '1px solid #444444',
    borderRadius: '6px',
    color: '#ffffff',
    fontSize: '14px',
    boxSizing: 'border-box',
  };

  const textareaStyle: CSSProperties = {
    ...inputStyle,
    minHeight: '100px',
    resize: 'vertical',
    fontFamily: 'inherit',
  };

  const buttonContainerStyle: CSSProperties = {
    display: 'flex',
    gap: '12px',
    marginTop: '24px',
  };

  const primaryButtonStyle: CSSProperties = {
    flex: 1,
    padding: '14px 24px',
    backgroundColor: '#fc4c02',
    color: 'white',
    border: 'none',
    borderRadius: '6px',
    fontSize: '14px',
    fontWeight: 600,
    cursor: 'pointer',
    transition: 'background-color 0.2s',
  };

  const secondaryButtonStyle: CSSProperties = {
    flex: 1,
    padding: '14px 24px',
    backgroundColor: 'transparent',
    color: '#888888',
    border: '1px solid #444444',
    borderRadius: '6px',
    fontSize: '14px',
    fontWeight: 600,
    cursor: 'pointer',
  };

  const errorStyle: CSSProperties = {
    padding: '12px',
    backgroundColor: 'rgba(239, 68, 68, 0.2)',
    border: '1px solid rgba(239, 68, 68, 0.5)',
    borderRadius: '6px',
    color: '#ef4444',
    fontSize: '14px',
    marginBottom: '16px',
  };

  const successStyle: CSSProperties = {
    padding: '16px',
    backgroundColor: 'rgba(34, 197, 94, 0.2)',
    border: '1px solid rgba(34, 197, 94, 0.5)',
    borderRadius: '6px',
    textAlign: 'center' as const,
  };

  return (
    <div style={overlayStyle} onClick={onClose}>
      <div style={modalStyle} onClick={(e) => e.stopPropagation()}>
        <h2 style={titleStyle}>Export to Strava</h2>

        {error && <div style={errorStyle}>{error}</div>}

        {!authenticated ? (
          <div style={{ textAlign: 'center', padding: '32px 0' }}>
            <p style={{ color: '#888888', marginBottom: '24px' }}>
              Connect your Strava account to upload this workout directly.
            </p>
            <button
              onClick={handleConnect}
              disabled={status === 'connecting'}
              style={primaryButtonStyle}
            >
              {status === 'connecting' ? 'Connecting...' : 'Connect to Strava'}
            </button>
          </div>
        ) : status === 'success' ? (
          <div style={successStyle}>
            <svg
              width="48"
              height="48"
              viewBox="0 0 24 24"
              fill="none"
              stroke="#22c55e"
              strokeWidth="2"
              style={{ marginBottom: '12px' }}
            >
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
              <polyline points="22 4 12 14.01 9 11.01" />
            </svg>
            <p style={{ color: '#22c55e', fontWeight: 600, marginBottom: '8px' }}>
              Upload Complete!
            </p>
            {activityUrl && (
              <a
                href={activityUrl}
                target="_blank"
                rel="noopener noreferrer"
                style={{ color: '#fc4c02', textDecoration: 'underline' }}
              >
                View on Strava
              </a>
            )}
          </div>
        ) : (
          <>
            {athlete && (
              <div style={{ ...sectionStyle, display: 'flex', alignItems: 'center', gap: '12px' }}>
                {athlete.profile && (
                  <img
                    src={athlete.profile}
                    alt={`${athlete.firstname} ${athlete.lastname}`}
                    style={{ width: 40, height: 40, borderRadius: '50%' }}
                  />
                )}
                <div>
                  <div style={{ color: '#ffffff', fontWeight: 600 }}>
                    {athlete.firstname} {athlete.lastname}
                  </div>
                  <div style={{ color: '#22c55e', fontSize: '12px' }}>✓ Connected</div>
                </div>
              </div>
            )}

            <div style={sectionStyle}>
              <label style={labelStyle}>Workout Name</label>
              <input
                type="text"
                value={workoutName}
                onChange={(e) => setWorkoutName(e.target.value)}
                style={inputStyle}
                disabled={status === 'uploading' || status === 'processing'}
              />
            </div>

            <div style={sectionStyle}>
              <label style={labelStyle}>Description</label>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                style={textareaStyle}
                disabled={status === 'uploading' || status === 'processing'}
              />
            </div>

            {(status === 'uploading' || status === 'processing') && (
              <div style={{ ...sectionStyle, textAlign: 'center', padding: '16px' }}>
                <div
                  style={{
                    width: '40px',
                    height: '40px',
                    border: '3px solid #333',
                    borderTop: '3px solid #fc4c02',
                    borderRadius: '50%',
                    animation: 'spin 1s linear infinite',
                    margin: '0 auto 12px',
                  }}
                />
                <p style={{ color: '#888888' }}>
                  {status === 'uploading' ? 'Uploading to Strava...' : 'Processing...'}
                </p>
              </div>
            )}

            <div style={buttonContainerStyle}>
              <button
                onClick={handleUpload}
                disabled={status === 'uploading' || status === 'processing'}
                style={{
                  ...primaryButtonStyle,
                  opacity: status === 'uploading' || status === 'processing' ? 0.7 : 1,
                  cursor: status === 'uploading' || status === 'processing' ? 'not-allowed' : 'pointer',
                }}
              >
                {status === 'uploading'
                  ? 'Uploading...'
                  : status === 'processing'
                  ? 'Processing...'
                  : 'Upload to Strava'}
              </button>
              <button onClick={handleDownload} style={secondaryButtonStyle}>
                Download FIT
              </button>
            </div>
          </>
        )}

        <button
          onClick={onClose}
          style={{
            ...secondaryButtonStyle,
            marginTop: '12px',
            width: '100%',
          }}
        >
          Close
        </button>

        <style>{`
          @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
          }
        `}</style>
      </div>
    </div>
  );
}

export default StravaUploadModal;
