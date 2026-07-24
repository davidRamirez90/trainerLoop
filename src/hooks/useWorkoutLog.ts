import { useCallback, useEffect, useState } from 'react';

export type WorkoutLogEntry = {
  id: string;
  timestamp: string;
  level: 'info' | 'warning' | 'error';
  message: string;
  data?: Record<string, unknown>;
};

const LOG_STORAGE_KEY = 'trainerLoop.workoutLogs.v1';
const MAX_LOG_ENTRIES = 300;

const readStoredLogs = (): WorkoutLogEntry[] => {
  if (typeof window === 'undefined') {
    return [];
  }
  try {
    const raw = window.localStorage.getItem(LOG_STORAGE_KEY);
    return raw ? (JSON.parse(raw) as WorkoutLogEntry[]) : [];
  } catch {
    return [];
  }
};

const writeStoredLogs = (logs: WorkoutLogEntry[]) => {
  if (typeof window === 'undefined') {
    return;
  }
  window.localStorage.setItem(LOG_STORAGE_KEY, JSON.stringify(logs.slice(0, MAX_LOG_ENTRIES)));
};

export const useWorkoutLog = () => {
  const [logs, setLogs] = useState<WorkoutLogEntry[]>(() => readStoredLogs());

  useEffect(() => {
    writeStoredLogs(logs);
  }, [logs]);

  const addLog = useCallback(
    (level: WorkoutLogEntry['level'], message: string, data?: Record<string, unknown>) => {
      setLogs((prev) => [
        {
          id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
          timestamp: new Date().toISOString(),
          level,
          message,
          data,
        },
        ...prev,
      ].slice(0, MAX_LOG_ENTRIES));
    },
    []
  );

  const clearLogs = useCallback(() => {
    setLogs([]);
  }, []);

  return { logs, addLog, clearLogs };
};
