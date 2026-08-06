# Implementation Plan - Telemetry Abstraction & Lifecycle

Address the transport fragmentation and lifecycle defects identified in Audit B. This involves unifying the NMEA transport interface, centralizing reconnection logic, and enforcing strict coroutine lifecycle binding.

## User Review Required

> [!IMPORTANT]
> The `NmeaClient` interface will be replaced by `NmeaTransport`. All implementing classes and consumers (like `DirectNmeaMultiplexer` and `NmeaPlaybackEngine`) will be updated to use the new interface and its `Flow`-based API.

## Proposed Changes

### 1. Unified Transport Interface & State Machine

#### [NEW] [ConnectionState.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/connection/ConnectionState.kt)
Define connection states for all transports.
```kotlin
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}
```

#### [NEW] [NmeaTransport.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/connection/NmeaTransport.kt)
New interface replacing `NmeaClient`.
```kotlin
interface NmeaTransport {
    val connectionState: StateFlow<ConnectionState>
    val dataStream: Flow<String>
    fun connect()
    fun disconnect()
}
```

#### [NEW] [AbstractNmeaTransport.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/connection/AbstractNmeaTransport.kt)
Centralized state machine and exponential backoff logic. This avoids duplicating reconnection loops in every client.

#### [DELETE] [NmeaClient.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/connection/NmeaClient.kt)
Removed in favor of `NmeaTransport`.

---

### 2. Transport Implementation Refactoring

#### [MODIFY] [BluetoothNmeaClient.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/connection/BluetoothNmeaClient.kt)
#### [MODIFY] [TcpNmeaClient.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/connection/TcpNmeaClient.kt)
#### [MODIFY] [UsbNmeaClient.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/connection/UsbNmeaClient.kt)
#### [MODIFY] [NmeaPlaybackEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/replay/NmeaPlaybackEngine.kt)
Refactor to implement `NmeaTransport` (or inherit from `AbstractNmeaTransport`).

---

### 3. Lifecycle Binding

#### [MODIFY] [DirectNmeaMultiplexer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/multiplexer/DirectNmeaMultiplexer.kt)
- Remove internal `CoroutineScope`.
- Accept `CoroutineScope` in constructor.
- Update to use `NmeaTransport` instead of `NmeaClient`.

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Remove internal `engineScope`.
- Accept `CoroutineScope` in constructor.

#### [MODIFY] [SailingDependencyContainer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/di/SailingDependencyContainer.kt)
- Provide a way to inject/pass scope to `DirectNmeaMultiplexer`.

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Pass `pluginScope` to `SignalKEngine` and `DirectNmeaMultiplexer` during initialization.

## Verification Plan

### Automated Tests
- Verify that `NmeaTransport` implementations correctly report `ConnectionState`.
- Verify that `DirectNmeaMultiplexer` stops all collection when its scope is cancelled.

### Manual Verification
- Test reconnection behavior (e.g., toggling Bluetooth or killing TCP server).
- Verify that disabling the Nautical plugin cleanly stops all NMEA background activity.
