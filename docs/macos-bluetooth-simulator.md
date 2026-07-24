# macOS-compatible trainer simulator approach

## Goal

Use a browser to drive repeatable trainer/heart-rate scenarios while a stable macOS
Bluetooth backend advertises as a smart trainer that the Android app can pair with.

## Recommended architecture

```text
Browser simulator dashboard
  -> localhost WebSocket/HTTP
macOS native simulator host
  -> CoreBluetooth peripheral manager
Android training app
```

The browser should remain the control surface. The BLE peripheral must be native on
macOS because the Android app needs to connect to an advertised GATT server, not to a
web page.

## Backend technology

- Native Swift command-line app or small menu-bar app.
- CoreBluetooth `CBPeripheralManager` for BLE advertising and GATT services.
- Local WebSocket server for dashboard commands and live backend state.
- React/Vite dashboard can be served by the native host or by the existing dev server.

## Minimum simulated services

- Fitness Machine Service (`0x1826`).
- Fitness Machine Feature (`0x2acc`) for connection metadata.
- Indoor Bike Data (`0x2ad2`) with notifications for power and cadence.
- Fitness Machine Control Point (`0x2ad9`) for request-control, start/resume,
  stop/pause, and set-target-power responses.
- Optional Battery Service (`0x180f`) and Device Information Service (`0x180a`) so
  the app settings panel can show battery, manufacturer, and model.
- Optional Heart Rate Service (`0x180d`) with Heart Rate Measurement (`0x2a37`), or
  HR embedded in the FTMS payload once supported by the Android app.

## Scenario controls

The browser dashboard should expose presets matching development scenarios:

- Steady interval with stable HR.
- Fatigue with cadence collapse and HR drift.
- Incomplete recovery with elevated HR.
- Power/cadence notification dropouts.
- Control-point failures: deny control, reject target power, delay responses.
- GATT disconnect/reconnect.

## Implementation notes

1. Start with a Swift backend that advertises one FTMS trainer and emits Indoor Bike
   Data once per second.
2. Add Control Point write handling and response notifications next so ERG mode can
   be tested.
3. Add the browser dashboard as a separate Vite app or a route in this repo that
   connects to `localhost` over WebSocket.
4. Keep scenario files JSON-based so they can be reused by both the in-app simulator
   and the macOS BLE backend.
5. Treat CoreBluetooth behavior as the source of truth for macOS compatibility and
   verify with the Android app after each new characteristic is added.
