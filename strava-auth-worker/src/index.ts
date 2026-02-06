import type { Env } from './types';
import { initiateAuth, handleCallback, getTokens, getValidAccessToken, deleteTokens } from './oauth';
import { uploadActivity, checkUploadStatus, getAthlete } from './strava';

// CORS headers
function getCorsHeaders(origin: string): Record<string, string> {
  return {
    'Access-Control-Allow-Origin': origin,
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
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
    
    // Handle CORS preflight
    if (request.method === 'OPTIONS') {
      return new Response(null, {
        status: 204,
        headers: getCorsHeaders(corsOrigin),
      });
    }

    try {
      // Health check
      if (url.pathname === '/health') {
        return jsonResponse({ status: 'ok', version: '1.0.0' }, 200, corsOrigin);
      }

      // Initiate OAuth flow
      if (url.pathname === '/auth/initiate' && request.method === 'GET') {
        const redirectUri = url.searchParams.get('redirect_uri');
        if (!redirectUri) {
          return errorResponse('Missing redirect_uri parameter', 400, corsOrigin);
        }

        const userId = getUserId(request);
        const { authUrl, state } = await initiateAuth(env, redirectUri, userId);
        
        return jsonResponse({ 
          success: true, 
          data: { authUrl, state } 
        }, 200, corsOrigin);
      }

      // OAuth callback
      if (url.pathname === '/auth/callback' && request.method === 'GET') {
        const code = url.searchParams.get('code');
        const state = url.searchParams.get('state');
        const error = url.searchParams.get('error');

        if (error) {
          return errorResponse(`Strava authorization denied: ${error}`, 403, corsOrigin);
        }

        if (!code || !state) {
          return errorResponse('Missing code or state parameter', 400, corsOrigin);
        }

        const token = await handleCallback(env, code, state);
        
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
        const userId = getUserId(request);
        const tokens = await getTokens(env.STRAVA_TOKENS, userId);
        
        if (!tokens) {
          return jsonResponse({
            success: true,
            data: { authenticated: false },
          }, 200, corsOrigin);
        }

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
        const userId = getUserId(request);
        await deleteTokens(env.STRAVA_TOKENS, userId);
        
        return jsonResponse({
          success: true,
          data: { message: 'Logged out successfully' },
        }, 200, corsOrigin);
      }

      // Upload activity
      if (url.pathname === '/upload' && request.method === 'POST') {
        const userId = getUserId(request);
        
        // Get valid access token
        let accessToken: string;
        try {
          accessToken = await getValidAccessToken(env, userId);
        } catch (e) {
          return errorResponse('Not authenticated with Strava', 401, corsOrigin);
        }

        // Parse request body
        let body: { fileData?: string; name?: string; description?: string; sportType?: string; deviceName?: string };
        try {
          body = await request.json();
        } catch {
          return errorResponse('Invalid JSON body', 400, corsOrigin);
        }

        // Validate required fields
        if (!body.fileData || !body.name) {
          return errorResponse('Missing required fields: fileData, name', 400, corsOrigin);
        }

        // Upload to Strava
        const uploadResult = await uploadActivity(accessToken, {
          fileData: body.fileData,
          name: body.name,
          description: body.description || '',
          sportType: body.sportType || 'Ride',
          deviceName: body.deviceName || 'Trainer Loop',
        });

        return jsonResponse({
          success: true,
          data: uploadResult,
        }, 200, corsOrigin);
      }

      // Check upload status
      if (url.pathname.startsWith('/upload/status/') && request.method === 'GET') {
        const uploadId = parseInt(url.pathname.split('/').pop() || '');
        if (!uploadId) {
          return errorResponse('Invalid upload ID', 400, corsOrigin);
        }

        const userId = getUserId(request);
        
        let accessToken: string;
        try {
          accessToken = await getValidAccessToken(env, userId);
        } catch (e) {
          return errorResponse('Not authenticated with Strava', 401, corsOrigin);
        }

        const status = await checkUploadStatus(accessToken, uploadId);
        
        return jsonResponse({
          success: true,
          data: status,
        }, 200, corsOrigin);
      }

      // Get athlete info
      if (url.pathname === '/athlete' && request.method === 'GET') {
        const userId = getUserId(request);
        
        let accessToken: string;
        try {
          accessToken = await getValidAccessToken(env, userId);
        } catch (e) {
          return errorResponse('Not authenticated with Strava', 401, corsOrigin);
        }

        const athlete = await getAthlete(accessToken);
        
        return jsonResponse({
          success: true,
          data: athlete,
        }, 200, corsOrigin);
      }

      // 404 for unknown routes
      return errorResponse('Not found', 404, corsOrigin);

    } catch (error) {
      console.error('Worker error:', error);
      const message = error instanceof Error ? error.message : 'Internal server error';
      return errorResponse(message, 500, corsOrigin);
    }
  },
};
