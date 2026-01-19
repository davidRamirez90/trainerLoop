import { useCallback, useEffect, useRef, useState } from 'react';

import type { TelemetrySample } from '../types';

const FTMS_SERVICE = 0x1826;
const INDOOR_BIKE_DATA = 0x2ad2;
const HEART_RATE_SERVICE = 0x180d;
const HEART_RATE_MEASUREMENT = 0x2a37;


type LatestTelemetry = {
  powerWatts: number | null;
  cadenceRpm: number | null;
  hrBpm: number | null;
};

type BluetoothTelemetryConfig = {
  trainerDevice: BluetoothDevice | null;
  hrDevice: BluetoothDevice | null;
  elapsedSec: number;
  isRecording: boolean;
  sessionId: number;
};

const parseIndoorBikeData = (data: DataView) => {
  if (data.byteLength < 2) {
    return null;
  }

  let offset = 0;
  const flags = data.getUint16(offset, true);
  offset += 2;

  const hasAverageSpeed = (flags & (1 << 1)) !== 0;
  const hasInstantCadence = (flags & (1 << 2)) !== 0;
  const hasAverageCadence = (flags & (1 << 3)) !== 0;
  const hasTotalDistance = (flags & (1 << 4)) !== 0;
  const hasResistanceLevel = (flags & (1 << 5)) !== 0;
  const hasInstantPower = (flags & (1 << 6)) !== 0;
  const hasAveragePower = (flags & (1 << 7)) !== 0;
  const hasExpendedEnergy = (flags & (1 << 8)) !== 0;
  const hasHeartRate = (flags & (1 << 9)) !== 0;
  const hasMet = (flags & (1 << 10)) !== 0;
  const hasElapsedTime = (flags & (1 << 11)) !== 0;
  const hasRemainingTime = (flags & (1 << 12)) !== 0;

  const ensure = (bytes: number) => offset + bytes <= data.byteLength;

  const readUint16 = () => {
    if (!ensure(2)) {
      return null;
    }
    const value = data.getUint16(offset, true);
    offset += 2;
    return value;
  };

  const readInt16 = () => {
    if (!ensure(2)) {
      return null;
    }
    const value = data.getInt16(offset, true);
    offset += 2;
    return value;
  };

  const skip = (bytes: number) => {
    if (!ensure(bytes)) {
      return false;
    }
    offset += bytes;
    return true;
  };

  if (!skip(2)) {
    return null;
  }

  if (hasAverageSpeed && !skip(2)) {
    return null;
  }

  let cadenceRpm: number | null = null;
  if (hasInstantCadence) {
    const cadenceRaw = readUint16();
    cadenceRpm = cadenceRaw === null ? null : cadenceRaw / 2;
  }

  if (hasAverageCadence && !skip(2)) {
    return null;
  }

  if (hasTotalDistance && !skip(3)) {
    return null;
  }

  if (hasResistanceLevel && !skip(2)) {
    return null;
  }

  let powerWatts: number | null = null;
  if (hasInstantPower) {
    powerWatts = readInt16();
  }

  if (hasAveragePower && !skip(2)) {
    return null;
  }

  if (hasExpendedEnergy && !skip(5)) {
    return null;
  }

  if (hasHeartRate && !skip(1)) {
    return null;
  }

  if (hasMet && !skip(1)) {
    return null;
  }

  if (hasElapsedTime && !skip(2)) {
    return null;
  }

  if (hasRemainingTime && !skip(2)) {
    return null;
  }

  return {
    cadenceRpm,
    powerWatts,
    hrBpm: null,
  };
};

const parseHeartRateMeasurement = (data: DataView) => {
  if (data.byteLength < 2) {
    return null;
  }
  const flags = data.getUint8(0);
  const isUint16 = (flags & 0x01) !== 0;
  if (isUint16) {
    if (data.byteLength < 3) {
      return null;
    }
    return data.getUint16(1, true);
  }
  return data.getUint8(1);
};

