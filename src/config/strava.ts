export const STRAVA_CONFIG = {
  WORKER_URL: import.meta.env.VITE_STRAVA_WORKER_URL || 'http://localhost:8787',
  AUTH_STORAGE_KEY: 'trainerLoop.stravaAuth.v1',
  POPUP_WIDTH: 500,
  POPUP_HEIGHT: 600,
};
