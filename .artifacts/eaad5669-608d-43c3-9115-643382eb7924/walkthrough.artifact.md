# Walkthrough - Nautical Plugin Architectural Hygiene & Performance

The following improvements were made to the `net.osmand.plus.plugins.nautical` package to resolve architectural hygiene issues and performance bottlenecks identified in the audit.

## 1. Rendering Optimization (Zero-Allocation `onDraw`)

> [!TIP]
> Eliminating object allocations in the UI thread's rendering loop is critical for smooth map interaction, especially during high-speed panning and zooming.

- **[S57MapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/ui/S57MapLayer.kt):**
    - Moved `Path` creation and `String.format` for soundings out of `onDraw`.
    - These are now pre-calculated in the background `prepareFeatures` worker thread and stored in `PreparedGeometry`.
- **[NauticalMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalMapLayer.kt):**
    - Moved `SafetyCorridorChecker` instantiation out of the hot path.
    - Implemented `reusableWaypoints` list to avoid repeated `route.map` allocations.
- **[WeatherRoutingMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/map/layers/WeatherRoutingMapLayer.kt):**
    - Cached safety corridor results and hazardous segment indexes, only re-calculating when the route changes.

## 2. Resource Lifecycle Management (Coroutine Leaks)

> [!WARNING]
> Proper cancellation of background jobs prevents memory leaks and battery drain when plugins or features are disabled.

- **[SignalKDataBroker.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKDataBroker.kt):** Added `stop()` method to cancel its internal `CoroutineScope`.
- **[SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt):** Now explicitly stops the `dataBroker` during its own `stop()` sequence.
- **[TcpNmeaClient.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/connection/TcpNmeaClient.kt):** Added `cancelChildren()` to ensure all connection retry jobs are terminated on `disconnect()`.
- **[SignalKWebSocketClient.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/network/SignalKWebSocketClient.kt):** Added `scope.cancel()` to `disconnect()`.

## 3. Spatial Precision (JTS Fine-Grained Intersection)

- **[SafetyCorridorChecker.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/engine/SafetyCorridorChecker.kt):** Enhanced hazard detection with a secondary JTS `intersects()` check on the candidate features returned from the spatial index.
- **[IsochroneRoutingEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/routing/algorithm/IsochroneRoutingEngine.kt):** Updated `isLandCollision` to use exact JTS geometry intersection for precise land avoidance.

## 4. Architectural Cleanup

- Removed all remaining code and comment references to the deprecated `S57IndexManager`.
- Standardized usage of `S57SpatialIndex` across all nautical layers.

---
All 4 performance and hygiene criteria identified in the audit have been fully addressed.
