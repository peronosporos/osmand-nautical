# Architectural Rewrite: Z-Order, Raster I/O, Closed-Loop Validation, and Safety Preflight

## User Review Required

> [!IMPORTANT]
> This plan covers four critical architectural changes:
> 1. **Map Layer Z-Ordering**: Moving `SailingLaylinesMapLayer` and `WeatherRoutingMapLayer` from Z-order 0.0f to 3.0f.
> 2. **Raster I/O Offloading**: Removing synchronous `getSourcesForViewport` disk I/O from `MarineRasterMapLayer.onDraw()` and implementing an asynchronous tile/source fetcher with `Dispatchers.IO`.
> 3. **Closed-Loop Command Validation**: Enforcing 3000ms timeout / confirmation feedback loops on autopilot commands and UI widgets/dialogs.
> 4. **Safety Preflight Error Propagation**: Surfacing preflight rejection reasons via high-priority UI alerts (red snackbar/toast/TTS) when maneuvers are aborted.

## Proposed Changes

### Map Layers & Z-Ordering

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Update registration Z-order for `SailingLaylinesMapLayer` and `WeatherRoutingMapLayer` from `0.0f` to `3.0f`.

### Raster I/O

#### [MODIFY] [MarineRasterMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/raster/MarineRasterMapLayer.kt)
- Remove synchronous calls to `manager.getSourcesForViewport` inside `onDraw()`.
- Implement coroutine-based asynchronous caching/fetching of sources using `Dispatchers.IO` so `onDraw()` renders pre-fetched/cached bitmaps without blocking the UI thread.

### Closed-Loop Command Validation

#### [MODIFY] [AutopilotController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotController.kt)
- Track pending command states and enforce 3000ms timeout windows where commands require confirmation from the SignalK / NMEA server.
- Surface failure/timeout via UI feedback if no server delta/acknowledgement is received.

#### [MODIFY] [NauticalCompassWizardDialog.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalCompassWizardDialog.kt) (or related UI widgets)
- Bind UI loading/spinner states to pending command validation and display error dialogs/toasts upon failure/timeout.

### Safety Preflight Error Routing

#### [MODIFY] [SafetyPreflightController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SafetyPreflightController.kt)
- Ensure failure reasons are passed to high-priority UI notifications (red snackbar / toast / audio alert) instead of failing silently.

## Verification Plan

### Automated Tests
- Run unit and instrumentation tests for nautical plugin modules.

### Manual Verification
- Verify map layer rendering order (laylines and weather routing rendering above marine raster and S57, but below NauticalMapLayer).
- Verify UI responsiveness during raster map panning (no main thread disk I/O lag).
- Verify autopilot command confirmation feedback and 3000ms timeout handling.
- Verify safety preflight failure alerts.
