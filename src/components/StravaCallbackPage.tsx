import { useEffect, useState } from 'react';
import { STRAVA_CONFIG } from '../config/strava';

export function StravaCallbackPage() {
  const [status, setStatus] = useState<'processing' | 'success' | 'error'>('processing');
  const [message, setMessage] = useState('Completing authentication...');

  useEffect(() => {
    const completeAuth = async () => {
      try {
        const urlParams = new URLSearchParams(window.location.search);
        const code = urlParams.get('code');
        const state = urlParams.get('state');
        const error = urlParams.get('error');

        if (error) {
          setStatus('error');
          setMessage(`Authorization denied: ${error}`);
          if (window.opener) {
            window.opener.postMessage({ type: 'STRAVA_AUTH_ERROR', error }, window.location.origin);
          }
          return;
        }

        if (!code || !state) {
          setStatus('error');
          setMessage('Missing authorization code or state');
          if (window.opener) {
            window.opener.postMessage({ type: 'STRAVA_AUTH_ERROR', error: 'Missing parameters' }, window.location.origin);
          }
          return;
        }

        // Exchange code for tokens via worker
        const response = await fetch(`${STRAVA_CONFIG.WORKER_URL}/auth/callback?code=${encodeURIComponent(code)}&state=${encodeURIComponent(state)}`);

        if (!response.ok) {
          const errorText = await response.text();
          throw new Error(errorText);
        }

        const data = await response.json();

        if (data.success) {
          setStatus('success');
          setMessage('Successfully connected to Strava!');
          
          // Notify parent window
          if (window.opener) {
            window.opener.postMessage({ type: 'STRAVA_AUTH_SUCCESS' }, window.location.origin);
            setTimeout(() => window.close(), 1500);
          }
        } else {
          throw new Error(data.error || 'Authentication failed');
        }
      } catch (err) {
        const errorMsg = err instanceof Error ? err.message : 'Authentication failed';
        setStatus('error');
        setMessage(errorMsg);
        
        if (window.opener) {
          window.opener.postMessage({ type: 'STRAVA_AUTH_ERROR', error: errorMsg }, window.location.origin);
        }
      }
    };

    completeAuth();
  }, []);

  const getStatusColor = () => {
    switch (status) {
      case 'success':
        return '#4CAF50';
      case 'error':
        return '#f44336';
      default:
        return '#fc4c02';
    }
  };

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '100vh',
        backgroundColor: '#1a1a1a',
        color: '#fff',
        fontFamily: 'system-ui, -apple-system, sans-serif',
        padding: '20px',
        textAlign: 'center',
      }}
    >
      <div
        style={{
          width: '60px',
          height: '60px',
          border: `4px solid ${status === 'processing' ? '#333' : getStatusColor()}`,
          borderTop: `4px solid ${getStatusColor()}`,
          borderRadius: '50%',
          animation: status === 'processing' ? 'spin 1s linear infinite' : 'none',
          marginBottom: '20px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        {status === 'success' && (
          <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="#4CAF50" strokeWidth="3">
            <polyline points="20 6 9 17 4 12" />
          </svg>
        )}
        {status === 'error' && (
          <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="#f44336" strokeWidth="3">
            <line x1="18" y1="6" x2="6" y2="18" />
            <line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        )}
      </div>

      <h2 style={{ margin: '0 0 10px 0', fontSize: '24px' }}>
        {status === 'processing' && 'Connecting to Strava...'}
        {status === 'success' && 'Connected!'}
        {status === 'error' && 'Connection Failed'}
      </h2>

      <p style={{ margin: 0, color: '#888', fontSize: '16px' }}>{message}</p>

      {status === 'error' && (
        <button
          onClick={() => window.close()}
          style={{
            marginTop: '20px',
            padding: '10px 20px',
            backgroundColor: '#fc4c02',
            color: '#fff',
            border: 'none',
            borderRadius: '6px',
            cursor: 'pointer',
            fontSize: '14px',
          }}
        >
          Close Window
        </button>
      )}

      <style>{`
        @keyframes spin {
          0% { transform: rotate(0deg); }
          100% { transform: rotate(360deg); }
        }
      `}</style>
    </div>
  );
}
