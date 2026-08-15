# Implementation Plan - Fix Signal K Reconnection Loop

The goal is to prevent the rapid connect/disconnect cycle (the "Reconnection War") in the Nautical plugin by implementing guards against duplicate connection attempts and reducing redundant triggers from mDNS, NetworkCallback, and UI actions.

## User Review Required

> [!IMPORTANT]
> The fix involves changing `NauticalPlugin.startEngine()` and `OkHttpSignalKConnection.connect()` to be thread-safe (`@Synchronized`) and state-aware. It will no longer tear down an active connection if the target host hasn't changed.

## Proposed Changes

### [Nautical Engine]

#### [MODIFY] [SignalKConnection.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKConnection.kt)
*   Add `var url: String?` property to the interface to track the current connection target.

#### [MODIFY] [OkHttpSignalKConnection.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/OkHttpSignalKConnection.kt)
*   Implement `override var url: String?`.
*   Add `@Synchronized` to the `connect` methods.
*   Ensure `isConnecting` is reset to `false` in `onClosed`.
*   Add ingress logging in `onMessage`: `log.info("SignalK Ingress: ${text.take(120)}...")`.

### [Nautical Plugin]

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
*   Add `private var pluginStartTime = 0L` and initialize it in `initPlugin()`.
*   Add `@Synchronized` to `startEngine()`.
*   Refactor `startEngine()`:
    *   Calculate the target `wsUrl`.
    *   If `connection` is already connected/connecting to the **same** URL, skip the reconnection.
    *   Only `disconnect()` and re-initialize if the host/URL has changed.
*   Update `networkCallback`:
    *   Inside `debounceReconnect`, ignore network changes that occur within the first 5 seconds of plugin startup to allow the initial connection to stabilize.

#### [MODIFY] [SignalKDiscovery.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/network/SignalKDiscovery.kt)
*   In `onServiceResolved`, check if the resolved `host` matches the current `NAUTICAL_SERVER_IP`.
*   Avoid triggering settings updates (which might lead to `reconnect()`) if the host is unchanged.

## Verification Plan

### Automated Tests
*   Push to remote CI and monitor build/test results.
*   Retain logs from CI if failure occurs.

### Manual Verification
*   Verify via logcat that "Initiating WebSocket connection..." no longer appears in rapid succession.
*   Verify that "SignalK Ingress" logs appear once connected.
*   Verify that toggling network or restarting the app doesn't trigger a connect/disconnect loop.
