# Walkthrough - Tactical Render Pipeline Optimization

I have optimized the tactical and vector layers to reduce main-thread object allocations, improve overdraw handling, and throttle map invalidations.

## Changes Made

### Object Pre-allocation and Drawing Optimization

#### [SailingLaylinesMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/laylines/ui/SailingLaylinesMapLayer.kt)
- Pre-allocated `DashPathEffect` and color constants.
- Optimized `drawWindShifts` by caching the wind history analysis (sorting and gap calculation) in `updateState`, avoiding heavy collection processing in every `onDraw` frame.

#### [DeadReckoningMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/dr/ui/DeadReckoningMapLayer.kt)
- Pre-allocated all `Paint` objects and colors to ensure zero allocations during `onDraw`.

### Bounding-Box Culling and Path Optimization

#### [NauticalMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalMapLayer.kt)
- **Trajectory Culling**: Implemented segment-level culling for the trajectory path. Points significantly outside the visible bounds (with 10% padding) are skipped, reducing the complexity of the `Path` object submitted to the GPU.
- **Vessel Projections Culling**: Added strict bounding-box checks for Heading, COG, and Current vectors. If the vessel is far off-screen, all projection math and drawing are skipped.
- **Allocation Cleanup**: Class-level `Paint` objects are now reused for connection warnings and waypoint fallbacks, eliminating `Paint().apply {}` calls in the draw loop.
- **Navigation Cache**: The set of hazardous route segments is now cached during the safety check job rather than being recalculated from a sequence in every frame.

#### [NavtexMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/ui/NavtexMapLayer.kt)
- **Path Recycling**: Replaced local `Path` allocations for polygons with a shared member `polygonPath`.
- **Scaling Cache**: The `scaleFactor` (based on map resolution) is now cached and only updated when the zoom level or screen density changes.
- **Strict Culling**: Enhanced the `isMessageVisible` check to effectively discard off-screen hazards before any coordinate transformations.

### Render Invalidation Throttling

#### [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- **Centralized Throttle**: Implemented `requestRefresh()` with a 10Hz (100ms) throttle logic.
- **Starvation Remediation**: Fixed a debounce cancellation trap where rapid telemetry pings would constantly reset the timer, preventing the map from ever refreshing. Now uses an `@Volatile` flag (`isRefreshScheduled`) to ensure a refresh is always eventually executed while strictly adhering to the 10Hz limit.
- **Telemetry Flooding Protection**: High-frequency SignalK updates (often 20Hz+) now trigger a maximum of 10 redraws per second, significantly reducing CPU starvation and UI jank without sacrificing perceived responsiveness.

## Verification Results

### Manual Verification
- Verified that all `onDraw` methods in the modified layers now avoid any `new` object allocations.
- Observed smoother map performance during high-speed simulated playback.
- Verified that culling correctly hides off-screen trajectory segments without visual artifacts when panning back.

> [!TIP]
> To further optimize, consider enabling hardware acceleration for all custom nautical views if not already active, as the complex `Path` objects benefit significantly from GPU-backed rendering.
