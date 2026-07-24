import { describe, expect, it } from 'vitest';

import { getScenarioTelemetry, getSimulationScenarioLabel } from '../hooks/useTelemetrySimulation';

describe('telemetry simulation scenarios', () => {
  it('generates fatigue with lower cadence and higher heart rate over time', () => {
    const early = getScenarioTelemetry({
      scenario: 'fatigue',
      elapsedSec: 30,
      targetWatts: 220,
      isRecovery: false,
    });
    const late = getScenarioTelemetry({
      scenario: 'fatigue',
      elapsedSec: 240,
      targetWatts: 220,
      isRecovery: false,
    });

    expect(late.cadenceRpm).toBeLessThan(early.cadenceRpm);
    expect(late.hrBpm).toBeGreaterThan(early.hrBpm);
    expect(late.powerWatts).toBeLessThan(early.powerWatts);
  });

  it('labels scenario presets for the development UI', () => {
    expect(getSimulationScenarioLabel('dropouts')).toBe('Power/cadence dropouts');
  });
});
