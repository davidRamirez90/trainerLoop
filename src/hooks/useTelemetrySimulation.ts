import { useEffect, useMemo, useRef, useState } from 'react';

import type { TelemetrySample } from '../types';

export type SimulationScenario = 'steady' | 'fatigue' | 'recovery' | 'dropouts';

type LatestSimulationTelemetry = {
  powerWatts: number | null;
  cadenceRpm: number | null;
  hrBpm: number | null;
};

type TelemetrySimulationConfig = {
  enabled: boolean;
  scenario: SimulationScenario;
  elapsedSec: number;
  isRecording: boolean;
  sessionId: number;
  targetWatts: number;
  isRecovery: boolean;
};

const SCENARIO_LABELS: Record<SimulationScenario, string> = {
  steady: 'Steady good interval',
  fatigue: 'Fatigue: low cadence + HR drift',
  recovery: 'Incomplete recovery HR',
  dropouts: 'Power/cadence dropouts',
};

const clamp = (value: number, min: number, max: number) =>
  Math.min(Math.max(value, min), max);

export const getScenarioTelemetry = ({
  scenario,
  elapsedSec,
  targetWatts,
  isRecovery,
}: Pick<
  TelemetrySimulationConfig,
  'scenario' | 'elapsedSec' | 'targetWatts' | 'isRecovery'
>): TelemetrySample => {
  const target = Math.max(70, Math.round(targetWatts || 150));
  const wave = Math.sin(elapsedSec / 8);
  const smallNoise = Math.round(wave * 4);

  if (scenario === 'fatigue') {
    const fatigueFactor = clamp((elapsedSec - 90) / 180, 0, 1);
    return {
      timeSec: elapsedSec,
      powerWatts: Math.round(target * (1 - fatigueFactor * 0.16)) + smallNoise,
      cadenceRpm: Math.round(88 - fatigueFactor * 26 + Math.sin(elapsedSec / 5) * 3),
      hrBpm: Math.round(136 + fatigueFactor * 34 + Math.sin(elapsedSec / 18) * 3),
    };
  }

  if (scenario === 'recovery') {
    const recoveryBoost = isRecovery ? 18 : 0;
    return {
      timeSec: elapsedSec,
      powerWatts: isRecovery ? Math.round(target * 0.9) : target + smallNoise,
      cadenceRpm: isRecovery ? 78 : 88 + Math.round(Math.sin(elapsedSec / 6) * 2),
      hrBpm: Math.round(146 + recoveryBoost + Math.sin(elapsedSec / 20) * 4),
    };
  }

  if (scenario === 'dropouts') {
    const isDropout = Math.floor(elapsedSec / 12) % 4 === 3;
    return {
      timeSec: elapsedSec,
      powerWatts: isDropout ? 0 : target + smallNoise,
      cadenceRpm: isDropout ? 0 : 86 + Math.round(Math.sin(elapsedSec / 4) * 3),
      hrBpm: 142 + Math.round(Math.sin(elapsedSec / 16) * 5),
      dropout: isDropout,
    };
  }

  return {
    timeSec: elapsedSec,
    powerWatts: target + smallNoise,
    cadenceRpm: 88 + Math.round(Math.sin(elapsedSec / 6) * 2),
    hrBpm: 138 + Math.round(Math.sin(elapsedSec / 20) * 3),
  };
};

export const getSimulationScenarioLabel = (scenario: SimulationScenario) =>
  SCENARIO_LABELS[scenario];

export const useTelemetrySimulation = ({
  enabled,
  scenario,
  elapsedSec,
  isRecording,
  sessionId,
  targetWatts,
  isRecovery,
}: TelemetrySimulationConfig) => {
  const [samples, setSamples] = useState<TelemetrySample[]>([]);
  const [latest, setLatest] = useState<LatestSimulationTelemetry>({
    powerWatts: null,
    cadenceRpm: null,
    hrBpm: null,
  });
  const lastRecordedSecRef = useRef<number | null>(null);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      setSamples([]);
      setLatest({ powerWatts: null, cadenceRpm: null, hrBpm: null });
      lastRecordedSecRef.current = null;
    }, 0);
    return () => window.clearTimeout(timeoutId);
  }, [sessionId, scenario, enabled]);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      if (!enabled) {
        setLatest({ powerWatts: null, cadenceRpm: null, hrBpm: null });
        return;
      }

      const sample = getScenarioTelemetry({
        scenario,
        elapsedSec,
        targetWatts,
        isRecovery,
      });
      setLatest({
        powerWatts: sample.powerWatts,
        cadenceRpm: sample.cadenceRpm,
        hrBpm: sample.hrBpm,
      });

      if (!isRecording) {
        return;
      }
      if (lastRecordedSecRef.current !== null && elapsedSec <= lastRecordedSecRef.current) {
        return;
      }
      lastRecordedSecRef.current = elapsedSec;
      setSamples((prev) => [...prev, sample]);
    }, 0);

    return () => window.clearTimeout(timeoutId);
  }, [elapsedSec, enabled, isRecording, isRecovery, scenario, targetWatts]);


  return useMemo(
    () => ({
      samples,
      latest,
      isActive: enabled,
      scenarioLabel: getSimulationScenarioLabel(scenario),
    }),
    [enabled, latest, samples, scenario]
  );
};
