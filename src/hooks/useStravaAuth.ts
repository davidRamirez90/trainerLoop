import { useState, useEffect, useCallback } from 'react';
import { STRAVA_CONFIG } from '../config/strava';

interface StravaAthlete {
  id: number;
  firstname: string;
  lastname: string;
  profile: string;
}

interface AuthState {
  authenticated: boolean;
  athlete: StravaAthlete | null;
}

export function useStravaAuth() {
  const [authState, setAuthState] = useState<AuthState>({
    authenticated: false,
    athlete: null,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Check auth status on mount
  useEffect(() => {
    checkAuthStatus();
  }, []);

  const checkAuthStatus = async () => {
    try {
      setError(null);
      const response = await fetch(`${STRAVA_CONFIG.WORKER_URL}/auth/status`, {
        headers: {
          'X-User-ID': getUserId(),
        },
      });

      if (response.ok) {
        const data = await response.json();
        if (data.success) {
          setAuthState({
            authenticated: data.data.authenticated,
            athlete: data.data.athlete || null,
          });
        }
      } else {
        const errorData = await response.text();
        console.error('Strava auth status check failed:', response.status, errorData);
        setError(`Worker error: ${response.status}`);
      }
    } catch (err) {
      console.error('Failed to check Strava auth status:', err);
      setError('Worker not reachable. Is the Cloudflare Worker deployed?');
    } finally {
      setLoading(false);
    }
  };

  const initiateAuth = useCallback((): Promise<boolean> => {
    return new Promise((resolve) => {
      setError(null);
      
      // Check if worker URL is configured
      if (!STRAVA_CONFIG.WORKER_URL || STRAVA_CONFIG.WORKER_URL === 'http://localhost:8787') {
        const errorMsg = 'Strava Worker not configured. Please set VITE_STRAVA_WORKER_URL in .env.local';
        console.error(errorMsg);
        setError(errorMsg);
        resolve(false);
        return;
      }

      const redirectUri = window.location.origin;
      
      // Open popup with loading message
      const popup = window.open(
        '',
        'stravaAuth',
        `width=${STRAVA_CONFIG.POPUP_WIDTH},height=${STRAVA_CONFIG.POPUP_HEIGHT},left=${
          (window.screen.width - STRAVA_CONFIG.POPUP_WIDTH) / 2
        },top=${(window.screen.height - STRAVA_CONFIG.POPUP_HEIGHT) / 2}`
      );

      if (!popup) {
        setError('Popup blocked. Please allow popups for this site.');
        resolve(false);
        return;
      }

      // Show loading state in popup
      popup.document.write(`
        <html>
          <body style="font-family: system-ui, sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; background: #1a1a1a; color: #fff;">
            <div style="text-align: center;">
              <div style="margin-bottom: 16px;">Connecting to Strava...</div>
              <div style="width: 40px; height: 40px; border: 3px solid #333; border-top: 3px solid #fc4c02; border-radius: 50%; animation: spin 1s linear infinite; margin: 0 auto;"></div>
              <style>@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }</style>
            </div>
          </body>
        </html>
      `);

      // Get auth URL from worker
      fetch(
        `${STRAVA_CONFIG.WORKER_URL}/auth/initiate?redirect_uri=${encodeURIComponent(
          redirectUri
        )}`,
        {
          headers: {
            'X-User-ID': getUserId(),
          },
        }
      )
        .then(async (response) => {
          if (!response.ok) {
            const errorText = await response.text();
            throw new Error(`Worker error: ${response.status} - ${errorText}`);
          }
          return response.json();
        })
        .then((data) => {
          if (data.success && data.data.authUrl) {
            popup.location.href = data.data.authUrl;

            // Listen for message from popup
            const handleMessage = (event: MessageEvent) => {
              if (event.origin !== window.location.origin) return;

              if (event.data.type === 'STRAVA_AUTH_SUCCESS') {
                window.removeEventListener('message', handleMessage);
                checkAuthStatus();
                resolve(true);
              } else if (event.data.type === 'STRAVA_AUTH_ERROR') {
                window.removeEventListener('message', handleMessage);
                setError('Authentication failed. Please try again.');
                resolve(false);
              }
            };

            window.addEventListener('message', handleMessage);

            // Check if popup is closed
            const checkClosed = setInterval(() => {
              if (popup.closed) {
                clearInterval(checkClosed);
                window.removeEventListener('message', handleMessage);
                resolve(false);
              }
            }, 1000);
          } else {
            popup.close();
            setError('Failed to get authorization URL from worker');
            resolve(false);
          }
        })
        .catch((err) => {
          console.error('Failed to initiate Strava auth:', err);
          popup.close();
          setError(`Worker error: ${err.message}. Is the Cloudflare Worker deployed?`);
          resolve(false);
        });
    });
  }, []);

  const logout = useCallback(async () => {
    try {
      setError(null);
      await fetch(`${STRAVA_CONFIG.WORKER_URL}/auth/logout`, {
        method: 'POST',
        headers: {
          'X-User-ID': getUserId(),
        },
      });
      setAuthState({ authenticated: false, athlete: null });
    } catch (err) {
      console.error('Failed to logout from Strava:', err);
      setError('Failed to disconnect from Strava');
    }
  }, []);

  return {
    ...authState,
    loading,
    error,
    initiateAuth,
    logout,
    refreshAuth: checkAuthStatus,
  };
}

// Generate consistent user ID
function getUserId(): string {
  const storageKey = 'trainerLoop.userId';
  let userId = localStorage.getItem(storageKey);
  if (!userId) {
    userId = `${Date.now()}-${Math.random().toString(36).substring(2, 15)}`;
    localStorage.setItem(storageKey, userId);
  }
  return userId;
}
