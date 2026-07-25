# Implementation Plan - Nautical Plugin Architectural Cleanup & Performance Fixes

This plan addresses the findings of the architectural hygiene and performance audit.

## User Review Required

> [!IMPORTANT]
> The fixes include significant changes to the map rendering logic and coroutine lifecycle management.
> These changes are designed to improve performance and prevent memory leaks.

## Proposed Changes

### 1. Clean Up `S57IndexManager` References
Replace all remaining references to the deleted `S57IndexManager` with `S57SpatialIndex`.

#### [MODIFY] [NauticalMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalMapLayer.kt)
- Update `s57IndexManager` to `s57SpatialIndex`.

#### [MODIFY] [WeatherRoutingMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/map/layers/WeatherRoutingMapLayer.kt)
- Update `s57IndexManager` to `s57SpatialIndex`.

---

### 2. Fix Rendering Allocations in `onDraw()`
Optimize `onDraw` methods to eliminate per-frame allocations.

#### [MODIFY] [S57MapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/ui/S57MapLayer.kt)
- Update `PreparedFeature` to include pre-formatted text (for soundings) and pre-calculated `Path` objects.
- Move `getPathFromGeometry` and `String.format` calls into the background `prepareFeatures` task.
- Update `onDraw` to use these pre-calculated values.

#### [MODIFY] [NauticalMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalMapLayer.kt)
- Move `SafetyCorridorChecker` instantiation out of `onDraw` (e.g., to a lazy property or initialized during `needsRecheck`).
- Cache `Waypoint` list conversion.
- Re-use `trajectoryPath` effectively (already mostly done but needs verification).

---

### 3. Fix Coroutine Resource Leaks
Implement proper teardown for classes managing `CoroutineScope`.

#### [MODIFY] [SignalKDataBroker.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKDataBroker.kt)
- Add a `stop()` or `cancel()` method that calls `scope.cancel()`.
- Ensure it's called from `SignalKEngine.stop()`.

#### [MODIFY] [TcpNmeaClient.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/connection/TcpNmeaClient.kt)
- Update `disconnect()` to call `job?.cancel()` and also manage the lifecycle of the passed-in `scope` if appropriate, or ensure the local job is fully cleaned up.

#### [MODIFY] [SignalKWebSocketClient.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/network/SignalKWebSocketClient.kt)
- Add `scope.cancel()` to `disconnect()`.

---

### 4. Restore Fine-Grained JTS Intersection Precision
Enhance spatial safety checks with exact geometry intersections.

#### [MODIFY] [SafetyCorridorChecker.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/engine/SafetyCorridorChecker.kt)
- After querying `candidates` from `indexManager`, add a filter step using `it.geometries.any { g -> g.toJtsGeometry(geometryFactory)?.intersects(corridor) == true }`.

#### [MODIFY] [IsochroneRoutingEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/routing/algorithm/IsochroneRoutingEngine.kt)
- Update `isLandCollision` to perform an exact `intersects` check against the JTS geometry of the land features.

## Verification Plan

### Automated Tests
- N/A (Project doesn't allow running tests easily here, will perform manual code verification).

### Manual Verification
- Static analysis of the modified code to ensure no `onDraw` allocations.
- Verification that all `cancel()` calls are in place.
- Verification that `intersects()` is used correctly.
