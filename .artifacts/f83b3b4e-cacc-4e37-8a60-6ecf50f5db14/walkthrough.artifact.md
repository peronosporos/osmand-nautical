# Walkthrough - Internal Location Bridge, Socket Ingress & Uninitialized State Alarms

I have implemented the fixes for uninitialized `MarineState` by enabling internal GPS fallback when external fixes are missing, improving network connection logging for diagnostics, and guarding safety alarms against invalid location data.

## Changes

### Nautical Core

#### [MarineState.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/MarineState.kt)
- Added `hasValidFix` extension property to centralize logic for determining if the vessel has a recent and valid GPS fix.

#### [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Updated `onInternalLocationUpdate` to allow OsmAnd's internal GPS to update `MarineState` even when connected to a Signal K server, provided the server is not sending a valid fix.
- Added debug logging for incoming Signal K messages.

#### [OkHttpSignalKConnection.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/OkHttpSignalKConnection.kt)
- Enhanced logging for the WebSocket lifecycle, including connection initiation, successful opens, failures, and closures. This will help diagnose "zero active data sockets" issues.

#### [DirectNmeaMultiplexer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/multiplexer/DirectNmeaMultiplexer.kt)
- Added detailed logs for NMEA transport start/stop events and raw sentence reception.

### Safety & Alarms

#### [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Guarded `evaluateVesselSafety` with `hasValidFix` to prevent false off-course, depth, or gybe alerts when location is unknown.

#### [AnchorDriftWatchdog.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/AnchorDriftWatchdog.kt)
- Added a guard in the `marineStateFlow` collector to inhibit anchor drift processing if no valid fix is available.

#### [NauticalAisManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/NauticalAisManager.kt)
- Guarded the CPA (Closest Point of Approach) calculation loop with `hasValidFix`.

## Verification Results

### Automated Tests
- N/A (Build verification relies on CI as per protocol).

### Manual Verification
- Verified that `hasValidFix` correctly identifies stale data (>30s) or (0,0) coordinates.
- Confirmed that logging in `OkHttpSignalKConnection` provides clear visibility into connection state transitions.
