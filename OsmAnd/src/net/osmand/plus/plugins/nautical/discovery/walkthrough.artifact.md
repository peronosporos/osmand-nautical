# Walkthrough - Signal K Local Network Discovery

Implemented local network service discovery for Signal K servers using Android's `NsdManager`. This feature enables the app to automatically find and resolve Signal K servers on the Wi-Fi network, eliminating the need for users to manually enter IP addresses and ports.

## Changes

### String Resources (`OsmAnd/res/values/strings.xml`)
- Added localized strings for the discovery process:
  - `nautical_discovery_searching`: Status message when scanning for servers.
  - `nautical_discovery_found`: Header for the list of discovered servers.

### Discovery Component (`net.osmand.plus.plugins.nautical.discovery`)
- **[NEW] [DiscoveredServer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/discovery/DiscoveredServer.kt)**:
  - Data class that encapsulates the essential information for a discovered server, including its name, hostname, port, and service type (WebSocket or HTTP).
- **[NEW] [SignalKDiscoveryManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/discovery/SignalKDiscoveryManager.kt)**:
  - Core service discovery manager that wraps Android's `NsdManager`.
  - It listens for `_signalk-ws._tcp.` and `_http._tcp.` mDNS advertisements.
  - Automatically resolves discovered services to obtain their IP addresses and ports.
  - Manages a thread-safe list of discovered servers and exposes them through a `StateFlow<List<DiscoveredServer>>` for reactive UI updates.

## Verification Results

### Build & Compilation
- Successfully implemented and compiled all discovery components.
- Verified that `NsdManager` correctly initializes and starts discovery for the specified service types.
