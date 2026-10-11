# Current Android device checks

Use a physical FTMS trainer and HR sensor. Run the automated checks in the Android
README first. This checklist covers retained functionality after the scope cleanup.

1. Scan/connect trainer and HR; verify device status on Home. Only trainer and HR
   roles should appear. Choose Workout must open the library.
2. Import ZWO/MRC/ERG/JSON; preview and start. Verify FTP-scaled built-ins and the
   structured Endurance Ride. Previously stored workouts should still open.
3. Confirm first target, interval transitions, zero load for untargeted segments,
   intensity ±1%/reset, ERG off/on, pause/resume, skip and +30s recovery.
4. Connect a trainer after opening/starting the player; verify control once ready.
   Drop/reconnect BLE mid-ride, then explicitly detach/reattach; verify recording
   continuity and HR clearing on disconnect. Pause before reconnection; verify the
   trainer does not restart until Resume.
5. Complete a workout; confirm the trainer stops, then Save and share FIT. Rotate
   on the completion screen before saving; telemetry must remain available.
6. Configure intervals.icu; check today's plan, library sync, FTP/weight updates,
   automatic upload on Save and manual retry for completed unsynced history.
7. Run a ramp test: warm-up exemption, low-power finish, no exhaustion finish on
   sensor dropout, accept/discard FTP, and optional intervals.icu FTP write-back.
8. Verify portrait/landscape charts, zoom/panning/live follow, interval cues,
   screen-off service continuity, optional virtual overlay and light/dark themes.
9. Upgrade over an existing installation; confirm historical rides remain readable.
   No builder, standalone free ride, GPX routes, coaching or shifter UI should remain.