export const useBluetoothTelemetry = ({
  trainerDevice,
  hrDevice,
  elapsedSec,
  isRecording,
  sessionId,
}: BluetoothTelemetryConfig) => {
  const [samples, setSamples] = useState<TelemetrySample[]>([]);
  const [isActive, setIsActive] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [latest, setLatest] = useState<LatestTelemetry>({
    powerWatts: null,
    cadenceRpm: null,
    hrBpm: null,
  });
  const hrDeviceRef = useRef<BluetoothDevice | null>(hrDevice);
  const elapsedRef = useRef(elapsedSec);
  const isRecordingRef = useRef(isRecording);
  const lastValuesRef = useRef<LatestTelemetry>({
    powerWatts: null,
    cadenceRpm: null,
    hrBpm: null,
  });

  useEffect(() => {
    hrDeviceRef.current = hrDevice;
  }, [hrDevice]);

  useEffect(() => {
    elapsedRef.current = elapsedSec;
  }, [elapsedSec]);

  useEffect(() => {
    isRecordingRef.current = isRecording;
  }, [isRecording]);

  useEffect(() => {
    const timeoutId = setTimeout(() => {
      setSamples([]);
      lastValuesRef.current = { powerWatts: null, cadenceRpm: null, hrBpm: null };
      setLatest({ powerWatts: null, cadenceRpm: null, hrBpm: null });
    }, 0);
    return () => clearTimeout(timeoutId);
  }, [sessionId]);

  const updateLatest = useCallback((updates: Partial<LatestTelemetry>) => {
    const next = { ...lastValuesRef.current, ...updates };
    lastValuesRef.current = next;
    setLatest(next);
    return next;
  }, []);

  const pushSample = useCallback((updates: Partial<LatestTelemetry>) => {
    const next = updateLatest(updates);

    if (!isRecordingRef.current) {
      return;
    }

    const hasAny =
      next.powerWatts !== null || next.cadenceRpm !== null || next.hrBpm !== null;
    if (!hasAny) {
      return;
    }

    const power = next.powerWatts ?? 0;
    const cadence = next.cadenceRpm ?? 0;
    const hr = next.hrBpm ?? 0;

    const nextSample: TelemetrySample = {
      timeSec: elapsedRef.current,
      powerWatts: power,
      cadenceRpm: cadence,
      hrBpm: hr,
    };

    setSamples((prevSamples) => [...prevSamples, nextSample]);
  }, [updateLatest]);

  useEffect(() => {
    if (!trainerDevice) {
      const timeoutId = setTimeout(() => {
        setIsActive(false);
        setError(null);
        setLatest((prev) =>
          hrDeviceRef.current
            ? { ...prev, powerWatts: null, cadenceRpm: null }
            : { powerWatts: null, cadenceRpm: null, hrBpm: null }
        );
      }, 0);
      return () => clearTimeout(timeoutId);
    }

    let active = true;
    let characteristic: BluetoothRemoteGATTCharacteristic | null = null;

    const timeoutId = setTimeout(() => {
      setError(null);
      setSamples([]);
      lastValuesRef.current = { powerWatts: null, cadenceRpm: null, hrBpm: null };
      setLatest({ powerWatts: null, cadenceRpm: null, hrBpm: null });
    }, 0);

    const handleIndoorBikeData = (event: Event) => {
      const target = event.target as BluetoothRemoteGATTCharacteristic | null;
      const value = target?.value;
      if (!value) {
        return;
      }
      const parsed = parseIndoorBikeData(value);
      if (!parsed) {
        return;
      }
      const updates: Partial<LatestTelemetry> = {
        powerWatts: parsed.powerWatts,
        cadenceRpm: parsed.cadenceRpm,
      };
      if (!hrDeviceRef.current) {
        updates.hrBpm = null;
      }
      if (!isRecordingRef.current) {
        updateLatest(updates);
        return;
      }
      pushSample(updates);
    };

    const startNotifications = async () => {
      if (!trainerDevice) {
        throw new Error('Trainer device not available.');
      }
      const server =
        trainerDevice.gatt?.connected
          ? trainerDevice.gatt
          : await trainerDevice.gatt?.connect();
      if (!server) {
        throw new Error('Trainer connection unavailable.');
      }
      const service = await server.getPrimaryService(FTMS_SERVICE);
      characteristic = await service.getCharacteristic(INDOOR_BIKE_DATA);
      characteristic.addEventListener('characteristicvaluechanged', handleIndoorBikeData);
      await characteristic.startNotifications();
      if (active) {
        setIsActive(true);
      }
    };

    startNotifications().catch((err) => {
      if (!active) {
        return;
      }
      const message = err instanceof Error ? err.message : 'Trainer telemetry failed.';
      setError(message);
      setIsActive(false);
    });

    return () => {
      active = false;
      clearTimeout(timeoutId);
      setIsActive(false);
      if (characteristic) {
        characteristic.removeEventListener('characteristicvaluechanged', handleIndoorBikeData);
        characteristic.stopNotifications().catch(() => undefined);
      }
    };
  }, [pushSample, trainerDevice, updateLatest]);

  useEffect(() => {
    if (!hrDevice) {
      return undefined;
    }

    let active = true;
    let characteristic: BluetoothRemoteGATTCharacteristic | null = null;

    const handleHeartRate = (event: Event) => {
      const target = event.target as BluetoothRemoteGATTCharacteristic | null;
      const value = target?.value;
      if (!value) {
        return;
      }
      const hr = parseHeartRateMeasurement(value);
      if (hr === null) {
        return;
      }
      if (isRecordingRef.current) {
        pushSample({ hrBpm: hr });
      } else {
        updateLatest({ hrBpm: hr });
      }
    };

    const startNotifications = async () => {
      const server =
        hrDevice.gatt?.connected ? hrDevice.gatt : await hrDevice.gatt?.connect();
      if (!server) {
        throw new Error('HR sensor connection unavailable.');
      }
      const service = await server.getPrimaryService(HEART_RATE_SERVICE);
      characteristic = await service.getCharacteristic(HEART_RATE_MEASUREMENT);
      characteristic.addEventListener('characteristicvaluechanged', handleHeartRate);
      await characteristic.startNotifications();
    };

    startNotifications().catch((err) => {
      if (!active) {
        return;
      }
      const message = err instanceof Error ? err.message : 'HR telemetry failed.';
      setError(message);
    });

    return () => {
      active = false;
      if (characteristic) {
        characteristic.removeEventListener('characteristicvaluechanged', handleHeartRate);
        characteristic.stopNotifications().catch(() => undefined);
      }
    };
  }, [hrDevice, pushSample, updateLatest]);

  return {
    samples,
    isActive,
    error,
    latest,
  };
};
