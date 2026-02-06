import type { OAuthState, OAuthToken, StravaTokens, Env } from './types';

// PKCE Code Verifier length
const CODE_VERIFIER_LENGTH = 128;
const STATE_TTL_SECONDS = 600; // 10 minutes
const TOKEN_TTL_SECONDS = 30 * 24 * 60 * 60; // 30 days

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
  await kv.put(`state:${state}`, JSON.stringify(data), {
    expirationTtl: STATE_TTL_SECONDS,
  });
}

/**
 * Get and delete OAuth state from KV
 */
async function getOAuthState(
  kv: KVNamespace,
  state: string
): Promise<OAuthState | null> {
  const data = await kv.get(`state:${state}`);
  if (!data) return null;
  
  // Delete after retrieval (one-time use)
  await kv.delete(`state:${state}`);
  
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
  await kv.put(`tokens:${userId}`, JSON.stringify(tokens), {
    expirationTtl: TOKEN_TTL_SECONDS,
  });
}

/**
 * Get OAuth tokens from KV
 */
export async function getTokens(
  kv: KVNamespace,
  userId: string
): Promise<OAuthToken | null> {
  const data = await kv.get(`tokens:${userId}`);
  if (!data) return null;
  return JSON.parse(data) as OAuthToken;
}

/**
 * Delete OAuth tokens from KV
 */
export async function deleteTokens(
  kv: KVNamespace,
  userId: string
): Promise<void> {
  await kv.delete(`tokens:${userId}`);
}

/**
 * Initiate OAuth flow
 */
export async function initiateAuth(
  env: Env,
  redirectUri: string,
  userId: string
): Promise<{ authUrl: string; state: string }> {
  const codeVerifier = generateCodeVerifier();
  const codeChallenge = await generateCodeChallenge(codeVerifier);
  const state = generateState();

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
  // Get stored state
  const oauthState = await getOAuthState(env.STRAVA_TOKENS, state);
  if (!oauthState) {
    throw new Error('Invalid or expired state parameter');
  }

  // Exchange code for tokens
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

  if (!response.ok) {
    const error = await response.text();
    throw new Error(`Token exchange failed: ${error}`);
  }

  const stravaTokens: StravaTokens = await response.json();

  // Store tokens
  const token: OAuthToken = {
    accessToken: stravaTokens.access_token,
    refreshToken: stravaTokens.refresh_token,
    expiresAt: stravaTokens.expires_at,
    athlete: stravaTokens.athlete,
  };

  await storeTokens(env.STRAVA_TOKENS, oauthState.userId, token);

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

  if (!response.ok) {
    const error = await response.text();
    throw new Error(`Token refresh failed: ${error}`);
  }

  const stravaTokens: StravaTokens = await response.json();

  // Update stored tokens
  const token: OAuthToken = {
    accessToken: stravaTokens.access_token,
    refreshToken: stravaTokens.refresh_token,
    expiresAt: stravaTokens.expires_at,
    athlete: stravaTokens.athlete,
  };

  await storeTokens(env.STRAVA_TOKENS, userId, token);

  return token;
}

/**
 * Get valid access token (refresh if needed)
 */
export async function getValidAccessToken(
  env: Env,
  userId: string
): Promise<string> {
  const tokens = await getTokens(env.STRAVA_TOKENS, userId);
  
  if (!tokens) {
    throw new Error('No tokens found for user');
  }

  // Check if token is expired (with 5 minute buffer)
  const now = Math.floor(Date.now() / 1000);
  if (now >= tokens.expiresAt - 300) {
    // Token expired or about to expire, refresh it
    const newTokens = await refreshAccessToken(env, userId, tokens.refreshToken);
    return newTokens.accessToken;
  }

  return tokens.accessToken;
}
