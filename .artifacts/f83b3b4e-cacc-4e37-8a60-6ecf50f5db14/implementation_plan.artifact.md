# Critical Fix: Internal Location Bridge, Socket Ingress & Uninitialized State Alarms

This plan addresses issues where OsmAnd fails to establish active data sockets, streams zero location data, and triggers false alarms due to uninitialized marine state.

## Proposed Changes

### [Nautical Core]

#### [MODIFY] [MarineState.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/MarineState.kt)
- Add `hasValidFix` extension property to `MarineState` to centralize fix validity logic.
- Logic: `latitude` and `longitude` are non-null and non-zero, and the position timestamp is less than 30 seconds old.

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Update `onInternalLocationUpdate` to allow fallback even when `connectionStatus == CONNECTED` if there is no valid external fix.
- Add debug logging to `handleIncomingMessage` to trace received sentences.

#### [MODIFY] [OkHttpSignalKConnection.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/OkHttpSignalKConnection.kt)
- Add explicit logging for connection lifecycle: `connect()`, `onOpen()`, `onFailure()`, `onClosing()`, and `onClosed()`.
- Log the URL and protocol (WS vs WSS) during connection attempts.

#### [MODIFY] [DirectNmeaMultiplexer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/multiplexer/DirectNmeaMultiplexer.kt)
- Add logging for `start()`, `stop()`, and NMEA sentence reception in `processSentence()`.
- Log successful socket binding and IO coroutine launch.

### [Safety & Alarms]

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Guard `evaluateVesselSafety` with `if (!state.hasValidFix) return`.
- Ensure `MarineState` updates from internal GPS are correctly propagated when external data is missing.

#### [MODIFY] [AnchorDriftWatchdog.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/AnchorDriftWatchdog.kt)
- Add guard in `observationJob` and `onLocationChanged` to inhibit alarms if the state is uninitialized or invalid (`!state.hasValidFix`).

#### [MODIFY] [NauticalAisManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/NauticalAisManager.kt)
- Guard `updateAllCpa` with `if (!state.hasValidFix) return`.

## Verification Plan

### Manual Verification
- Deploy to a device and observe logcat for new connection and sentence logs.
- Simulate "No Fix" scenario (e.g., disconnect server or disable GPS) and verify that anchor drift and collision alarms do not trigger.
- Verify that OsmAnd's internal GPS data is used to update Marine widgets when Signal K server is connected but lacks GPS data.
- Check that the "Internal GPS Fallback" correctly updates `MarineState` (lat, lon, SOG, COG).
