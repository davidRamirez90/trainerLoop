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
  const response = await fetch(`${STRAVA_CONFIG.WORKER_URL}/upload`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-User-ID': getUserId(),
    },
    body: JSON.stringify(data),
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.error || 'Failed to upload to Strava');
  }

  const result = await response.json();
  return result.data;
}

/**
 * Check upload status
 */
export async function checkUploadStatus(uploadId: number): Promise<UploadResult> {
  const response = await fetch(
    `${STRAVA_CONFIG.WORKER_URL}/upload/status/${uploadId}`,
    {
      headers: {
        'X-User-ID': getUserId(),
      },
    }
  );

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.error || 'Failed to check upload status');
  }

  const result = await response.json();
  return result.data;
}

/**
 * Convert FIT file to base64
 */
export function fitFileToBase64(file: Uint8Array): string {
  let binary = '';
  const bytes = new Uint8Array(file);
  const len = bytes.byteLength;
  for (let i = 0; i < len; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}
