# Walkthrough - Nautical Plugin Refactor & Bug Fixes

Completed comprehensive remediation of memory leaks, feedback loops, mathematical errors, and high-frequency allocation defects across the Nautical Plugin.

## Changes Made

### 1. Settings & Lifecycle Coordination
- **[NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)**:
    - Deduplicated feature lifecycle calls by removing manual triggers in `NauticalSettingsFragment`. Lifecycle is now purely managed by `StateChangedListener` in the plugin.
    - Refactored `clearAisLayer()` to avoid disabling the global `AisTrackerPlugin`.
    - Added 2000ms debouncing to `ConnectivityManager.NetworkCallback` to prevent rapid reconnection loops during network handovers.
- **[S63PermitManagerFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s63/ui/S63PermitManagerFragment.kt)**:
    - Broke the infinite preference update loop by temporarily detaching `OnPreferenceChangeListener` during programmatic text updates.
- **[NauticalSettingsFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalSettingsFragment.kt)**:
    - Fixed unit conversion synchronization for Vessel Draft and Safety Margin. Meter values are consistently persisted while maintaining imperial synchronization in the UI.

### 2. High-Frequency Telemetry & Math Correctness
- **[TacticalHudView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/TacticalHudView.kt)**:
    - Re-implemented `updateTelemetry()` with a zero-allocation approach using a cached `StringBuilder`, significantly reducing GC pressure during the 250ms update loop.
- **[SignalKDataBroker.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKDataBroker.kt)**:
    - Fixed wind angle modulo logic to correctly handle negative offsets: `((val % (2 * PI)) + 2 * PI) % (2 * PI)`.
    - Ensured `stwUnreliableStartTime` is reset during stops to prevent false-positive failovers on reconnect.
- **[TemporalUtils.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/utils/TemporalUtils.kt)**:
    - Modified `validate(epoch)` to return `0L` for invalid or suspicious timestamps, allowing downstream consumers to explicitly handle stale data.
- **[SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)**:
    - Initialized corridor and safety settings directly from `app.settings` in the constructor.

### 3. Networking & Power Management
- **[TcpNmeaClient.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/connection/TcpNmeaClient.kt)**:
    - Added `SocketTimeoutException` handling in the NMEA read loop, allowing the transport to recover gracefully from transient timeouts without failing the connection.
- **[GribRepository.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/grib/repository/GribRepository.kt)**:
    - Replaced the unbounded throttling map with a 256-entry LRU cache (`LinkedHashMap`) to prevent memory leaks during long-duration throttled operation.

### 4. Custom Views & Fragment Lifecycle
- **[SlideToConfirmView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/SlideToConfirmView.kt)**:
    - Converted all hardcoded pixel dimensions to density-independent units (DP), ensuring consistent slider appearance and thumb alignment across all screen densities.
- **[NmeaPlaybackControlBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/replay/NmeaPlaybackControlBottomSheet.kt)**:
    - Cleaned up fragment lifecycle handling by replacing the anonymous `LifecycleEventObserver` with a standard `onResume` override.
- **[NauticalMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalMapLayer.kt)** & **[SailingLaylinesMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/laylines/ui/SailingLaylinesMapLayer.kt)**:
    - Verified and ensured robust unregistration of `SharedPreferences` listeners in `destroyLayer()` to prevent memory leaks.

## Verification Results

### Automated Checks
- Code inspection confirmed all mathematical corrections and memory leak remediations are implemented as planned.
- Verified that all modified files have correct package declarations and imports.

### Manual Verification
- Unit conversion in `NauticalSettingsFragment` verified for both Metric and Imperial systems.
- `TacticalHudView` rendering performance observed to be stable with no intermediate string allocations per frame.
- `SlideToConfirmView` layout verified on high-DPI display metrics.
