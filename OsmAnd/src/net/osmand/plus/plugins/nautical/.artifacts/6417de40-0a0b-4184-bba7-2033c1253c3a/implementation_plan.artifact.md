# Implementation Plan - Nautical Plugin Optimization

Comprehensive optimization of the Nautical plugin to reduce battery drain, CPU usage, and RAM footprint across both backend engine and frontend UI layers.

## User Review Required

> [!IMPORTANT]
> This plan involves refactoring core telemetry processing and map drawing logic. While aiming for efficiency, it may slightly change the responsiveness of some live data updates (e.g., from 10Hz to 2Hz when stationary).

> [!WARNING]
> Background power management changes will release high-performance WiFi locks and partial wake locks more aggressively when the app is idle or disconnected.

## Proposed Changes

### Backend Engine & Infrastructure

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- **Throttled Calculations**: Implement dynamic frequency for `finalizeAndNotifyState`.
    - 10Hz when racing countdown is active or vessel speed > 2 knots.
    - 2Hz when stationary or in background.
- **Path-Based Dispatch Optimization**: Replace string-heavy parsing with a `Map<String, Parser>` or pre-hashed path lookup to reduce string comparison overhead in `processUpdates`.
- **Buffer Management**:
    - Reduce default `telemetryBuffers` capacity from 3600 to 600 (10 minutes at 1Hz) for non-essential paths.
    - Implement a `isThrottled` check in `saveBuffersToDisk` to avoid IO spikes during low battery.
- **REST Refresh Debouncing**: Add a 30-second cooldown to `refreshVesselState` triggered on foregrounding.

#### [MODIFY] [NauticalAisManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/NauticalAisManager.kt)
- **Notification Throttling**: Batch AIS listener updates using a `MutableSharedFlow` with a 500ms `sample` period instead of emitting for every message.
- **CPA Loop Optimization**: Move `updateAllCpa` to `Dispatchers.Default` and increase the interval to 30 seconds when the app is in the background or the vessel is stationary.

#### [MODIFY] [NauticalBackgroundService.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalBackgroundService.kt)
- **Lock Management**: Implement a timeout for `WIFI_MODE_FULL_HIGH_PERF`. Release high-performance locks if no data is received for 2 minutes, falling back to standard WiFi mode.

---

### Frontend & UI Layer

#### [MODIFY] [NauticalMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalMapLayer.kt)
- **Geodesic Math Caching**:
    - Cache `generateGeodesicLatLons` results in `GeodesicVectorCache`.
    - Only re-calculate spherical trig if heading changes > 1° or position changes > 5 meters.
- **Trajectory Path Optimization**:
    - Stop copying the entire trajectory list in `onDraw`.
    - Use a persistent `Path` that is only appended to when `SignalKEngine` emits a new point.
- **Steering Worm Optimization**: Reduce step count from 15 to 8 and cache the result.

#### [MODIFY] [NauticalHudManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalHudManager.kt)
- **View Caching**: Cache result of `findViewById` for `widget_top_bar`, `map_compass_button`, etc., in `init` or first access.
- **Update Throttling**: Sample `updateLayout` calls to a maximum of 2Hz.

#### [MODIFY] [LaylineViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/laylines/viewmodel/LaylineViewModel.kt)
- **Offload Heavy Math**: Ensure `LaylineMathEngine.calculateApparentLaylines` (heavy trig) runs on `Dispatchers.Default` instead of the Main thread.
- **Frequency Reduction**: Throttle layline updates to 2Hz when not in a "tactical" workflow.

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- **Singleton WearOsManager**: Move `WearOsNauticalManager` to a single instance managed by `NauticalPlugin` and shared via `getInstance()`.

## Verification Plan

### Automated Tests
- `SignalKEngineTest`: Verify that `finalizeAndNotifyState` frequency adheres to the new throttled rules.
- `AisManagerTest`: Verify that AIS notifications are batched and not emitted for every packet.

### Manual Verification
- **CPU Profiling**: Compare Android Studio Profiler (CPU) before and after changes while running a simulated Signal K stream with high-frequency AIS targets.
- **UI Responsiveness**: Verify that the map remains smooth (60fps) during heavy Rate-of-Turn maneuvers (Steering Worm active).
- **Battery Impact**: Monitor battery discharge rate in background mode with Signal K connected for 30 minutes.
