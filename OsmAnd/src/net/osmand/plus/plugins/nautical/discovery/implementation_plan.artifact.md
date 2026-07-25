# Implementation Plan - Signal K Local Network Discovery

Implement local network service discovery for Signal K servers using Android's `NsdManager` to eliminate manual IP entry for users.

## User Review Required

> [!IMPORTANT]
> This feature relies on local network permissions and mDNS support on the user's Wi-Fi network. It will scan for `_signalk-ws._tcp` and `_http._tcp` services.

## Open Questions

- None.

## Proposed Changes

### Strings (`OsmAnd/res/values/strings.xml`)
- Add discovery localized strings at the beginning of `strings.xml`:
  - `nautical_discovery_searching`: "Searching for Signal K servers..."
  - `nautical_discovery_found`: "Discovered servers"

### Discovery Component (`net.osmand.plus.plugins.nautical.discovery`)

#### [NEW] [DiscoveredServer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/discovery/DiscoveredServer.kt)
- Data class representing a discovered Signal K server:
  - `name`: Human-readable name (e.g., "OpenPlotter").
  - `host`: Resolved IP address or hostname.
  - `port`: WebSocket/HTTP port.
  - `isWebSocket`: Flag indicating if it's a dedicated WebSocket service.

#### [NEW] [SignalKDiscoveryManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/discovery/SignalKDiscoveryManager.kt)
- Core manager utilizing `NsdManager`.
- Asynchronously scans for `_signalk-ws._tcp` and `_http._tcp`.
- Automatically resolves service info to extract IP and port.
- Exposes discovered servers via `StateFlow<List<DiscoveredServer>>`.
- Provides `startDiscovery()` and `stopDiscovery()` methods for lifecycle management.

## Verification Plan

### Automated Tests
- Unit tests for `DiscoveredServer` data class.
- Compilation and build check.

### Manual Verification
- Verify that starting discovery initiates `NsdManager` scan.
- Log resolved server details to verify mDNS functionality.
