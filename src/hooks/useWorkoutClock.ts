import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import type { WorkoutSegment } from '../data/workout';
import { getTotalDurationSec } from '../utils/workout';

type WorkoutClock = {
  elapsedSec: number;
  activeSec: number;
  totalDurationSec: number;
  isRunning: boolean;
  isComplete: boolean;
  isSessionActive: boolean;
  sessionStartMs: number | null;
  sessionId: number;
  start: () => void;
  startSession: () => void;
  resume: () => void;
  pause: () => void;
  stop: () => void;
};

const TICK_MS = 500;

export const useWorkoutClock = (segments: WorkoutSegment[]): WorkoutClock => {
  const totalDurationSec = useMemo(() => getTotalDurationSec(segments), [segments]);
  const [elapsedSec, setElapsedSec] = useState(0);
  const [activeSec, setActiveSec] = useState(0);
  const [isRunning, setIsRunning] = useState(false);
  const [isComplete, setIsComplete] = useState(false);
  const [isSessionActive, setIsSessionActive] = useState(false);
  const [sessionId, setSessionId] = useState(0);
  const [sessionStartMs, setSessionStartMs] = useState<number | null>(null);

  const sessionStartRef = useRef<number | null>(null);
  const activeStartRef = useRef<number | null>(null);
  const accumulatedRef = useRef(0);

  const ensureSessionStart = useCallback(() => {
    if (sessionStartRef.current !== null) {
      return;
    }
    const now = Date.now();
    sessionStartRef.current = now;
    setSessionStartMs(now);
  }, []);

  useEffect(() => {
    if (!isSessionActive) {
      return undefined;
    }

    ensureSessionStart();
    const intervalId = window.setInterval(() => {
      if (sessionStartRef.current === null) {
        return;
      }
      const now = Date.now();
      const nextElapsed = Math.floor((now - sessionStartRef.current) / 1000);
      setElapsedSec(nextElapsed);
    }, TICK_MS);

    return () => window.clearInterval(intervalId);
  }, [isSessionActive]);

  useEffect(() => {
    if (!isRunning) {
      return undefined;
    }

    activeStartRef.current = Date.now();
    const intervalId = window.setInterval(() => {
      if (activeStartRef.current === null) {
        return;
      }
      const now = Date.now();
      const nextActive = Math.min(
        Math.floor((now - activeStartRef.current) / 1000) + accumulatedRef.current,
        totalDurationSec
      );
      setActiveSec(nextActive);
      if (nextActive >= totalDurationSec) {
        accumulatedRef.current = totalDurationSec;
        setIsRunning(false);
        setIsComplete(true);
      }
    }, TICK_MS);

    return () => window.clearInterval(intervalId);
  }, [isRunning, totalDurationSec]);

  const stop = useCallback(() => {
    setIsRunning(false);
    setIsSessionActive(false);
    setIsComplete(false);
    setElapsedSec(0);
    setActiveSec(0);
    accumulatedRef.current = 0;
    sessionStartRef.current = null;
    setSessionStartMs(null);
    activeStartRef.current = null;
    setSessionId((prev) => prev + 1);
  }, []);

  const start = useCallback(() => {
    if (isRunning) {
      return;
    }
    if (isComplete) {
      stop();
    }
    if (!isSessionActive) {
      setIsSessionActive(true);
      ensureSessionStart();
    }
    setIsRunning(true);
  }, [ensureSessionStart, isComplete, isRunning, isSessionActive, stop]);

  const startSession = useCallback(() => {
    if (isComplete) {
      stop();
    }
    if (isSessionActive) {
      return;
    }
    setIsSessionActive(true);
    ensureSessionStart();
  }, [ensureSessionStart, isComplete, isSessionActive, stop]);

  const resume = useCallback(() => {
    if (isRunning) {
      return;
    }
    if (!isSessionActive) {
      setIsSessionActive(true);
      ensureSessionStart();
    }
    setIsComplete(false);
    setIsRunning(true);
  }, [ensureSessionStart, isRunning, isSessionActive]);

  const pause = useCallback(() => {
    if (!isRunning) {
      return;
    }
    accumulatedRef.current = activeSec;
    setIsRunning(false);
  }, [activeSec, isRunning]);

  useEffect(() => {
    setActiveSec((prev) => Math.min(prev, totalDurationSec));
    accumulatedRef.current = Math.min(accumulatedRef.current, totalDurationSec);
  }, [totalDurationSec]);

  return {
    elapsedSec,
    activeSec,
    totalDurationSec,
    isRunning,
    isComplete,
    isSessionActive,
    sessionStartMs,
    sessionId,
    start,
    startSession,
    resume,
    pause,
    stop,
  };
};
