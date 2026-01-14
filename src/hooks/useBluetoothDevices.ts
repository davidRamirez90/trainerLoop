import {
  type Dispatch,
  type MutableRefObject,
  type SetStateAction,
  useCallback,
  useEffect,
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

type ReconnectState = {
  attempts: number;
  timeoutId: number | null;
  manualDisconnect: boolean;
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

const createReconnectState = (): ReconnectState => ({
  attempts: 0,
  timeoutId: null,
  manualDisconnect: false,
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

const RECONNECT_BASE_DELAY_MS = 1000;
const RECONNECT_MAX_DELAY_MS = 10000;
const RECONNECT_MAX_ATTEMPTS = 5;

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

type ReconnectExtras = {
  battery: number | null;
  manufacturer: string | null;
  model: string | null;
  features: string | null;
};

type ReconnectConfig = {
  deviceRef: MutableRefObject<BluetoothDevice | null>;
  reconnectRef: MutableRefObject<ReconnectState>;
  setState: Dispatch<SetStateAction<DeviceState>>;
  setDevice: Dispatch<SetStateAction<BluetoothDevice | null>>;
  label: string;
  readExtras: (server: BluetoothRemoteGATTServer) => Promise<ReconnectExtras>;
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
  const trainerReconnectRef = useRef<ReconnectState>(createReconnectState());
  const hrReconnectRef = useRef<ReconnectState>(createReconnectState());

  const clearReconnect = useCallback((reconnectRef: MutableRefObject<ReconnectState>) => {
    const current = reconnectRef.current;
    if (current.timeoutId !== null) {
      window.clearTimeout(current.timeoutId);
    }
    current.timeoutId = null;
    current.attempts = 0;
  }, []);

  const markManualDisconnect = useCallback(
    (reconnectRef: MutableRefObject<ReconnectState>) => {
      clearReconnect(reconnectRef);
      reconnectRef.current.manualDisconnect = true;
    },
    [clearReconnect]
  );

  const markAutoReconnect = useCallback(
    (reconnectRef: MutableRefObject<ReconnectState>) => {
      reconnectRef.current.manualDisconnect = false;
    },
    []
  );

  const scheduleReconnect = useCallback((config: ReconnectConfig) => {
    const { deviceRef, reconnectRef, setState, setDevice, label, readExtras } = config;
    const device = deviceRef.current;
    const reconnectState = reconnectRef.current;
    if (!device || reconnectState.manualDisconnect || reconnectState.timeoutId !== null) {
      return;
    }

    const delay = Math.min(
      RECONNECT_BASE_DELAY_MS * Math.pow(2, reconnectState.attempts),
      RECONNECT_MAX_DELAY_MS
    );

    reconnectState.timeoutId = window.setTimeout(async () => {
      reconnectState.timeoutId = null;
      const currentDevice = deviceRef.current;
      if (!currentDevice || reconnectState.manualDisconnect) {
        return;
      }

      reconnectState.attempts += 1;
      setState((prev) => ({
        ...prev,
        status: 'connecting',
        error: `${label} disconnected. Reconnecting...`,
      }));

      try {
        const server =
          currentDevice.gatt?.connected
            ? currentDevice.gatt
            : await currentDevice.gatt?.connect();
        if (!server) {
          throw new Error(`Unable to reconnect to ${label.toLowerCase()}.`);
        }

        const extras = await readExtras(server);
        setDevice(currentDevice);
        setState({
          id: currentDevice.id,
          name: formatDeviceName(currentDevice),
          status: 'connected',
          battery: extras.battery,
          manufacturer: extras.manufacturer,
          model: extras.model,
          features: extras.features,
          error: null,
        });
        reconnectState.attempts = 0;
      } catch (error) {
        const message = error instanceof Error ? error.message : `${label} reconnect failed.`;
        if (reconnectState.attempts >= RECONNECT_MAX_ATTEMPTS) {
          setState((prev) => ({
            ...prev,
            status: 'error',
            error: `${message} Reconnect stopped.`,
          }));
          return;
        }
        setState((prev) => ({
          ...prev,
          status: 'connecting',
          error: `${message} Retrying...`,
        }));
        scheduleReconnect(config);
      }
    }, delay);
  }, []);

  const readTrainerExtras = useCallback(
    async (server: BluetoothRemoteGATTServer): Promise<ReconnectExtras> => {
      const [battery, info, features] = await Promise.all([
        readBatteryLevel(server),
        readDeviceInfo(server),
        readFeatureBits(server),
      ]);
      return {
        battery,
        manufacturer: info.manufacturer,
        model: info.model,
        features,
      };
    },
    []
  );

  const readHrExtras = useCallback(
    async (server: BluetoothRemoteGATTServer): Promise<ReconnectExtras> => {
      const [battery, info] = await Promise.all([
        readBatteryLevel(server),
        readDeviceInfo(server),
      ]);
      return {
        battery,
        manufacturer: info.manufacturer,
        model: info.model,
        features: null,
      };
    },
    []
  );

  const handleTrainerDisconnect = useCallback(() => {
    if (!trainerRef.current || trainerReconnectRef.current.manualDisconnect) {
      return;
    }
    setTrainerDevice(null);
    setTrainer((prev) => ({
      ...prev,
      status: 'connecting',
      error: 'Trainer disconnected. Reconnecting...',
    }));
    scheduleReconnect({
      deviceRef: trainerRef,
      reconnectRef: trainerReconnectRef,
      setState: setTrainer,
      setDevice: setTrainerDevice,
      label: 'Trainer',
      readExtras: readTrainerExtras,
    });
  }, [readTrainerExtras, scheduleReconnect]);

  const handleHrDisconnect = useCallback(() => {
    if (!hrRef.current || hrReconnectRef.current.manualDisconnect) {
      return;
    }
    setHrDevice(null);
    setHrSensor((prev) => ({
      ...prev,
      status: 'connecting',
      error: 'Heart rate sensor disconnected. Reconnecting...',
    }));
    scheduleReconnect({
      deviceRef: hrRef,
      reconnectRef: hrReconnectRef,
      setState: setHrSensor,
      setDevice: setHrDevice,
      label: 'Heart rate sensor',
      readExtras: readHrExtras,
    });
  }, [readHrExtras, scheduleReconnect]);

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

    markAutoReconnect(trainerReconnectRef);
    clearReconnect(trainerReconnectRef);
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

      const { battery, manufacturer, model, features } = await readTrainerExtras(server);

      setTrainer({
        id: device.id,
        name: formatDeviceName(device),
        status: 'connected',
        battery,
        manufacturer,
        model,
        features,
        error: null,
      });
      clearReconnect(trainerReconnectRef);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Trainer connection failed.';
      setTrainer((prev) => ({
        ...prev,
        status: 'error',
        error: message,
      }));
    }
  }, [
    bluetoothAvailable,
    clearReconnect,
    handleTrainerDisconnect,
    markAutoReconnect,
    readTrainerExtras,
  ]);

  const connectHeartRate = useCallback(async () => {
    if (!bluetoothAvailable) {
      setHrSensor((prev) => ({
        ...prev,
        status: 'error',
        error: 'Bluetooth is unavailable in this browser.',
      }));
      return;
    }

    markAutoReconnect(hrReconnectRef);
    clearReconnect(hrReconnectRef);
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

      const { battery, manufacturer, model } = await readHrExtras(server);

      setHrSensor({
        id: device.id,
        name: formatDeviceName(device),
        status: 'connected',
        battery,
        manufacturer,
        model,
        features: null,
        error: null,
      });
      clearReconnect(hrReconnectRef);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Heart rate connection failed.';
      setHrSensor((prev) => ({
        ...prev,
        status: 'error',
        error: message,
      }));
    }
  }, [
    bluetoothAvailable,
    clearReconnect,
    handleHrDisconnect,
    markAutoReconnect,
    readHrExtras,
  ]);

  const disconnectTrainer = useCallback(() => {
    markManualDisconnect(trainerReconnectRef);
    resetDeviceState(trainerRef, setTrainer, setTrainerDevice, handleTrainerDisconnect);
  }, [handleTrainerDisconnect, markManualDisconnect, resetDeviceState]);

  const disconnectHeartRate = useCallback(() => {
    markManualDisconnect(hrReconnectRef);
    resetDeviceState(hrRef, setHrSensor, setHrDevice, handleHrDisconnect);
  }, [handleHrDisconnect, markManualDisconnect, resetDeviceState]);

  useEffect(() => {
    return () => {
      clearReconnect(trainerReconnectRef);
      clearReconnect(hrReconnectRef);
    };
  }, [clearReconnect]);

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
