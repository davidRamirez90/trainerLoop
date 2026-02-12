import type { OAuthState, OAuthToken, StravaTokens, Env } from './types';

// PKCE Code Verifier length
const CODE_VERIFIER_LENGTH = 128;
const STATE_TTL_SECONDS = 600; // 10 minutes
const TOKEN_TTL_SECONDS = 30 * 24 * 60 * 60; // 30 days

// Logger helper
function log(level: 'info' | 'error' | 'warn', message: string, data?: Record<string, unknown>) {
  const timestamp = new Date().toISOString();
  const logEntry = {
    timestamp,
    level: level.toUpperCase(),
    message: `[Strava] ${message}`,
    ...data,
  };

  if (level === 'error') {
    console.error(JSON.stringify(logEntry));
  } else if (level === 'warn') {
    console.warn(JSON.stringify(logEntry));
  } else {
    console.log(JSON.stringify(logEntry));
  }
}

/**
 * Generate a random code verifier for PKCE
 */
function generateCodeVerifier(): string {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~';
  let result = '';
  const randomValues = new Uint8Array(CODE_VERIFIER_LENGTH);
  crypto.getRandomValues(randomValues);
  
  for (let i = 0; i < CODE_VERIFIER_LENGTH; i++) {
    result += chars[randomValues[i] % chars.length];
  }
  
  return result;
}

/**
 * Generate code challenge from verifier (SHA256, base64url)
 */
