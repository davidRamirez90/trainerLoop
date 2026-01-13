import { useEffect, useMemo, useRef, useState } from 'react';

import { type WorkoutSegment } from '../data/workout';
import type { TelemetrySample } from '../types';
import { getTargetRangeAtTime, getTotalDurationSec } from '../utils/workout';

const SAMPLE_INTERVAL_MS = 1000;
const MAX_SAMPLES = 1800;

const clamp = (value: number, min: number, max: number) =>
  Math.min(max, Math.max(min, value));

const seedNoise = (t: number, variance: number) => {
  const sin = Math.sin(t / 6) * variance;
  const cos = Math.cos(t / 11) * (variance * 0.6);
  return sin + cos;
};

export const useTelemetrySimulation = (segments: WorkoutSegment[]) => {
  const totalDurationSec = useMemo(() => getTotalDurationSec(segments), [segments]);
  const [samples, setSamples] = useState<TelemetrySample[]>([]);
  const [elapsedSec, setElapsedSec] = useState(0);
  const [isLive, setIsLive] = useState(true);
  const startRef = useRef<number | null>(null);

  useEffect(() => {
    startRef.current = Date.now();
    setSamples([]);
    setElapsedSec(0);
    setIsLive(true);

    const intervalId = window.setInterval(() => {
      if (!startRef.current) {
        return;
      }

      const now = Date.now();
      const nextElapsedSec = Math.min(
        Math.floor((now - startRef.current) / 1000),
        totalDurationSec
      );

      const { segment, targetRange } = getTargetRangeAtTime(
        segments,
        nextElapsedSec
      );
      const { low: lowTarget, high: highTarget } = targetRange;
      const target = (lowTarget + highTarget) / 2;
      const variance = segment.isWork ? 12 : 7;
      const drift = seedNoise(nextElapsedSec, variance);
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
        Math.round(cadenceBase + Math.sin(nextElapsedSec / 8) * 3),
        70,
        105
      );

      const nextSample: TelemetrySample = {
        timeSec: nextElapsedSec,
        powerWatts,
        cadenceRpm,
        hrBpm,
      };

      setElapsedSec(nextElapsedSec);
      setSamples((prev) => {
        const trimmed = prev.length > MAX_SAMPLES ? prev.slice(-MAX_SAMPLES) : prev;
        return [...trimmed, nextSample];
      });

      if (nextElapsedSec >= totalDurationSec) {
        setIsLive(false);
        window.clearInterval(intervalId);
      }
    }, SAMPLE_INTERVAL_MS);

    return () => window.clearInterval(intervalId);
  }, [segments, totalDurationSec]);

  return {
    samples,
    elapsedSec,
    totalDurationSec,
    isLive,
  };
};
