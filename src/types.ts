export type TelemetrySample = {
  timeSec: number;
  powerWatts: number;
  cadenceRpm: number;
  hrBpm: number;
  dropout?: boolean;
  lagCompensated?: boolean;
};

export type TelemetryGap = {
  startSec: number;
  endSec: number;
  kind: 'dropout';
};
