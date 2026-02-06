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

  // Check auth status on mount
  useEffect(() => {
    checkAuthStatus();
  }, []);

  const checkAuthStatus = async () => {
    try {
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
      }
    } catch (error) {
      console.error('Failed to check Strava auth status:', error);
    } finally {
      setLoading(false);
    }
  };

  const initiateAuth = useCallback((): Promise<boolean> => {
    return new Promise((resolve) => {
      const redirectUri = `${window.location.origin}/strava-callback`;
      const popup = window.open(
        '',
        'stravaAuth',
        `width=${STRAVA_CONFIG.POPUP_WIDTH},height=${STRAVA_CONFIG.POPUP_HEIGHT},left=${
          (window.screen.width - STRAVA_CONFIG.POPUP_WIDTH) / 2
        },top=${(window.screen.height - STRAVA_CONFIG.POPUP_HEIGHT) / 2}`
      );

      if (!popup) {
        resolve(false);
        return;
      }

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
        .then((response) => response.json())
        .then((data) => {
          if (data.success) {
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
            resolve(false);
          }
        })
        .catch((error) => {
          console.error('Failed to initiate Strava auth:', error);
          popup.close();
          resolve(false);
        });
    });
  }, []);

  const logout = useCallback(async () => {
    try {
      await fetch(`${STRAVA_CONFIG.WORKER_URL}/auth/logout`, {
        method: 'POST',
        headers: {
          'X-User-ID': getUserId(),
        },
      });
      setAuthState({ authenticated: false, athlete: null });
    } catch (error) {
      console.error('Failed to logout from Strava:', error);
    }
  }, []);

  return {
    ...authState,
    loading,
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
