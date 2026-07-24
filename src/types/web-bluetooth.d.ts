export {};

declare global {
  type BluetoothServiceUUID = number | string;
  type BluetoothCharacteristicUUID = number | string;

  interface BluetoothRemoteGATTServer {
    connected: boolean;
    device: BluetoothDevice;
    connect(): Promise<BluetoothRemoteGATTServer>;
    disconnect(): void;
    getPrimaryService(service: BluetoothServiceUUID): Promise<BluetoothRemoteGATTService>;
  }

  interface BluetoothRemoteGATTService {
    uuid: string;
    isPrimary: boolean;
    getCharacteristic(
      characteristic: BluetoothCharacteristicUUID
    ): Promise<BluetoothRemoteGATTCharacteristic>;
  }

  interface BluetoothRemoteGATTCharacteristic extends EventTarget {
    uuid: string;
    value?: DataView | null;
    readValue(): Promise<DataView>;
    writeValue(value: Uint8Array): Promise<void>;
    writeValueWithResponse?(value: Uint8Array): Promise<void>;
    startNotifications(): Promise<BluetoothRemoteGATTCharacteristic>;
    stopNotifications(): Promise<BluetoothRemoteGATTCharacteristic>;
    addEventListener(
      type: 'characteristicvaluechanged',
      listener: (event: Event) => void,
      options?: boolean | AddEventListenerOptions
    ): void;
    removeEventListener(
      type: 'characteristicvaluechanged',
      listener: (event: Event) => void,
      options?: boolean | EventListenerOptions
    ): void;
  }

  interface BluetoothDevice extends EventTarget {
    id: string;
    name?: string | null;
    gatt?: BluetoothRemoteGATTServer | null;
    addEventListener(
      type: 'gattserverdisconnected',
      listener: (event: Event) => void,
      options?: boolean | AddEventListenerOptions
    ): void;
    removeEventListener(
      type: 'gattserverdisconnected',
      listener: (event: Event) => void,
      options?: boolean | EventListenerOptions
    ): void;
  }

  interface Bluetooth {
    requestDevice(options?: RequestDeviceOptions): Promise<BluetoothDevice>;
    getAvailability?(): Promise<boolean>;
  }

  interface RequestDeviceOptions {
    filters?: BluetoothLEScanFilter[];
    optionalServices?: BluetoothServiceUUID[];
    acceptAllDevices?: boolean;
  }

  interface BluetoothLEScanFilter {
    services?: BluetoothServiceUUID[];
    name?: string;
    namePrefix?: string;
    manufacturerData?: BluetoothManufacturerDataFilter[];
    serviceData?: BluetoothServiceDataFilter[];
  }

  interface BluetoothManufacturerDataFilter {
    companyIdentifier: number;
    dataPrefix?: BufferSource;
    mask?: BufferSource;
  }

  interface BluetoothServiceDataFilter {
    service: BluetoothServiceUUID;
    dataPrefix?: BufferSource;
    mask?: BufferSource;
  }

  interface Navigator {
    bluetooth?: Bluetooth;
  }
}
