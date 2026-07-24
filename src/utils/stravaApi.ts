import { STRAVA_CONFIG } from '../config/strava';

export interface UploadActivityData {
  fileData: string; // Base64 encoded FIT file
  name: string;
  description: string;
  sportType?: string;
  deviceName?: string;
}

export interface UploadResult {
  id: number;
  status: string;
  activityId: number | null;
  error: string | null;
}

// Get user ID from localStorage
function getUserId(): string {
  const storageKey = 'trainerLoop.userId';
  let userId = localStorage.getItem(storageKey);
  if (!userId) {
    userId = `${Date.now()}-${Math.random().toString(36).substring(2, 15)}`;
    localStorage.setItem(storageKey, userId);
  }
  return userId;
}

/**
 * Upload activity to Strava via Cloudflare Worker
 */
export async function uploadActivityToStrava(
  data: UploadActivityData
): Promise<UploadResult> {
  console.log('[Strava] Starting upload to worker', {
    workerUrl: STRAVA_CONFIG.WORKER_URL,
    fileSize: data.fileData.length,
    name: data.name,
    sportType: data.sportType || 'Ride',
    deviceName: data.deviceName || 'Trainer Loop',
    timestamp: new Date().toISOString(),
  });

  const userId = getUserId();
  console.log('[Strava] Using userId:', userId.substring(0, 8) + '...');

  const response = await fetch(`${STRAVA_CONFIG.WORKER_URL}/upload`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-User-ID': userId,
    },
    body: JSON.stringify(data),
  });

  console.log('[Strava] Worker response received:', {
    status: response.status,
    statusText: response.statusText,
    ok: response.ok,
    timestamp: new Date().toISOString(),
  });

  if (!response.ok) {
    const error = await response.json();
    console.error('[Strava] Upload failed:', {
      status: response.status,
      error: error.error,
      timestamp: new Date().toISOString(),
    });
    throw new Error(error.error || 'Failed to upload to Strava');
  }

  const result = await response.json();
  console.log('[Strava] Upload successful:', {
    uploadId: result.data?.id,
    status: result.data?.status,
    timestamp: new Date().toISOString(),
  });
  return result.data;
}

/**
 * Check upload status
 */
export async function checkUploadStatus(uploadId: number): Promise<UploadResult> {
  console.log('[Strava] Checking upload status:', {
    uploadId,
    workerUrl: STRAVA_CONFIG.WORKER_URL,
    timestamp: new Date().toISOString(),
  });

  const response = await fetch(
    `${STRAVA_CONFIG.WORKER_URL}/upload/status/${uploadId}`,
    {
      headers: {
        'X-User-ID': getUserId(),
      },
    }
  );

  console.log('[Strava] Status check response:', {
    uploadId,
    status: response.status,
    ok: response.ok,
    timestamp: new Date().toISOString(),
  });

  if (!response.ok) {
    const error = await response.json();
    console.error('[Strava] Status check failed:', {
      uploadId,
      status: response.status,
      error: error.error,
      timestamp: new Date().toISOString(),
    });
    throw new Error(error.error || 'Failed to check upload status');
  }

  const result = await response.json();
  console.log('[Strava] Status received:', {
    uploadId,
    stravaStatus: result.data?.status,
    activityId: result.data?.activityId,
    error: result.data?.error,
    timestamp: new Date().toISOString(),
  });
  return result.data;
}

/**
 * Convert FIT file to base64
 */
export function fitFileToBase64(file: Uint8Array): string {
  console.log('[Strava] Converting FIT file to base64:', {
    inputSize: file.length,
    timestamp: new Date().toISOString(),
  });

  let binary = '';
  const bytes = new Uint8Array(file);
  const len = bytes.byteLength;
  for (let i = 0; i < len; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  const base64 = btoa(binary);

  console.log('[Strava] Base64 conversion complete:', {
    inputSize: file.length,
    outputSize: base64.length,
    timestamp: new Date().toISOString(),
  });

  return base64;
}
