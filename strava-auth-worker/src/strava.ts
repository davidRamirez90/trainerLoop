import type { UploadRequest, UploadResponse, UploadStatus } from './types';

const STRAVA_API_BASE = 'https://www.strava.com/api/v3';

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
 * Upload activity to Strava
 */
export async function uploadActivity(
  accessToken: string,
  uploadData: UploadRequest
): Promise<UploadResponse> {
  log('info', 'Starting Strava upload', {
    name: uploadData.name,
    fileDataLength: uploadData.fileData.length,
    sportType: uploadData.sportType,
    deviceName: uploadData.deviceName,
  });

  // Decode base64 to binary
  log('info', 'Decoding base64 file data', {
    inputLength: uploadData.fileData.length,
  });

  const binaryString = atob(uploadData.fileData);
  const bytes = new Uint8Array(binaryString.length);
  for (let i = 0; i < binaryString.length; i++) {
    bytes[i] = binaryString.charCodeAt(i);
  }

  log('info', 'Base64 decoded', {
    inputLength: uploadData.fileData.length,
    outputLength: bytes.length,
  });

  // Build multipart form data
  const boundary = '----FormBoundary' + Math.random().toString(36).substring(2);
  log('info', 'Building multipart form data', {
    boundary: boundary.substring(0, 20) + '...',
    fileSize: bytes.length,
  });

  const formData = buildMultipartFormData(boundary, uploadData, bytes);

  log('info', 'Sending request to Strava API', {
    endpoint: '/uploads',
    formDataSize: formData.length,
    accessTokenPrefix: accessToken.substring(0, 8) + '...[REDACTED]',
  });

  const response = await fetch(`${STRAVA_API_BASE}/uploads`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${accessToken}`,
      'Content-Type': `multipart/form-data; boundary=${boundary}`,
    },
    body: formData,
  });

  log('info', 'Strava API response received', {
    status: response.status,
    statusText: response.statusText,
    ok: response.ok,
  });

  if (!response.ok) {
    const error = await response.text();
    log('error', 'Strava upload failed', {
      status: response.status,
      statusText: response.statusText,
      error: error.substring(0, 500),
    });
    throw new Error(`Upload failed: ${error}`);
  }

  const result = await response.json() as UploadResponse;
  log('info', 'Strava upload successful', {
    uploadId: result.id,
    uploadIdStr: result.id_str,
    status: result.status,
    activityId: result.activity_id,
    error: result.error,
  });

  return result;
}

/**
 * Check upload status
 */
export async function checkUploadStatus(
  accessToken: string,
  uploadId: number
): Promise<UploadStatus> {
  log('info', 'Checking upload status with Strava', {
    uploadId,
    endpoint: `/uploads/${uploadId}`,
  });

  const response = await fetch(`${STRAVA_API_BASE}/uploads/${uploadId}`, {
    headers: {
      'Authorization': `Bearer ${accessToken}`,
    },
  });

  log('info', 'Strava status check response received', {
    uploadId,
    status: response.status,
    ok: response.ok,
  });

  if (!response.ok) {
    const error = await response.text();
    log('error', 'Strava status check failed', {
      uploadId,
      status: response.status,
      error: error.substring(0, 500),
    });
    throw new Error(`Status check failed: ${error}`);
  }

  const result = await response.json() as UploadStatus;
  log('info', 'Strava upload status retrieved', {
    uploadId,
    status: result.status,
    activityId: result.activity_id,
    error: result.error,
  });

  return result;
}

/**
 * Get athlete info
 */
export async function getAthlete(accessToken: string) {
  log('info', 'Fetching athlete info from Strava', {
    endpoint: '/athlete',
  });

  const response = await fetch(`${STRAVA_API_BASE}/athlete`, {
    headers: {
      'Authorization': `Bearer ${accessToken}`,
    },
  });

  log('info', 'Strava athlete endpoint response received', {
    status: response.status,
    ok: response.ok,
  });

  if (!response.ok) {
    const error = await response.text();
    log('error', 'Failed to get athlete from Strava', {
      status: response.status,
      error: error.substring(0, 500),
    });
    throw new Error(`Failed to get athlete: ${error}`);
  }

  const result = await response.json() as { id: number; firstname: string; lastname: string };
  log('info', 'Athlete info retrieved from Strava', {
    athleteId: result.id,
    athleteName: `${result.firstname} ${result.lastname}`,
  });

  return result;
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
