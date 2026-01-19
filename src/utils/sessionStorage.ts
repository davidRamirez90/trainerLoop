import type { TelemetrySample } from '../types';
import type { CoachEvent } from '../hooks/useCoach';

// Coach event for session notes
export interface SessionCoachEvent {
  id: string;
  timestamp: number;
  type: CoachEvent['type'];
  message: string;
  category: CoachEvent['category'];
  suggestionId?: string;
}

export interface SessionSummary {
  id: string;
  date: string;
  workoutType: string;
  durationSec: number;
  avgPower: number;
  maxPower: number;
  avgCadence: number;
  avgHr: number;
  coachNotes: string;
  coachProfileId: string;
  coachEvents: SessionCoachEvent[];
  completed: boolean;
}

export interface SessionData extends SessionSummary {
  startTimeMs: number;
  endTimeMs: number;
  samples: TelemetrySample[];
}

const SESSIONS_STORAGE_KEY = 'trainerLoop.sessions.v1';

const computeAverage = (
  samples: TelemetrySample[],
  selector: (sample: TelemetrySample) => number,
  include: (value: number) => boolean
): number | null => {
  let sum = 0;
  let count = 0;
  for (const sample of samples) {
    const value = selector(sample);
    if (!include(value)) {
      continue;
    }
    sum += value;
    count += 1;
  }
  if (count === 0) {
    return null;
  }
  return Math.round(sum / count);
};

const computeMax = (
  samples: TelemetrySample[],
  selector: (sample: TelemetrySample) => number,
  include: (value: number) => boolean
): number | null => {
  let maxValue: number | null = null;
  for (const sample of samples) {
    const value = selector(sample);
    if (!include(value)) {
      continue;
    }
    if (maxValue === null || value > maxValue) {
      maxValue = value;
    }
  }
  return maxValue;
};

export const buildSessionSummary = (
  startTimeMs: number,
  endTimeMs: number,
  durationSec: number,
  samples: TelemetrySample[],
  workoutName: string,
  completed: boolean,
  coachNotes: string = '',
  coachProfileId: string = '',
  coachEvents: SessionCoachEvent[] = []
): SessionSummary => {
  const avgPower = computeAverage(samples, (s) => s.powerWatts, () => true) ?? 0;
  const maxPower = computeMax(samples, (s) => s.powerWatts, () => true) ?? 0;
  const avgCadence = computeAverage(
    samples,
    (s) => s.cadenceRpm,
    (v) => v > 0
  ) ?? 0;
  const avgHr = computeAverage(samples, (s) => s.hrBpm, (v) => v > 0) ?? 0;

  return {
    id: `${startTimeMs}-${endTimeMs}`,
    date: new Date(startTimeMs).toISOString(),
    workoutType: workoutName,
    durationSec,
    avgPower,
    maxPower,
    avgCadence,
    avgHr,
    coachNotes,
    coachProfileId,
    coachEvents,
    completed,
  };
};

const isSessionData = (data: unknown): data is SessionData => {
  if (typeof data !== 'object' || data === null) {
    return false;
  }
  const obj = data as Record<string, unknown>;
  return (
    typeof obj.id === 'string' &&
    typeof obj.date === 'string' &&
    typeof obj.workoutType === 'string' &&
    typeof obj.durationSec === 'number' &&
    typeof obj.avgPower === 'number' &&
    typeof obj.completed === 'boolean' &&
    Array.isArray(obj.samples)
  );
};

export const loadSessionsFromStorage = (): SessionData[] => {
  if (typeof window === 'undefined') {
    return [];
  }
  try {
    const raw = window.localStorage.getItem(SESSIONS_STORAGE_KEY);
    if (!raw) {
      return [];
    }
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed.filter(isSessionData);
  } catch {
    return [];
  }
};

export const saveSessionsToStorage = (sessions: SessionData[]): void => {
  if (typeof window === 'undefined') {
    return;
  }
  try {
    window.localStorage.setItem(SESSIONS_STORAGE_KEY, JSON.stringify(sessions));
  } catch {
    // Ignore storage errors (quota or unavailable).
  }
};

export const addSessionToStorage = (session: SessionData): void => {
  const sessions = loadSessionsFromStorage();
  // Remove existing session with same id to avoid duplicates
  const filtered = sessions.filter((s) => s.id !== session.id);
  filtered.unshift(session); // Add new session at the beginning
  saveSessionsToStorage(filtered);
};

export const clearSessionsFromStorage = (): void => {
  if (typeof window === 'undefined') {
    return;
  }
  try {
    window.localStorage.removeItem(SESSIONS_STORAGE_KEY);
  } catch {
    // Ignore storage errors.
  }
};

export const deleteSessionFromStorage = (sessionId: string): void => {
  const sessions = loadSessionsFromStorage();
  const filtered = sessions.filter((s) => s.id !== sessionId);
  saveSessionsToStorage(filtered);
};

export const updateSessionNotesInStorage = (
  sessionId: string,
  coachNotes: string
): void => {
  const sessions = loadSessionsFromStorage();
  const updated = sessions.map((s) =>
    s.id === sessionId ? { ...s, coachNotes } : s
  );
  saveSessionsToStorage(updated);
};

// Add a coach event to a session
export const addCoachEventToSession = (
  sessionId: string,
  event: SessionCoachEvent
): void => {
  const sessions = loadSessionsFromStorage();
  const updated = sessions.map((s) =>
    s.id === sessionId
      ? { ...s, coachEvents: [...s.coachEvents, event] }
      : s
  );
  saveSessionsToStorage(updated);
};

// Update coach profile ID for a session
export const updateSessionCoachProfile = (
  sessionId: string,
  coachProfileId: string
): void => {
  const sessions = loadSessionsFromStorage();
  const updated = sessions.map((s) =>
    s.id === sessionId ? { ...s, coachProfileId } : s
  );
  saveSessionsToStorage(updated);
};

// Clear all coach events for a session
export const clearSessionCoachEvents = (sessionId: string): void => {
  const sessions = loadSessionsFromStorage();
  const updated = sessions.map((s) =>
    s.id === sessionId ? { ...s, coachEvents: [] } : s
  );
  saveSessionsToStorage(updated);
};
