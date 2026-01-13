import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import type { WorkoutSegment } from '../data/workout';
import { getTotalDurationSec } from '../utils/workout';

type WorkoutClock = {
  elapsedSec: number;
  totalDurationSec: number;
  isRunning: boolean;
  isComplete: boolean;
  sessionId: number;
  start: () => void;
  pause: () => void;
  stop: () => void;
};

const TICK_MS = 500;

export const useWorkoutClock = (segments: WorkoutSegment[]): WorkoutClock => {
  const totalDurationSec = useMemo(() => getTotalDurationSec(segments), [segments]);
  const [elapsedSec, setElapsedSec] = useState(0);
  const [isRunning, setIsRunning] = useState(false);
  const [isComplete, setIsComplete] = useState(false);
  const [sessionId, setSessionId] = useState(0);

  const startRef = useRef<number | null>(null);
  const accumulatedRef = useRef(0);

  useEffect(() => {
    if (!isRunning) {
      return undefined;
    }

    startRef.current = Date.now();
    const intervalId = window.setInterval(() => {
      if (startRef.current === null) {
        return;
      }
      const now = Date.now();
      const nextElapsed = Math.min(
        Math.floor((now - startRef.current) / 1000) + accumulatedRef.current,
        totalDurationSec
      );
      setElapsedSec(nextElapsed);
      if (nextElapsed >= totalDurationSec) {
        accumulatedRef.current = totalDurationSec;
        setIsRunning(false);
        setIsComplete(true);
      }
    }, TICK_MS);

    return () => window.clearInterval(intervalId);
  }, [isRunning, totalDurationSec]);

  const stop = useCallback(() => {
    setIsRunning(false);
    setIsComplete(false);
    setElapsedSec(0);
    accumulatedRef.current = 0;
    startRef.current = null;
    setSessionId((prev) => prev + 1);
  }, []);

  const start = useCallback(() => {
    if (isRunning) {
      return;
    }
    if (isComplete) {
      stop();
    }
    setIsRunning(true);
  }, [isComplete, isRunning, stop]);

  const pause = useCallback(() => {
    if (!isRunning) {
      return;
    }
    accumulatedRef.current = elapsedSec;
    setIsRunning(false);
  }, [elapsedSec, isRunning]);

  useEffect(() => {
    setElapsedSec((prev) => Math.min(prev, totalDurationSec));
  }, [totalDurationSec]);

  return {
    elapsedSec,
    totalDurationSec,
    isRunning,
    isComplete,
    sessionId,
    start,
    pause,
    stop,
  };
};
