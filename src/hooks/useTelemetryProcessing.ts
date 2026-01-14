import { useMemo } from 'react';

import type { TelemetryGap, TelemetrySample } from '../types';

const SMOOTHING_WINDOW = 5;
const DROPOUT_THRESHOLD_SEC = 3;
const LAG_COMPENSATION_MAX_SEC = 2;
const GAP_EPSILON_SEC = 0.03;

type TelemetryProcessingConfig = {
  samples: TelemetrySample[];
  elapsedSec: number;
  isRunning: boolean;
};

const smoothSamples = (samples: TelemetrySample[]) => {
  const smoothed: TelemetrySample[] = [];
  const window: TelemetrySample[] = [];
  let powerSum = 0;
  let cadenceSum = 0;
  let hrSum = 0;

  samples.forEach((sample) => {
    window.push(sample);
    powerSum += sample.powerWatts;
    cadenceSum += sample.cadenceRpm;
    hrSum += sample.hrBpm;

    if (window.length > SMOOTHING_WINDOW) {
      const removed = window.shift();
      if (removed) {
        powerSum -= removed.powerWatts;
        cadenceSum -= removed.cadenceRpm;
        hrSum -= removed.hrBpm;
      }
    }

    const size = window.length || 1;
    smoothed.push({
      timeSec: sample.timeSec,
      powerWatts: powerSum / size,
      cadenceRpm: cadenceSum / size,
      hrBpm: hrSum / size,
    });
  });

  return smoothed;
};

export const useTelemetryProcessing = ({
  samples,
  elapsedSec,
  isRunning,
}: TelemetryProcessingConfig) => {
  return useMemo(() => {
    if (!samples.length) {
      return { samples: [], gaps: [], latestSample: null };
    }

    const smoothed = smoothSamples(samples);
    const processed: TelemetrySample[] = [];
    const gaps: TelemetryGap[] = [];

    let previous: TelemetrySample | null = null;

    smoothed.forEach((sample) => {
      if (previous) {
        const delta = sample.timeSec - previous.timeSec;
        if (delta >= DROPOUT_THRESHOLD_SEC) {
          gaps.push({
            startSec: previous.timeSec,
            endSec: sample.timeSec,
            kind: 'dropout',
          });
          processed.push({
            ...previous,
            timeSec: previous.timeSec + GAP_EPSILON_SEC,
            dropout: true,
          });
        }
      }
      processed.push(sample);
      previous = sample;
    });

    let latestSample = smoothed[smoothed.length - 1];
    if (isRunning && latestSample) {
      const deltaToNow = elapsedSec - latestSample.timeSec;
      if (deltaToNow > 0 && deltaToNow <= LAG_COMPENSATION_MAX_SEC) {
        const lagSample = {
          ...latestSample,
          timeSec: elapsedSec,
          lagCompensated: true,
        };
        processed.push(lagSample);
        latestSample = lagSample;
      } else if (deltaToNow >= DROPOUT_THRESHOLD_SEC) {
        const gapEnd = Math.max(elapsedSec, latestSample.timeSec);
        gaps.push({
          startSec: latestSample.timeSec,
          endSec: gapEnd,
          kind: 'dropout',
        });
        processed.push({
          ...latestSample,
          timeSec: Math.min(latestSample.timeSec + GAP_EPSILON_SEC, gapEnd),
          dropout: true,
        });
      }
    }

    const latestNonDropout =
      [...processed].reverse().find((sample) => !sample.dropout) ?? null;

    return {
      samples: processed,
      gaps,
      latestSample: latestNonDropout,
    };
  }, [elapsedSec, isRunning, samples]);
};
