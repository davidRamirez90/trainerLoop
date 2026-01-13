import { useCallback, useEffect, useRef, useState } from 'react';

const FTMS_SERVICE = 0x1826;
const FITNESS_MACHINE_CONTROL_POINT = 0x2ad9;

const OPCODE_REQUEST_CONTROL = 0x00;
const OPCODE_SET_TARGET_POWER = 0x05;
const OPCODE_START_RESUME = 0x07;
const OPCODE_STOP_PAUSE = 0x08;
const RESPONSE_CODE = 0x80;
const RESULT_SUCCESS = 0x01;

const MAX_TARGET_WATTS = 2000;
const MIN_SEND_INTERVAL_MS = 900;

type FtmsControlStatus = 'idle' | 'requesting' | 'ready' | 'error';

type FtmsControlConfig = {
  trainerDevice: BluetoothDevice | null;
  targetWatts: number;
  isActive: boolean;
};

const clampTargetWatts = (value: number) =>
  Math.min(MAX_TARGET_WATTS, Math.max(0, Math.round(value)));

const buildRequestControl = () => new Uint8Array([OPCODE_REQUEST_CONTROL]);

const buildStartResume = () => new Uint8Array([OPCODE_START_RESUME]);

const buildStopPause = (mode: 'stop' | 'pause') =>
  new Uint8Array([OPCODE_STOP_PAUSE, mode === 'stop' ? 0x01 : 0x02]);

const buildTargetPower = (watts: number) => {
  const payload = new ArrayBuffer(3);
  const view = new DataView(payload);
  view.setUint8(0, OPCODE_SET_TARGET_POWER);
  view.setInt16(1, watts, true);
  return new Uint8Array(payload);
};

const writeControlPoint = async (
  controlPoint: BluetoothRemoteGATTCharacteristic,
  payload: Uint8Array
) => {
  if ('writeValueWithResponse' in controlPoint) {
    await controlPoint.writeValueWithResponse(payload);
    return;
  }
  await controlPoint.writeValue(payload);
};

export const useFtmsControl = ({
  trainerDevice,
  targetWatts,
  isActive,
}: FtmsControlConfig) => {
  const [status, setStatus] = useState<FtmsControlStatus>('idle');
  const [error, setError] = useState<string | null>(null);
  const controlPointRef = useRef<BluetoothRemoteGATTCharacteristic | null>(null);
  const hasControlRef = useRef(false);
  const lastSendRef = useRef({ time: 0, target: 0 });

  const sendCommand = useCallback(async (payload: Uint8Array, message: string) => {
    if (!hasControlRef.current || status !== 'ready') {
      return;
    }
    const controlPoint = controlPointRef.current;
    if (!controlPoint) {
      return;
    }
    try {
      await writeControlPoint(controlPoint, payload);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : message;
      setError(errorMessage);
    }
  }, [status]);

  const startWorkout = useCallback(() => {
    sendCommand(buildStartResume(), 'Trainer start failed.');
  }, [sendCommand]);

  const pauseWorkout = useCallback(() => {
    sendCommand(buildStopPause('pause'), 'Trainer pause failed.');
  }, [sendCommand]);

  const stopWorkout = useCallback(() => {
    sendCommand(buildStopPause('stop'), 'Trainer stop failed.');
  }, [sendCommand]);

  useEffect(() => {
    if (!trainerDevice) {
      setStatus('idle');
      setError(null);
      hasControlRef.current = false;
      controlPointRef.current = null;
      return undefined;
    }

    let active = true;
    let controlPoint: BluetoothRemoteGATTCharacteristic | null = null;

    setStatus('requesting');
    setError(null);
    hasControlRef.current = false;

    const handleResponse = (event: Event) => {
      const target = event.target as BluetoothRemoteGATTCharacteristic | null;
      const value = target?.value;
      if (!value || value.byteLength < 3) {
        return;
      }
      const opcode = value.getUint8(0);
      if (opcode !== RESPONSE_CODE) {
        return;
      }
      const requestOpcode = value.getUint8(1);
      const resultCode = value.getUint8(2);

      if (requestOpcode === OPCODE_REQUEST_CONTROL) {
        if (resultCode === RESULT_SUCCESS) {
          hasControlRef.current = true;
          setStatus('ready');
          setError(null);
        } else {
          hasControlRef.current = false;
          setStatus('error');
          setError('Trainer denied control.');
        }
        return;
      }

      if (requestOpcode === OPCODE_SET_TARGET_POWER && resultCode !== RESULT_SUCCESS) {
        setError('Trainer rejected target power.');
      }

      if (requestOpcode === OPCODE_START_RESUME && resultCode !== RESULT_SUCCESS) {
        setError('Trainer rejected start command.');
      }

      if (requestOpcode === OPCODE_STOP_PAUSE && resultCode !== RESULT_SUCCESS) {
        setError('Trainer rejected stop command.');
      }
    };

    const setupControlPoint = async () => {
      const server =
        trainerDevice.gatt?.connected
          ? trainerDevice.gatt
          : await trainerDevice.gatt?.connect();
      if (!server) {
        throw new Error('Trainer connection unavailable.');
      }
      const service = await server.getPrimaryService(FTMS_SERVICE);
      controlPoint = await service.getCharacteristic(FITNESS_MACHINE_CONTROL_POINT);
      controlPointRef.current = controlPoint;
      controlPoint.addEventListener('characteristicvaluechanged', handleResponse);
      await controlPoint.startNotifications();
      await writeControlPoint(controlPoint, buildRequestControl());
    };

    setupControlPoint().catch((err) => {
      if (!active) {
        return;
      }
      const message = err instanceof Error ? err.message : 'Trainer control failed.';
      setStatus('error');
      setError(message);
    });

    return () => {
      active = false;
      if (controlPoint) {
        controlPoint.removeEventListener('characteristicvaluechanged', handleResponse);
        controlPoint.stopNotifications().catch(() => undefined);
      }
      controlPointRef.current = null;
      hasControlRef.current = false;
    };
  }, [trainerDevice]);

  useEffect(() => {
    if (!isActive) {
      lastSendRef.current = { time: 0, target: 0 };
    }
  }, [isActive]);

  useEffect(() => {
    if (!isActive || status !== 'ready' || !hasControlRef.current) {
      return;
    }
    const controlPoint = controlPointRef.current;
    if (!controlPoint) {
      return;
    }
    if (!Number.isFinite(targetWatts)) {
      return;
    }

    const nextTarget = clampTargetWatts(targetWatts);
    const now = Date.now();
    const last = lastSendRef.current;
    if (last.target === nextTarget && now - last.time < MIN_SEND_INTERVAL_MS) {
      return;
    }

    if (now - last.time < MIN_SEND_INTERVAL_MS && last.target !== nextTarget) {
      return;
    }

    writeControlPoint(controlPoint, buildTargetPower(nextTarget))
      .then(() => {
        lastSendRef.current = { time: now, target: nextTarget };
      })
      .catch((err) => {
        const message = err instanceof Error ? err.message : 'Trainer target write failed.';
        setError(message);
      });
  }, [isActive, status, targetWatts]);

  return {
    status,
    error,
    startWorkout,
    pauseWorkout,
    stopWorkout,
  };
};
