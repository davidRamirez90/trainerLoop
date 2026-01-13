import {
  type Dispatch,
  type MutableRefObject,
  type SetStateAction,
  useCallback,
  useMemo,
  useRef,
  useState,
} from 'react';

type DeviceStatus = 'idle' | 'connecting' | 'connected' | 'error';

type DeviceState = {
  id: string | null;
  name: string | null;
  status: DeviceStatus;
  battery: number | null;
  manufacturer: string | null;
  model: string | null;
  features: string | null;
  error: string | null;
};

const createInitialDeviceState = (): DeviceState => ({
  id: null,
  name: null,
  status: 'idle',
  battery: null,
  manufacturer: null,
  model: null,
  features: null,
  error: null,
});

const textDecoder = new TextDecoder('utf-8');

const FTMS_SERVICE = 0x1826;
const HEART_RATE_SERVICE = 0x180d;
const BATTERY_SERVICE = 0x180f;
const DEVICE_INFO_SERVICE = 0x180a;
const FITNESS_MACHINE_FEATURE = 0x2acc;
const BATTERY_LEVEL = 0x2a19;
const MANUFACTURER_NAME = 0x2a29;
const MODEL_NUMBER = 0x2a24;

const readOptionalString = async (
  service: BluetoothRemoteGATTService,
  characteristic: BluetoothCharacteristicUUID
) => {
  try {
    const data = await service.getCharacteristic(characteristic).then((char) =>
      char.readValue()
    );
    return textDecoder
      .decode(data.buffer.slice(data.byteOffset, data.byteOffset + data.byteLength))
      .trim();
  } catch {
    return null;
  }
};

const readBatteryLevel = async (server: BluetoothRemoteGATTServer) => {
  try {
    const service = await server.getPrimaryService(BATTERY_SERVICE);
    const data = await service.getCharacteristic(BATTERY_LEVEL).then((char) =>
      char.readValue()
    );
    return data.getUint8(0);
  } catch {
    return null;
  }
};

const readDeviceInfo = async (server: BluetoothRemoteGATTServer) => {
  try {
    const service = await server.getPrimaryService(DEVICE_INFO_SERVICE);
    const [manufacturer, model] = await Promise.all([
      readOptionalString(service, MANUFACTURER_NAME),
      readOptionalString(service, MODEL_NUMBER),
    ]);
    return { manufacturer, model };
  } catch {
    return { manufacturer: null, model: null };
  }
};

const readFeatureBits = async (server: BluetoothRemoteGATTServer) => {
  try {
    const service = await server.getPrimaryService(FTMS_SERVICE);
    const data = await service
      .getCharacteristic(FITNESS_MACHINE_FEATURE)
      .then((char) => char.readValue());
    const bytes = new Uint8Array(data.buffer, data.byteOffset, data.byteLength);
    const hex = Array.from(bytes, (value) => value.toString(16).padStart(2, '0'))
      .join('')
      .toUpperCase();
    return `0x${hex}`;
  } catch {
    return null;
  }
};

const formatDeviceName = (device: BluetoothDevice) =>
  device.name && device.name.trim() ? device.name.trim() : 'Unknown Device';

