import { useCallback, useState, useEffect } from 'react';

import type { SessionData, SessionSummary, SessionCoachEvent } from '../utils/sessionStorage';
import {
  loadSessionsFromStorage,
  saveSessionsToStorage,
  addSessionToStorage,
  deleteSessionFromStorage,
  updateSessionNotesInStorage,
  addCoachEventToSession,
  updateSessionCoachProfile,
  clearSessionCoachEvents,
} from '../utils/sessionStorage';

const MAX_CACHED_SAMPLES = 1000;

export const useSessionHistory = () => {
  const [sessions, setSessions] = useState<SessionSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const load = () => {
      setIsLoading(true);
      try {
        const loaded = loadSessionsFromStorage();
        // Return summaries (without full samples to reduce memory)
        const summaries = loaded.map((session) => {
          const { samples, ...rest } = session;
          // Truncate samples for summary view if needed
          const truncatedSamples =
            samples.length > MAX_CACHED_SAMPLES
              ? samples.slice(-MAX_CACHED_SAMPLES)
              : samples;
          return { ...rest, samples: truncatedSamples } as SessionData;
        });
        setSessions(summaries);
      } catch {
        setSessions([]);
      } finally {
        setIsLoading(false);
      }
    };
    load();
  }, []);

  const addSession = useCallback((session: SessionData) => {
    addSessionToStorage(session);
    setSessions((prev) => {
      const filtered = prev.filter((s) => s.id !== session.id);
      const { samples, ...summary } = session;
      const truncatedSamples =
        samples.length > MAX_CACHED_SAMPLES
          ? samples.slice(-MAX_CACHED_SAMPLES)
          : samples;
      return [{ ...summary, samples: truncatedSamples }, ...filtered];
    });
  }, []);

  const removeSession = useCallback((sessionId: string) => {
    deleteSessionFromStorage(sessionId);
    setSessions((prev) => prev.filter((s) => s.id !== sessionId));
  }, []);

  const updateNotes = useCallback((sessionId: string, coachNotes: string) => {
    updateSessionNotesInStorage(sessionId, coachNotes);
    setSessions((prev) =>
      prev.map((s) => (s.id === sessionId ? { ...s, coachNotes } : s))
    );
  }, []);

  const clearAll = useCallback(() => {
    saveSessionsToStorage([]);
    setSessions([]);
  }, []);

  const getSessionById = useCallback(
    (sessionId: string): SessionData | undefined => {
      const fullSessions = loadSessionsFromStorage();
      return fullSessions.find((s) => s.id === sessionId);
    },
    []
  );

  const addCoachEvent = useCallback((sessionId: string, event: SessionCoachEvent) => {
    addCoachEventToSession(sessionId, event);
    setSessions((prev) =>
      prev.map((s) =>
        s.id === sessionId
          ? { ...s, coachEvents: [...s.coachEvents, event] }
          : s
      )
    );
  }, []);

  const updateCoachProfile = useCallback((sessionId: string, coachProfileId: string) => {
    updateSessionCoachProfile(sessionId, coachProfileId);
    setSessions((prev) =>
      prev.map((s) =>
        s.id === sessionId ? { ...s, coachProfileId } : s
      )
    );
  }, []);

  const clearCoachEvents = useCallback((sessionId: string) => {
    clearSessionCoachEvents(sessionId);
    setSessions((prev) =>
      prev.map((s) =>
        s.id === sessionId ? { ...s, coachEvents: [] } : s
      )
    );
  }, []);

  return {
    sessions,
    isLoading,
    addSession,
    removeSession,
    updateNotes,
    clearAll,
    getSessionById,
    addCoachEvent,
    updateCoachProfile,
    clearCoachEvents,
  };
};
