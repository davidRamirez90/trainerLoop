import { useEffect, useMemo, useRef, useState } from 'react';

import { type WorkoutSegment } from '../data/workout';
import type { TelemetrySample } from '../types';
import { getTargetRangeAtTime, getTotalDurationSec } from '../utils/workout';

const MAX_SAMPLES = 1800;

const clamp = (value: number, min: number, max: number) =>
  Math.min(max, Math.max(min, value));

const seedNoise = (t: number, variance: number) => {
  const sin = Math.sin(t / 6) * variance;
  const cos = Math.cos(t / 11) * (variance * 0.6);
  return sin + cos;
};

export const useTelemetrySimulation = (
  segments: WorkoutSegment[],
  elapsedSec: number,
  isRunning: boolean,
  sessionId: number
) => {
  const totalDurationSec = useMemo(() => getTotalDurationSec(segments), [segments]);
  const [samples, setSamples] = useState<TelemetrySample[]>([]);
  const [isLive, setIsLive] = useState(false);
  const lastGeneratedRef = useRef<number | null>(null);

  useEffect(() => {
    setSamples([]);
    setIsLive(false);
    lastGeneratedRef.current = null;
  }, [segments, sessionId]);

  useEffect(() => {
    if (!isRunning) {
      setIsLive(false);
      return;
    }
    if (!segments.length) {
      setIsLive(false);
      return;
    }

    if (elapsedSec > totalDurationSec) {
      return;
    }

    if (lastGeneratedRef.current === elapsedSec) {
      return;
    }
    lastGeneratedRef.current = elapsedSec;

    const { segment, targetRange } = getTargetRangeAtTime(segments, elapsedSec);
    const { low: lowTarget, high: highTarget } = targetRange;
    const target = (lowTarget + highTarget) / 2;
    const variance = segment.isWork ? 12 : 7;
    const drift = seedNoise(elapsedSec, variance);
    const jitter = (Math.random() - 0.5) * variance * 2;

    const powerWatts = clamp(
      target + drift + jitter,
      lowTarget - 20,
      highTarget + 30
    );

    const hrBase = segment.isWork ? 162 : 136;
    const hrBpm = clamp(
      Math.round(hrBase + (powerWatts - target) * 0.2 + drift * 0.4),
      110,
      184
    );

    const cadenceBase = segment.isWork ? 92 : 86;
    const cadenceRpm = clamp(
      Math.round(cadenceBase + Math.sin(elapsedSec / 8) * 3),
      70,
      105
    );

    const nextSample: TelemetrySample = {
      timeSec: elapsedSec,
      powerWatts,
      cadenceRpm,
      hrBpm,
    };

    setSamples((prev) => {
      const trimmed = prev.length > MAX_SAMPLES ? prev.slice(-MAX_SAMPLES) : prev;
      return [...trimmed, nextSample];
    });

    setIsLive(true);
  }, [elapsedSec, isRunning, segments, totalDurationSec]);

  return {
    samples,
    isLive,
  };
};