async function generateCodeChallenge(verifier: string): Promise<string> {
  const encoder = new TextEncoder();
  const data = encoder.encode(verifier);
  const hash = await crypto.subtle.digest('SHA-256', data);
  
  // Base64url encode
  const base64 = btoa(String.fromCharCode(...new Uint8Array(hash)));
  return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/**
 * Generate random state parameter
 */
function generateState(): string {
  const array = new Uint8Array(32);
  crypto.getRandomValues(array);
  return btoa(String.fromCharCode(...array)).replace(/[^a-zA-Z0-9]/g, '').substring(0, 32);
}

/**
 * Store OAuth state in KV
 */
async function storeOAuthState(
  kv: KVNamespace,
  state: string,
  data: OAuthState
): Promise<void> {
  log('info', 'Storing OAuth state in KV', {
    state: state.substring(0, 8) + '...',
    userId: data.userId.substring(0, 8) + '...',
    ttl: STATE_TTL_SECONDS,
  });

  await kv.put(`state:${state}`, JSON.stringify(data), {
    expirationTtl: STATE_TTL_SECONDS,
  });

  log('info', 'OAuth state stored successfully', {
    state: state.substring(0, 8) + '...',
  });
}

/**
 * Get and delete OAuth state from KV
 */
async function getOAuthState(
  kv: KVNamespace,
  state: string
): Promise<OAuthState | null> {
  log('info', 'Retrieving OAuth state from KV', {
    state: state.substring(0, 8) + '...',
  });

  const data = await kv.get(`state:${state}`);
  if (!data) {
    log('warn', 'OAuth state not found in KV', {
      state: state.substring(0, 8) + '...',
    });
    return null;
  }

  log('info', 'OAuth state found, deleting (one-time use)', {
    state: state.substring(0, 8) + '...',
  });

  // Delete after retrieval (one-time use)
  await kv.delete(`state:${state}`);

  log('info', 'OAuth state retrieved successfully', {
    state: state.substring(0, 8) + '...',
  });

  return JSON.parse(data) as OAuthState;
}

/**
 * Store OAuth tokens in KV
 */
async function storeTokens(
  kv: KVNamespace,
  userId: string,
  tokens: OAuthToken
): Promise<void> {
  log('info', 'Storing OAuth tokens in KV', {
    userId: userId.substring(0, 8) + '...',
    athleteId: tokens.athlete.id,
    expiresAt: tokens.expiresAt,
    ttl: TOKEN_TTL_SECONDS,
  });

  await kv.put(`tokens:${userId}`, JSON.stringify(tokens), {
    expirationTtl: TOKEN_TTL_SECONDS,
  });

  log('info', 'OAuth tokens stored successfully', {
    userId: userId.substring(0, 8) + '...',
    athleteId: tokens.athlete.id,
  });
}

/**
 * Get OAuth tokens from KV
 */
export async function getTokens(
  kv: KVNamespace,
  userId: string
): Promise<OAuthToken | null> {
  log('info', 'Retrieving OAuth tokens from KV', {
    userId: userId.substring(0, 8) + '...',
  });

  const data = await kv.get(`tokens:${userId}`);
  if (!data) {
    log('info', 'No tokens found for user', {
      userId: userId.substring(0, 8) + '...',
    });
    return null;
  }

  const tokens = JSON.parse(data) as OAuthToken;
  const now = Math.floor(Date.now() / 1000);
  const isExpired = now >= tokens.expiresAt;
  const expiresIn = tokens.expiresAt - now;

  log('info', 'Tokens retrieved from KV', {
    userId: userId.substring(0, 8) + '...',
    athleteId: tokens.athlete.id,
    isExpired,
    expiresIn,
    expiresAt: tokens.expiresAt,
  });

  return tokens;
}

/**
 * Delete OAuth tokens from KV
 */
export async function deleteTokens(
  kv: KVNamespace,
  userId: string
): Promise<void> {
  log('info', 'Deleting OAuth tokens from KV', {
    userId: userId.substring(0, 8) + '...',
  });

  await kv.delete(`tokens:${userId}`);

  log('info', 'OAuth tokens deleted successfully', {
    userId: userId.substring(0, 8) + '...',
  });
}

/**
 * Initiate OAuth flow
 */
export async function initiateAuth(
  env: Env,
  redirectUri: string,
  userId: string
): Promise<{ authUrl: string; state: string }> {
  log('info', 'Initiating OAuth flow', {
    userId: userId.substring(0, 8) + '...',
    redirectUri,
  });

  const codeVerifier = generateCodeVerifier();
  const codeChallenge = await generateCodeChallenge(codeVerifier);
  const state = generateState();

  log('info', 'Generated PKCE parameters', {
    state: state.substring(0, 8) + '...',
    codeVerifierLength: codeVerifier.length,
  });

  // Store state
  await storeOAuthState(env.STRAVA_TOKENS, state, {
    codeVerifier,
    redirectUri,
    userId,
    createdAt: Date.now(),
  });

  // Build authorization URL
  const params = new URLSearchParams({
    client_id: env.STRAVA_CLIENT_ID,
    redirect_uri: redirectUri,
    response_type: 'code',
    approval_prompt: 'auto',
    scope: 'activity:write,read',
    state: state,
    code_challenge: codeChallenge,
    code_challenge_method: 'S256',
  });

  const authUrl = `https://www.strava.com/oauth/authorize?${params.toString()}`;

  log('info', 'OAuth authorization URL generated', {
    userId: userId.substring(0, 8) + '...',
    state: state.substring(0, 8) + '...',
    clientId: env.STRAVA_CLIENT_ID,
  });

  return { authUrl, state };
}

/**
 * Handle OAuth callback
 */
export async function handleCallback(
  env: Env,
  code: string,
  state: string
): Promise<OAuthToken> {
  log('info', 'Handling OAuth callback', {
    state: state.substring(0, 8) + '...',
    codeLength: code.length,
  });

  // Get stored state
  const oauthState = await getOAuthState(env.STRAVA_TOKENS, state);
  if (!oauthState) {
    log('error', 'Invalid or expired state parameter', {
      state: state.substring(0, 8) + '...',
    });
    throw new Error('Invalid or expired state parameter');
  }

  log('info', 'OAuth state validated', {
    userId: oauthState.userId.substring(0, 8) + '...',
    redirectUri: oauthState.redirectUri,
  });

  // Exchange code for tokens
  log('info', 'Exchanging code for tokens', {
    clientId: env.STRAVA_CLIENT_ID,
    grantType: 'authorization_code',
  });

  const response = await fetch('https://www.strava.com/oauth/token', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      client_id: env.STRAVA_CLIENT_ID,
      client_secret: env.STRAVA_CLIENT_SECRET,
      code: code,
      grant_type: 'authorization_code',
      code_verifier: oauthState.codeVerifier,
    }),
  });

  log('info', 'Token exchange response received', {
    status: response.status,
    ok: response.ok,
  });

  if (!response.ok) {
    const error = await response.text();
    log('error', 'Token exchange failed', {
      status: response.status,
      error: error.substring(0, 500),
    });
    throw new Error(`Token exchange failed: ${error}`);
  }

  const stravaTokens: StravaTokens = await response.json();

  log('info', 'Tokens received from Strava', {
    athleteId: stravaTokens.athlete.id,
    athleteName: `${stravaTokens.athlete.firstname} ${stravaTokens.athlete.lastname}`,
    expiresAt: stravaTokens.expires_at,
    tokenType: stravaTokens.token_type,
  });

  // Store tokens
  const token: OAuthToken = {
    accessToken: stravaTokens.access_token,
    refreshToken: stravaTokens.refresh_token,
    expiresAt: stravaTokens.expires_at,
    athlete: stravaTokens.athlete,
  };

  await storeTokens(env.STRAVA_TOKENS, oauthState.userId, token);

  log('info', 'OAuth callback completed successfully', {
    userId: oauthState.userId.substring(0, 8) + '...',
    athleteId: token.athlete.id,
  });

  return token;
}

