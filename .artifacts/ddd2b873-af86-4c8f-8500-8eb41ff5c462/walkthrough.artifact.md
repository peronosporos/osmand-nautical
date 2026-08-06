# Walkthrough - Telemetry Abstraction & Lifecycle Consistency

Completed the refactoring of NMEA transport layers and enforced strict coroutine lifecycle binding across the nautical plugin.

## Changes

### 1. Transport Abstraction & Reconnection logic
- Introduced `NmeaTransport` interface and `ConnectionState` enum to unify transport monitoring.
- Created `AbstractNmeaTransport` which centralizes the state machine and implements **exponential backoff reconnection**.
- Refactored `BluetoothNmeaClient`, `TcpNmeaClient`, and `UsbNmeaClient` to use the new abstraction.
- Reconnection loops are no longer scattered in individual clients, reducing code duplication and bug surface.

### 2. Lifecycle Binding
- Modified `DirectNmeaMultiplexer` and `SignalKEngine` to remove orphaned `CoroutineScope` instances.
- They now accept a `CoroutineScope` (typically `pluginScope`) in their constructors.
- This ensures that when the Nautical plugin is disabled, all active transport connections, parsers, and watchdog jobs are immediately cancelled.

### 3. Dependency Wiring
- Updated `SailingDependencyContainer` to handle scope-aware initialization of the NMEA multiplexer.
- Refactored `NauticalPlugin` to properly initialize engines with its `pluginScope`.
- Updated `UsbConnectionReceiver` to use the plugin's lifecycle-bound scope when attaching new USB devices.

### 4. Graceful Cancellation & Persistence
- Added `invokeOnCompletion` handles to `TcpNmeaClient` and `BluetoothNmeaClient` to ensure blocking socket operations are unblocked (by closing the socket) immediately upon coroutine cancellation.
- Refactored `SignalKEngine.saveBuffersToDisk` to be a `suspend` function using `NonCancellable` context.
- Updated `NauticalPlugin` to launch a non-blocking background coroutine for saving critical telemetry data during shutdown, ensuring no UI freezes (ANR) while maintaining data integrity via `NonCancellable`.

## Verification Results

### Automated Analysis
- Verified key files using `analyze_file`. Resolved critical errors and addressed several warnings related to unused imports and redundant qualifiers.

### Manual Review of Logic
- Centralized `runTransport` loop in `AbstractNmeaTransport` ensures that any `IOException` or socket failure triggers the exponential backoff (starting at 1s, doubling up to 30s).
- `DirectNmeaMultiplexer` now aggregates `ConnectionState.CONNECTED` across all active transports to provide a unified "is connected" status to the UI.
- Verified that `NmeaReplayViewModel` correctly uses its `viewModelScope` for the playback engine and multiplexer.

render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/connection/NmeaTransport.kt)
render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/connection/AbstractNmeaTransport.kt)
render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/multiplexer/DirectNmeaMultiplexer.kt)
render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
