import type { UploadRequest, UploadResponse, UploadStatus } from './types';

const STRAVA_API_BASE = 'https://www.strava.com/api/v3';

/**
 * Upload activity to Strava
 */
export async function uploadActivity(
  accessToken: string,
  uploadData: UploadRequest
): Promise<UploadResponse> {
  // Decode base64 to binary
  const binaryString = atob(uploadData.fileData);
  const bytes = new Uint8Array(binaryString.length);
  for (let i = 0; i < binaryString.length; i++) {
    bytes[i] = binaryString.charCodeAt(i);
  }

  // Build multipart form data
  const boundary = '----FormBoundary' + Math.random().toString(36).substring(2);
  const formData = buildMultipartFormData(boundary, uploadData, bytes);

  const response = await fetch(`${STRAVA_API_BASE}/uploads`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${accessToken}`,
      'Content-Type': `multipart/form-data; boundary=${boundary}`,
    },
    body: formData,
  });

  if (!response.ok) {
    const error = await response.text();
    throw new Error(`Upload failed: ${error}`);
  }

  return response.json() as Promise<UploadResponse>;
}

/**
 * Check upload status
 */
export async function checkUploadStatus(
  accessToken: string,
  uploadId: number
): Promise<UploadStatus> {
  const response = await fetch(`${STRAVA_API_BASE}/uploads/${uploadId}`, {
    headers: {
      'Authorization': `Bearer ${accessToken}`,
    },
  });

  if (!response.ok) {
    const error = await response.text();
    throw new Error(`Status check failed: ${error}`);
  }

  return response.json() as Promise<UploadStatus>;
}

/**
 * Get athlete info
 */
export async function getAthlete(accessToken: string) {
  const response = await fetch(`${STRAVA_API_BASE}/athlete`, {
    headers: {
      'Authorization': `Bearer ${accessToken}`,
    },
  });

  if (!response.ok) {
    const error = await response.text();
    throw new Error(`Failed to get athlete: ${error}`);
  }

  return response.json();
}

/**
 * Build multipart form data manually
 */
function buildMultipartFormData(
  boundary: string,
  uploadData: UploadRequest,
  fileBytes: Uint8Array
): Uint8Array {
  const encoder = new TextEncoder();
  const parts: Uint8Array[] = [];

  // File field
  parts.push(encoder.encode(`--${boundary}\r\n`));
  parts.push(encoder.encode('Content-Disposition: form-data; name="file"; filename="activity.fit"\r\n'));
  parts.push(encoder.encode('Content-Type: application/octet-stream\r\n\r\n'));
  parts.push(fileBytes);
  parts.push(encoder.encode('\r\n'));

  // Name field
  parts.push(encoder.encode(`--${boundary}\r\n`));
  parts.push(encoder.encode('Content-Disposition: form-data; name="name"\r\n\r\n'));
  parts.push(encoder.encode(uploadData.name));
  parts.push(encoder.encode('\r\n'));

  // Description field
  parts.push(encoder.encode(`--${boundary}\r\n`));
  parts.push(encoder.encode('Content-Disposition: form-data; name="description"\r\n\r\n'));
  parts.push(encoder.encode(uploadData.description));
  parts.push(encoder.encode('\r\n'));

  // Data type field
  parts.push(encoder.encode(`--${boundary}\r\n`));
  parts.push(encoder.encode('Content-Disposition: form-data; name="data_type"\r\n\r\n'));
  parts.push(encoder.encode('fit'));
  parts.push(encoder.encode('\r\n'));

  // Sport type (optional)
  if (uploadData.sportType) {
    parts.push(encoder.encode(`--${boundary}\r\n`));
    parts.push(encoder.encode('Content-Disposition: form-data; name="sport_type"\r\n\r\n'));
    parts.push(encoder.encode(uploadData.sportType));
    parts.push(encoder.encode('\r\n'));
  }

  // Device name (optional)
  if (uploadData.deviceName) {
    parts.push(encoder.encode(`--${boundary}\r\n`));
    parts.push(encoder.encode('Content-Disposition: form-data; name="device_name"\r\n\r\n'));
    parts.push(encoder.encode(uploadData.deviceName));
    parts.push(encoder.encode('\r\n'));
  }

  // End boundary
  parts.push(encoder.encode(`--${boundary}--\r\n`));

  // Calculate total length
  const totalLength = parts.reduce((sum, part) => sum + part.length, 0);
  const result = new Uint8Array(totalLength);
  
  // Concatenate
  let offset = 0;
  for (const part of parts) {
    result.set(part, offset);
    offset += part.length;
  }

  return result;
}
