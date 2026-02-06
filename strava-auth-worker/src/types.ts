// OAuth Types
export interface OAuthState {
  codeVerifier: string;
  redirectUri: string;
  userId: string;
  createdAt: number;
}

export interface OAuthToken {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
  athlete: StravaAthlete;
}

export interface StravaAthlete {
  id: number;
  firstname: string;
  lastname: string;
  profile: string;
}

export interface StravaTokens {
  token_type: string;
  access_token: string;
  expires_at: number;
  expires_in: number;
  refresh_token: string;
  athlete: StravaAthlete;
}

// Upload Types
export interface UploadRequest {
  fileData: string; // Base64 encoded FIT file
  name: string;
  description: string;
  sportType?: string;
  deviceName?: string;
}

export interface UploadResponse {
  id: number;
  id_str: string;
  external_id: string | null;
  error: string | null;
  status: string;
  activity_id: number | null;
}

export interface UploadStatus {
  id: number;
  status: string;
  activity_id: number | null;
  error: string | null;
}

// Environment
export interface Env {
  STRAVA_TOKENS: KVNamespace;
  STRAVA_CLIENT_ID: string;
  STRAVA_CLIENT_SECRET: string;
  ALLOWED_ORIGIN: string;
}

// API Response Types
export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: string;
}

export interface AuthStatus {
  authenticated: boolean;
  athlete?: {
    id: number;
    firstname: string;
    lastname: string;
    profile: string;
  };
}

export interface InitiateAuthResponse {
  authUrl: string;
  state: string;
}

// CORS Headers
export interface CorsHeaders {
  'Access-Control-Allow-Origin': string;
  'Access-Control-Allow-Methods': string;
  'Access-Control-Allow-Headers': string;
  'Access-Control-Max-Age': string;
}
