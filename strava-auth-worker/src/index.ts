import type { Env } from './types';
import { initiateAuth, handleCallback, getTokens, getValidAccessToken, deleteTokens } from './oauth';
import { uploadActivity, checkUploadStatus, getAthlete } from './strava';

// Logger helper for consistent logging
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

// CORS headers
function getCorsHeaders(origin: string): Record<string, string> {
  return {
    'Access-Control-Allow-Origin': origin,
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-User-ID',
    'Access-Control-Max-Age': '86400',
  };
}

// JSON response helper
function jsonResponse(data: unknown, status = 200, corsOrigin: string): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'Content-Type': 'application/json',
      ...getCorsHeaders(corsOrigin),
    },
  });
}

// Error response helper
function errorResponse(message: string, status = 400, corsOrigin: string): Response {
  return jsonResponse({ success: false, error: message }, status, corsOrigin);
}

// Extract user ID from request (using a simple header for now)
function getUserId(request: Request): string {
  // In production, you might want to use JWT or session cookies
  // For now, we'll use a custom header or generate from IP + user agent
  const userId = request.headers.get('X-User-ID');
  if (userId) return userId;
  
  // Fallback: generate from IP and user agent
  const ip = request.headers.get('CF-Connecting-IP') || 'unknown';
  const ua = request.headers.get('User-Agent') || 'unknown';
  return `${ip}-${ua}`.replace(/[^a-zA-Z0-9_-]/g, '').substring(0, 64);
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const corsOrigin = env.ALLOWED_ORIGIN || '*';
    const requestId = crypto.randomUUID();
    
    log('info', 'Request received', {
      requestId,
      method: request.method,
      path: url.pathname,
      userAgent: request.headers.get('User-Agent')?.substring(0, 50),
    });
    
    // Handle CORS preflight
    if (request.method === 'OPTIONS') {
      log('info', 'CORS preflight handled', { requestId });
      return new Response(null, {
        status: 204,
        headers: getCorsHeaders(corsOrigin),
      });
    }

    try {
      // Health check
      if (url.pathname === '/health') {
        log('info', 'Health check', { requestId });
        return jsonResponse({ status: 'ok', version: '1.0.0' }, 200, corsOrigin);
      }

      // Initiate OAuth flow
      if (url.pathname === '/auth/initiate' && request.method === 'GET') {
        log('info', 'OAuth initiate request', { requestId });
        const redirectUri = url.searchParams.get('redirect_uri');
        if (!redirectUri) {
          log('error', 'Missing redirect_uri parameter', { requestId });
          return errorResponse('Missing redirect_uri parameter', 400, corsOrigin);
        }

        const userId = getUserId(request);
        log('info', 'Initiating OAuth flow', { requestId, userId: userId.substring(0, 8) + '...' });
        
        const { authUrl, state } = await initiateAuth(env, redirectUri, userId);
        
        log('info', 'OAuth URL generated', { requestId, state: state.substring(0, 8) + '...' });
        return jsonResponse({ 
          success: true, 
          data: { authUrl, state } 
        }, 200, corsOrigin);
      }

      // OAuth callback
      if (url.pathname === '/auth/callback' && request.method === 'GET') {
        log('info', 'OAuth callback received', { requestId });
        const code = url.searchParams.get('code');
        const state = url.searchParams.get('state');
        const error = url.searchParams.get('error');

        if (error) {
          log('error', 'Strava authorization denied', { requestId, error });
          return errorResponse(`Strava authorization denied: ${error}`, 403, corsOrigin);
        }

        if (!code || !state) {
          log('error', 'Missing code or state parameter', { requestId, hasCode: !!code, hasState: !!state });
          return errorResponse('Missing code or state parameter', 400, corsOrigin);
        }

        log('info', 'Exchanging code for tokens', { requestId, state: state.substring(0, 8) + '...' });
        const token = await handleCallback(env, code, state);
        
        log('info', 'OAuth callback successful', { 
          requestId, 
          athleteId: token.athlete.id,
          athleteName: `${token.athlete.firstname} ${token.athlete.lastname}`,
        });
        
        return jsonResponse({
          success: true,
          data: {
            athlete: token.athlete,
            expiresAt: token.expiresAt,
          },
        }, 200, corsOrigin);
      }

      // Check auth status
      if (url.pathname === '/auth/status' && request.method === 'GET') {
        log('info', 'Auth status check', { requestId });
        const userId = getUserId(request);
        const tokens = await getTokens(env.STRAVA_TOKENS, userId);
        
        if (!tokens) {
          log('info', 'User not authenticated', { requestId, userId: userId.substring(0, 8) + '...' });
          return jsonResponse({
            success: true,
            data: { authenticated: false },
          }, 200, corsOrigin);
        }

        log('info', 'User authenticated', { 
          requestId, 
          userId: userId.substring(0, 8) + '...',
          athleteId: tokens.athlete.id,
        });

        return jsonResponse({
          success: true,
          data: {
            authenticated: true,
            athlete: {
              id: tokens.athlete.id,
              firstname: tokens.athlete.firstname,
              lastname: tokens.athlete.lastname,
              profile: tokens.athlete.profile,
            },
          },
        }, 200, corsOrigin);
      }

      // Logout / revoke
      if (url.pathname === '/auth/logout' && request.method === 'POST') {
        log('info', 'Logout request', { requestId });
        const userId = getUserId(request);
        await deleteTokens(env.STRAVA_TOKENS, userId);
        
        log('info', 'User logged out', { requestId, userId: userId.substring(0, 8) + '...' });
        return jsonResponse({
          success: true,
          data: { message: 'Logged out successfully' },
        }, 200, corsOrigin);
      }

      // Upload activity
      if (url.pathname === '/upload' && request.method === 'POST') {
        log('info', 'Upload activity request', { requestId });
        const userId = getUserId(request);
        
        // Get valid access token
        let accessToken: string;
        try {
          log('info', 'Getting valid access token', { requestId, userId: userId.substring(0, 8) + '...' });
          accessToken = await getValidAccessToken(env, userId);
          log('info', 'Access token obtained', { requestId, tokenPrefix: accessToken.substring(0, 8) + '...' });
        } catch (error) {
          log('error', 'Failed to get access token', { 
            requestId, 
            userId: userId.substring(0, 8) + '...',
            error: error instanceof Error ? error.message : 'Unknown error',
          });
          return errorResponse('Not authenticated with Strava', 401, corsOrigin);
        }

        // Parse request body
        let body: { fileData?: string; name?: string; description?: string; sportType?: string; deviceName?: string };
        try {
          body = await request.json();
          log('info', 'Request body parsed', { 
            requestId, 
            hasFileData: !!body.fileData,
            hasName: !!body.name,
            fileDataLength: body.fileData?.length,
            name: body.name,
            sportType: body.sportType,
          });
        } catch (error) {
          log('error', 'Failed to parse request body', { 
            requestId, 
            error: error instanceof Error ? error.message : 'Unknown error',
          });
          return errorResponse('Invalid JSON body', 400, corsOrigin);
        }

        // Validate required fields
        if (!body.fileData || !body.name) {
          log('error', 'Missing required fields', { 
            requestId, 
            hasFileData: !!body.fileData, 
            hasName: !!body.name,
          });
          return errorResponse('Missing required fields: fileData, name', 400, corsOrigin);
        }

        // Upload to Strava
        log('info', 'Uploading to Strava API', { 
          requestId, 
          name: body.name,
          fileDataLength: body.fileData.length,
          sportType: body.sportType || 'Ride',
        });
        
        const uploadResult = await uploadActivity(accessToken, {
          fileData: body.fileData,
          name: body.name,
          description: body.description || '',
          sportType: body.sportType || 'Ride',
          deviceName: body.deviceName || 'Trainer Loop',
        });

        log('info', 'Upload to Strava successful', { 
          requestId, 
          uploadId: uploadResult.id,
          status: uploadResult.status,
        });

        return jsonResponse({
          success: true,
          data: uploadResult,
        }, 200, corsOrigin);
      }

      // Check upload status
      if (url.pathname.startsWith('/upload/status/') && request.method === 'GET') {
        const uploadId = parseInt(url.pathname.split('/').pop() || '');
        log('info', 'Check upload status request', { requestId, uploadId });
        
        if (!uploadId) {
          log('error', 'Invalid upload ID', { requestId });
          return errorResponse('Invalid upload ID', 400, corsOrigin);
        }

        const userId = getUserId(request);
        
        let accessToken: string;
        try {
          accessToken = await getValidAccessToken(env, userId);
        } catch {
          log('error', 'Failed to get access token for status check', { 
            requestId, 
            userId: userId.substring(0, 8) + '...',
          });
          return errorResponse('Not authenticated with Strava', 401, corsOrigin);
        }

        const status = await checkUploadStatus(accessToken, uploadId);
        
        log('info', 'Upload status retrieved', { 
          requestId, 
          uploadId,
          status: status.status,
          activityId: status.activity_id,
          error: status.error,
        });
        
        return jsonResponse({
          success: true,
          data: status,
        }, 200, corsOrigin);
      }

      // Get athlete info
      if (url.pathname === '/athlete' && request.method === 'GET') {
        log('info', 'Get athlete info request', { requestId });
        const userId = getUserId(request);
        
        let accessToken: string;
        try {
          accessToken = await getValidAccessToken(env, userId);
        } catch {
          log('error', 'Failed to get access token for athlete info', { 
            requestId, 
            userId: userId.substring(0, 8) + '...',
          });
          return errorResponse('Not authenticated with Strava', 401, corsOrigin);
        }

        const athlete = await getAthlete(accessToken);
        
        const athleteData = athlete as { id: number; firstname: string; lastname: string };
        log('info', 'Athlete info retrieved', { 
          requestId, 
          athleteId: athleteData.id,
          athleteName: `${athleteData.firstname} ${athleteData.lastname}`,
        });
        
        return jsonResponse({
          success: true,
          data: athlete,
        }, 200, corsOrigin);
      }

      // 404 for unknown routes
      log('warn', 'Unknown route', { requestId, path: url.pathname });
      return errorResponse('Not found', 404, corsOrigin);

    } catch (error) {
      log('error', 'Worker error', { 
        requestId, 
        error: error instanceof Error ? error.message : 'Unknown error',
        stack: error instanceof Error ? error.stack : undefined,
      });
      const message = error instanceof Error ? error.message : 'Internal server error';
      return errorResponse(message, 500, corsOrigin);
    }
  },
};
