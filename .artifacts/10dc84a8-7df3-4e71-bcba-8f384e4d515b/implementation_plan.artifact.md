# Implementation Plan - Tactical Render Pipeline Optimization

This plan addresses main-thread object allocations and overdraw overloads in the tactical and vector layers of the OsmAnd-Nautical plugin.

## User Review Required

> [!IMPORTANT]
> The optimization involves moving logic from the `onDraw` loop to update methods or caching mechanisms. This might introduce a slight delay in UI updates if the throttling is too aggressive (currently targeted at 10Hz/100ms).

## Proposed Changes

### Tactical UI Performance (`net.osmand.plus.plugins.nautical`)

#### [MODIFY] [SailingLaylinesMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/laylines/ui/SailingLaylinesMapLayer.kt)
- **Object Pre-allocation**: Move `DashPathEffect` and color parsing out of `onDraw` and `setupPaints`.
- **Wind Shift Optimization**: Move angle sorting and mapping out of `drawWindShifts`. Cache the `sortedAngles` and only update them when the engine history changes or when `updateState` is called with new data.
- **Path Recycling**: Ensure `portPath` and `stbdPath` are reused efficiently without re-allocation.

#### [MODIFY] [DeadReckoningMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/dr/ui/DeadReckoningMapLayer.kt)
- **Object Pre-allocation**: Pre-allocate `amberPaint`, `dashedAmberPaint`, and `fillPaint` with final colors. Avoid `Color.rgb` calls in `onDraw`.

#### [MODIFY] [NauticalMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalMapLayer.kt)
- **Bounding-Box Culling**:
    - Implement segment-level clipping for the trajectory path. Skip `getPixXFromLatLon` for points significantly outside the visible bounds.
    - Implement strict culling for vessel projections (Heading, COG, Current vectors).
- **Navigation Path Optimization**: Cache `hazardousSegments` as a `Set<Int>` in `updateState` or similar, instead of calculating it in every `onDraw`.
- **Allocation Cleanup**: Remove local `Paint` objects created in `drawNavigationPath` fallback and `drawArrowHead`.

#### [MODIFY] [NavtexMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/ui/NavtexMapLayer.kt)
- **Path Recycling**: Use a member `polygonPath` instead of creating `new Path()` for every polygon in `onDraw`.
- **Calculation Caching**: Cache `scaleFactor` and the `dist50m` calculation. Only update if `tileBox.zoom` or `tileBox.density` changes.
- **Strict Culling**: Improve `isMessageVisible` check and apply it before any coordinate transformations.

### Throttling and Invalidation (`net.osmand.plus.plugins.nautical`)

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- **Throttled Invalidation**: Implement `requestRefresh()` method using a 100ms (10Hz) debounce/throttle logic.
- **Centralized Redraw**: Redirect all `mapView.refreshMap()` calls from nautical components to this throttled method to prevent CPU starvation during high-frequency telemetry updates.

## Verification Plan

### Automated Tests
- N/A for UI performance (mostly manual verification of frame rates and allocation tracking).
- Will verify that the build still completes: `./gradlew :OsmAnd:assembleDebug` (User will run this).

### Manual Verification
- Deploy to a device/emulator.
- Enable all nautical layers (Laylines, Trajectory, Navtex, Vessel Projections).
- Simulate high-frequency SignalK updates (using the replay controls).
- Observe smoothness of map panning and rotation.
- Use Android Studio Profiler to verify that `onDraw` of modified layers has zero or near-zero allocations.
