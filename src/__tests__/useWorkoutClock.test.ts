import { renderHook, act } from '@testing-library/react';

import type { WorkoutSegment } from '../data/workout';
import { useWorkoutClock } from '../hooks/useWorkoutClock';

describe('useWorkoutClock', () => {
  const segments: WorkoutSegment[] = [
    {
      id: 'warmup',
      label: 'Warmup',
      durationSec: 5,
      targetRange: { low: 100, high: 120 },
      phase: 'warmup',
      isWork: false,
    },
  ];

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2024-01-01T00:00:00Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('advances elapsed and active seconds when running', () => {
    const { result } = renderHook(() => useWorkoutClock(segments));

    act(() => {
      result.current.startSession();
      result.current.start();
    });

    act(() => {
      vi.advanceTimersByTime(1500);
    });

    expect(result.current.elapsedSec).toBeGreaterThan(0);
    expect(result.current.activeSec).toBeGreaterThan(0);
  });

  it('resets state on stop', () => {
    const { result } = renderHook(() => useWorkoutClock(segments));

    act(() => {
      result.current.start();
    });

    act(() => {
      vi.advanceTimersByTime(1000);
      result.current.stop();
    });

    expect(result.current.elapsedSec).toBe(0);
    expect(result.current.activeSec).toBe(0);
    expect(result.current.isRunning).toBe(false);
    expect(result.current.isSessionActive).toBe(false);
  });
});