export const useBluetoothDevices = () => {
  const bluetoothAvailable = useMemo(
    () => typeof navigator !== 'undefined' && !!navigator.bluetooth,
    []
  );

  const [trainer, setTrainer] = useState<DeviceState>(createInitialDeviceState);
  const [hrSensor, setHrSensor] = useState<DeviceState>(createInitialDeviceState);
  const [trainerDevice, setTrainerDevice] = useState<BluetoothDevice | null>(null);
  const [hrDevice, setHrDevice] = useState<BluetoothDevice | null>(null);

  const trainerRef = useRef<BluetoothDevice | null>(null);
  const hrRef = useRef<BluetoothDevice | null>(null);

  const handleTrainerDisconnect = useCallback(() => {
    trainerRef.current = null;
    setTrainerDevice(null);
    setTrainer((prev) => ({
      ...prev,
      status: 'idle',
      error: 'Trainer disconnected.',
    }));
  }, []);

  const handleHrDisconnect = useCallback(() => {
    hrRef.current = null;
    setHrDevice(null);
    setHrSensor((prev) => ({
      ...prev,
      status: 'idle',
      error: 'Heart rate sensor disconnected.',
    }));
  }, []);

  const resetDeviceState = useCallback(
    (
      deviceRef: MutableRefObject<BluetoothDevice | null>,
      setState: Dispatch<SetStateAction<DeviceState>>,
      setDevice: Dispatch<SetStateAction<BluetoothDevice | null>>,
      disconnectHandler: () => void
    ) => {
      if (deviceRef.current) {
        deviceRef.current.removeEventListener('gattserverdisconnected', disconnectHandler);
        deviceRef.current.gatt?.disconnect();
      }
      deviceRef.current = null;
      setState(createInitialDeviceState());
      setDevice(null);
    },
    []
  );

  const connectTrainer = useCallback(async () => {
    if (!bluetoothAvailable) {
      setTrainer((prev) => ({
        ...prev,
        status: 'error',
        error: 'Bluetooth is unavailable in this browser.',
      }));
      return;
    }

    setTrainer((prev) => ({ ...prev, status: 'connecting', error: null }));

    try {
      const device = await navigator.bluetooth.requestDevice({
        filters: [{ services: [FTMS_SERVICE] }],
        optionalServices: [BATTERY_SERVICE, DEVICE_INFO_SERVICE],
      });

      const server = await device.gatt?.connect();
      if (!server) {
        throw new Error('Unable to connect to trainer.');
      }

      if (trainerRef.current) {
        trainerRef.current.removeEventListener('gattserverdisconnected', handleTrainerDisconnect);
      }
      trainerRef.current = device;
      setTrainerDevice(device);
      device.addEventListener('gattserverdisconnected', handleTrainerDisconnect);

      const [battery, info, features] = await Promise.all([
        readBatteryLevel(server),
        readDeviceInfo(server),
        readFeatureBits(server),
      ]);

      setTrainer({
        id: device.id,
        name: formatDeviceName(device),
        status: 'connected',
        battery,
        manufacturer: info.manufacturer,
        model: info.model,
        features,
        error: null,
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Trainer connection failed.';
      setTrainer((prev) => ({
        ...prev,
        status: 'error',
        error: message,
      }));
    }
  }, [bluetoothAvailable, handleTrainerDisconnect]);

  const connectHeartRate = useCallback(async () => {
    if (!bluetoothAvailable) {
      setHrSensor((prev) => ({
        ...prev,
        status: 'error',
        error: 'Bluetooth is unavailable in this browser.',
      }));
      return;
    }

    setHrSensor((prev) => ({ ...prev, status: 'connecting', error: null }));

    try {
      const device = await navigator.bluetooth.requestDevice({
        filters: [{ services: [HEART_RATE_SERVICE] }],
        optionalServices: [BATTERY_SERVICE, DEVICE_INFO_SERVICE],
      });

      const server = await device.gatt?.connect();
      if (!server) {
        throw new Error('Unable to connect to heart rate sensor.');
      }

      if (hrRef.current) {
        hrRef.current.removeEventListener('gattserverdisconnected', handleHrDisconnect);
      }
      hrRef.current = device;
      setHrDevice(device);
      device.addEventListener('gattserverdisconnected', handleHrDisconnect);

      const [battery, info] = await Promise.all([
        readBatteryLevel(server),
        readDeviceInfo(server),
      ]);

      setHrSensor({
        id: device.id,
        name: formatDeviceName(device),
        status: 'connected',
        battery,
        manufacturer: info.manufacturer,
        model: info.model,
        features: null,
        error: null,
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Heart rate connection failed.';
      setHrSensor((prev) => ({
        ...prev,
        status: 'error',
        error: message,
      }));
    }
  }, [bluetoothAvailable, handleHrDisconnect]);

  const disconnectTrainer = useCallback(() => {
    resetDeviceState(trainerRef, setTrainer, setTrainerDevice, handleTrainerDisconnect);
  }, [handleTrainerDisconnect, resetDeviceState]);

  const disconnectHeartRate = useCallback(() => {
    resetDeviceState(hrRef, setHrSensor, setHrDevice, handleHrDisconnect);
  }, [handleHrDisconnect, resetDeviceState]);

  return {
    bluetoothAvailable,
    trainer,
    hrSensor,
    trainerDevice,
    hrDevice,
    connectTrainer,
    connectHeartRate,
    disconnectTrainer,
    disconnectHeartRate,
  };
};
