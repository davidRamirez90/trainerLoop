/**
 * @jest-environment jsdom
 */

import {
  buildSessionSummary,
  loadSessionsFromStorage,
  saveSessionsToStorage,
  addSessionToStorage,
  clearSessionsFromStorage,
  deleteSessionFromStorage,
  updateSessionNotesInStorage,
  type SessionData,
} from '../utils/sessionStorage';
import type { TelemetrySample } from '../types';

const createMockSamples = (count: number): TelemetrySample[] => {
  const samples: TelemetrySample[] = [];
  for (let i = 0; i < count; i++) {
    samples.push({
      timeSec: i,
      powerWatts: 100 + (i % 50),
      cadenceRpm: 80 + (i % 20),
      hrBpm: 140 + (i % 30),
    });
  }
  return samples;
};

const createMockSession = (
  id: string,
  completed: boolean = true
): SessionData => ({
  id,
  date: new Date().toISOString(),
  workoutType: 'Test Workout',
  durationSec: 3600,
  avgPower: 150,
  maxPower: 250,
  avgCadence: 85,
  avgHr: 155,
  coachNotes: '',
  coachProfileId: 'tempo-traditionalist',
  coachEvents: [],
  completed,
  startTimeMs: Date.now() - 3600000,
  endTimeMs: Date.now(),
  samples: createMockSamples(100),
});

describe('sessionStorage', () => {
  beforeEach(() => {
    clearSessionsFromStorage();
  });

  afterEach(() => {
    clearSessionsFromStorage();
  });

  describe('buildSessionSummary', () => {
    it('should create a session summary from provided data', () => {
      const samples = createMockSamples(10);
      const startTimeMs = Date.now() - 1000;
      const endTimeMs = Date.now();

      const summary = buildSessionSummary(
        startTimeMs,
        endTimeMs,
        60,
        samples,
        'Test Workout',
        true,
        'Great session!'
      );

      expect(summary.id).toBe(`${startTimeMs}-${endTimeMs}`);
      expect(summary.workoutType).toBe('Test Workout');
      expect(summary.durationSec).toBe(60);
      expect(summary.avgPower).toBeGreaterThan(0);
      expect(summary.maxPower).toBeGreaterThan(0);
      expect(summary.completed).toBe(true);
      expect(summary.coachNotes).toBe('Great session!');
    });

    it('should calculate average power correctly', () => {
      const samples: TelemetrySample[] = [
        { timeSec: 0, powerWatts: 100, cadenceRpm: 80, hrBpm: 140 },
        { timeSec: 1, powerWatts: 200, cadenceRpm: 90, hrBpm: 150 },
        { timeSec: 2, powerWatts: 150, cadenceRpm: 85, hrBpm: 145 },
      ];

      const summary = buildSessionSummary(
        Date.now(),
        Date.now(),
        10,
        samples,
        'Test',
        true
      );

      // (100 + 200 + 150) / 3 = 150
      expect(summary.avgPower).toBe(150);
    });

    it('should handle empty samples array', () => {
      const summary = buildSessionSummary(
        Date.now(),
        Date.now(),
        0,
        [],
        'Empty Workout',
        false
      );

      expect(summary.avgPower).toBe(0);
      expect(summary.maxPower).toBe(0);
      expect(summary.avgCadence).toBe(0);
      expect(summary.avgHr).toBe(0);
    });
  });

  describe('loadSessionsFromStorage', () => {
    it('should return empty array when no sessions stored', () => {
      const sessions = loadSessionsFromStorage();
      expect(sessions).toEqual([]);
    });

    it('should return stored sessions', () => {
      const session1 = createMockSession('session-1');
      const session2 = createMockSession('session-2');

      saveSessionsToStorage([session1, session2]);

      const sessions = loadSessionsFromStorage();
      expect(sessions).toHaveLength(2);
      expect(sessions[0].id).toBe('session-1');
      expect(sessions[1].id).toBe('session-2');
    });

    it('should return empty array for invalid JSON', () => {
      if (typeof window !== 'undefined') {
        window.localStorage.setItem('trainerLoop.sessions.v1', 'invalid json');
      }

      const sessions = loadSessionsFromStorage();
      expect(sessions).toEqual([]);
    });

    it('should return empty array for non-array data', () => {
      if (typeof window !== 'undefined') {
        window.localStorage.setItem('trainerLoop.sessions.v1', JSON.stringify({ foo: 'bar' }));
      }

      const sessions = loadSessionsFromStorage();
      expect(sessions).toEqual([]);
    });
  });

  describe('saveSessionsToStorage', () => {
    it('should save sessions to localStorage', () => {
      const session1 = createMockSession('test-1');
      const session2 = createMockSession('test-2');

      saveSessionsToStorage([session1, session2]);

      const sessions = loadSessionsFromStorage();
      expect(sessions).toHaveLength(2);
      expect(sessions[0].id).toBe('test-1');
    });
  });

  describe('addSessionToStorage', () => {
    it('should add session to storage', () => {
      const session = createMockSession('new-session');

      addSessionToStorage(session);

      const sessions = loadSessionsFromStorage();
      expect(sessions).toHaveLength(1);
      expect(sessions[0].id).toBe('new-session');
    });

    it('should add session at beginning of list', () => {
      const session1 = createMockSession('session-1');
      const session2 = createMockSession('session-2');

      saveSessionsToStorage([session1]);
      addSessionToStorage(session2);

      const sessions = loadSessionsFromStorage();
      expect(sessions[0].id).toBe('session-2');
      expect(sessions[1].id).toBe('session-1');
    });

    it('should replace existing session with same id', () => {
      const session1 = createMockSession('duplicate-id');
      const session2 = { ...createMockSession('duplicate-id'), avgPower: 999 };

      addSessionToStorage(session1);
      addSessionToStorage(session2);

      const sessions = loadSessionsFromStorage();
      expect(sessions).toHaveLength(1);
      expect(sessions[0].avgPower).toBe(999);
    });
  });

  describe('deleteSessionFromStorage', () => {
    it('should delete session by id', () => {
      const session1 = createMockSession('delete-me');
      const session2 = createMockSession('keep-me');

      saveSessionsToStorage([session1, session2]);
      deleteSessionFromStorage('delete-me');

      const sessions = loadSessionsFromStorage();
      expect(sessions).toHaveLength(1);
      expect(sessions[0].id).toBe('keep-me');
    });

    it('should handle non-existent session id', () => {
      const session = createMockSession('existing');

      saveSessionsToStorage([session]);
      deleteSessionFromStorage('non-existent');

      const sessions = loadSessionsFromStorage();
      expect(sessions).toHaveLength(1);
    });
  });

  describe('updateSessionNotesInStorage', () => {
    it('should update coach notes for session', () => {
      const session = createMockSession('update-me');

      addSessionToStorage(session);
      updateSessionNotesInStorage('update-me', 'Updated notes');

      const sessions = loadSessionsFromStorage();
      expect(sessions[0].coachNotes).toBe('Updated notes');
    });

    it('should handle non-existent session', () => {
      updateSessionNotesInStorage('non-existent', 'Some notes');

      // Should not throw
      const sessions = loadSessionsFromStorage();
      expect(sessions).toEqual([]);
    });
  });

  describe('clearSessionsFromStorage', () => {
    it('should clear all sessions', () => {
      const session1 = createMockSession('session-1');
      const session2 = createMockSession('session-2');

      saveSessionsToStorage([session1, session2]);
      clearSessionsFromStorage();

      const sessions = loadSessionsFromStorage();
      expect(sessions).toEqual([]);
    });
  });
});