/**
 * Refresh access token
 */
export async function refreshAccessToken(
  env: Env,
  userId: string,
  refreshToken: string
): Promise<OAuthToken> {
  log('info', 'Refreshing access token', {
    userId: userId.substring(0, 8) + '...',
    refreshTokenPrefix: refreshToken.substring(0, 8) + '...[REDACTED]',
  });

  const response = await fetch('https://www.strava.com/oauth/token', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      client_id: env.STRAVA_CLIENT_ID,
      client_secret: env.STRAVA_CLIENT_SECRET,
      refresh_token: refreshToken,
      grant_type: 'refresh_token',
    }),
  });

  log('info', 'Token refresh response received', {
    userId: userId.substring(0, 8) + '...',
    status: response.status,
    ok: response.ok,
  });

  if (!response.ok) {
    const error = await response.text();
    log('error', 'Token refresh failed', {
      userId: userId.substring(0, 8) + '...',
      status: response.status,
      error: error.substring(0, 500),
    });
    throw new Error(`Token refresh failed: ${error}`);
  }

  const stravaTokens: StravaTokens = await response.json();

  log('info', 'New tokens received from refresh', {
    userId: userId.substring(0, 8) + '...',
    athleteId: stravaTokens.athlete.id,
    expiresAt: stravaTokens.expires_at,
    tokenType: stravaTokens.token_type,
  });

  // Update stored tokens
  const token: OAuthToken = {
    accessToken: stravaTokens.access_token,
    refreshToken: stravaTokens.refresh_token,
    expiresAt: stravaTokens.expires_at,
    athlete: stravaTokens.athlete,
  };

  await storeTokens(env.STRAVA_TOKENS, userId, token);

  log('info', 'Access token refreshed successfully', {
    userId: userId.substring(0, 8) + '...',
    athleteId: token.athlete.id,
    newExpiresAt: token.expiresAt,
  });

  return token;
}

/**
 * Get valid access token (refresh if needed)
 */
export async function getValidAccessToken(
  env: Env,
  userId: string
): Promise<string> {
  log('info', 'Getting valid access token', {
    userId: userId.substring(0, 8) + '...',
  });

  const tokens = await getTokens(env.STRAVA_TOKENS, userId);

  if (!tokens) {
    log('error', 'No tokens found for user', {
      userId: userId.substring(0, 8) + '...',
    });
    throw new Error('No tokens found for user');
  }

  // Check if token is expired (with 5 minute buffer)
  const now = Math.floor(Date.now() / 1000);
  const expiresIn = tokens.expiresAt - now;
  const needsRefresh = now >= tokens.expiresAt - 300;

  log('info', 'Token expiration check', {
    userId: userId.substring(0, 8) + '...',
    expiresAt: tokens.expiresAt,
    now,
    expiresIn,
    needsRefresh,
    bufferSeconds: 300,
  });

  if (needsRefresh) {
    log('info', 'Token expired or about to expire, refreshing', {
      userId: userId.substring(0, 8) + '...',
      expiresIn,
    });
    // Token expired or about to expire, refresh it
    const newTokens = await refreshAccessToken(env, userId, tokens.refreshToken);
    log('info', 'Using refreshed access token', {
      userId: userId.substring(0, 8) + '...',
      tokenPrefix: newTokens.accessToken.substring(0, 8) + '...[REDACTED]',
    });
    return newTokens.accessToken;
  }

  log('info', 'Using existing valid access token', {
    userId: userId.substring(0, 8) + '...',
    expiresIn,
    tokenPrefix: tokens.accessToken.substring(0, 8) + '...[REDACTED]',
  });

  return tokens.accessToken;
}
