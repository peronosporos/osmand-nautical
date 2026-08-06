# Walkthrough - Phase 8.0P: Accessibility, RTL & Offline Resilience

Fixed RTL layout collapses, colorblind accessibility gaps, infinite network timeouts, and offline startup ANR vulnerabilities.

## Changes

### RTL & Accessibility
-   **[nautical_pilot_bottom_sheet.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/nautical_pilot_bottom_sheet.xml)**: Migrated margins to `marginStart`/`End` and audited padding for RTL compliance.
-   **[bottom_sheet_nautical_data.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/bottom_sheet_nautical_data.xml)**: Fixed padding for proper mirroring.
-   **[NavtexMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/ui/NavtexMapLayer.kt)**:
    -   Implemented geometric diagonal hatching for urgent hazard polygons to support colorblind users.
    -   Added an exclamation mark (`!`) badge to urgent warning markers.

### Network Resilience
-   **[SailingDependencyContainer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/di/SailingDependencyContainer.kt)**: Applied explicit 5s connection and 10s read/write timeouts to the shared OkHttp client.
-   **[NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)**: Hardened timeouts in `createHttpClient` to prevent infinite spinners in low-connectivity environments.
-   **[OceanographicGribMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/map/layers/OceanographicGribMapLayer.kt)**:
    -   Added real-time GRIB data expiration check (>24h).
    -   Renders a high-contrast "EXPIRED FORECAST" warning banner when data is stale.

### Offline Stability
-   **[SignalKDiscoveryManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/discovery/SignalKDiscoveryManager.kt)**: Moved mDNS service discovery and resolution to `Dispatchers.IO` with a 5s timeout bound.
-   **[SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)**: Wrapped self-identity resolution in a timeout block to prevent UI stalls on startup when offline.
-   **[CircularBuffer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/CircularBuffer.kt)**: Added missing `clear()` method to resolve a compilation error in `SignalKEngine`.

## Verification Results

### Automated Analysis
-   `NavtexMapLayer.kt`, `OceanographicGribMapLayer.kt`, `SignalKDiscoveryManager.kt`, and `SignalKEngine.kt` were analyzed for errors. Identified and resolved an unresolved reference to `clear()` in `CircularBuffer`.

### RTL Verification
-   Layouts verified to use `marginStart/End` and `paddingStart/End`.

### Accessibility
-   Urgent hazards now use both color (Red) and pattern (Hatching) to communicate status.
