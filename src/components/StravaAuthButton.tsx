import type { CSSProperties } from 'react';
import { useStravaAuth } from '../hooks/useStravaAuth';

interface StravaAuthButtonProps {
  onConnect?: () => void;
  className?: string;
  style?: CSSProperties;
  size?: 'small' | 'medium' | 'large';
}

const STRAVA_BRAND_COLOR = '#fc4c02';

export function StravaAuthButton({
  onConnect,
  className = '',
  style,
  size = 'medium',
}: StravaAuthButtonProps) {
  const { authenticated, athlete, loading, initiateAuth } = useStravaAuth();

  const handleClick = async () => {
    if (authenticated) {
      // Already connected - could show disconnect option or do nothing
      return;
    }

    const success = await initiateAuth();
    if (success && onConnect) {
      onConnect();
    }
  };

  const sizeStyles: Record<string, CSSProperties> = {
    small: {
      padding: '6px 12px',
      fontSize: '12px',
    },
    medium: {
      padding: '10px 20px',
      fontSize: '14px',
    },
    large: {
      padding: '14px 28px',
      fontSize: '16px',
    },
  };

  const baseStyles: CSSProperties = {
    display: 'inline-flex',
    alignItems: 'center',
    gap: '8px',
    border: 'none',
    borderRadius: '4px',
    fontWeight: 600,
    cursor: loading ? 'not-allowed' : 'pointer',
    transition: 'all 0.2s ease',
    opacity: loading ? 0.7 : 1,
    ...sizeStyles[size],
  };

  const connectedStyles: CSSProperties = {
    ...baseStyles,
    backgroundColor: '#10b981',
    color: 'white',
  };

  const disconnectedStyles: CSSProperties = {
    ...baseStyles,
    backgroundColor: STRAVA_BRAND_COLOR,
    color: 'white',
    boxShadow: '0 2px 4px rgba(252, 76, 2, 0.3)',
  };

  if (loading) {
    return (
      <button
        disabled
        className={`strava-button strava-button-loading ${className}`}
        style={{ ...disconnectedStyles, ...style }}
      >
        <span className="strava-loading-spinner" />
        Connecting...
      </button>
    );
  }

  if (authenticated && athlete) {
    return (
      <button
        disabled
        className={`strava-button strava-button-connected ${className}`}
        style={{ ...connectedStyles, ...style }}
      >
        <svg
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <polyline points="20 6 9 17 4 12" />
        </svg>
        Connected to Strava
      </button>
    );
  }

  return (
    <button
      onClick={handleClick}
      className={`strava-button strava-button-connect ${className}`}
      style={{ ...disconnectedStyles, ...style }}
    >
      <svg
        width="16"
        height="16"
        viewBox="0 0 24 24"
        fill="currentColor"
      >
        <path d="M15.387 17.944l-2.089-4.116h-3.065L15.387 24l5.15-10.172h-3.066m-7.008-5.599l2.836 5.598h4.172L10.477 0 4.444 12.343h4.172" />
      </svg>
      Connect to Strava
    </button>
  );
}

export default StravaAuthButton;
