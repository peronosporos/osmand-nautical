# Heavy Data Render Pipeline Optimization

This plan addresses UI thread starvation and spatial query bottlenecks in heavy data map layers (`WeatherRoutingMapLayer`, `OceanographicGribMapLayer`, `S57MapLayer`, and `TidalCurrentsMapLayer`).

## Proposed Changes

### [Asynchronous Grid & Isochrone Processing]

#### [MODIFY] [WeatherRoutingMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/map/layers/WeatherRoutingMapLayer.kt)
- Introduce `WeatherRoutingRenderCache` to store pre-calculated `Path` objects for the optimal route and isochrone rings.
- Use `layerScope.launch(Dispatchers.Default)` to refresh the cache when `optimalRouteResult` or map bounds change.
- `onDraw()` will strictly draw cached paths using `canvas.drawPath()`.

#### [MODIFY] [OceanographicGribMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/map/layers/OceanographicGribMapLayer.kt)
- Create `GribRenderCache` containing prepared isobar paths and a list of wave vector data (pre-formatted strings and orientation).
- Implement a background worker to iterate the GRIB grid and populate the cache.
- Remove `String.format` and repository queries from `onDraw()`.

### [Frustum Culling & S-57 Unblocking]

#### [MODIFY] [S57MapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/ui/S57MapLayer.kt)
- Replace `executor.execute` with `CoroutineScope(Dispatchers.Default)` for better lifecycle management.
- Implement strict spatial filtering using `indexManager.queryFeatures` before any geometry processing.
- Optimize `PreparedGeometry` to handle cached paths efficiently without synchronous fallback locks.

#### [MODIFY] [TidalCurrentsMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/tide/map/TidalCurrentsMapLayer.kt)
- Implement spatial indexing for tide stations.
- Move tidal height and velocity calculations to a background coroutine.
- Cache prepared arrow paths and properties for the current view.

## Verification Plan

### Automated Tests
- N/A for UI optimization, manual verification is preferred.

### Manual Verification
- Deploy to device and observe frame rates while panning/zooming over heavy data areas (isochrones, GRIB vectors, S-57 soundings).
- Verify that there is no UI stuttering during background data preparation.
