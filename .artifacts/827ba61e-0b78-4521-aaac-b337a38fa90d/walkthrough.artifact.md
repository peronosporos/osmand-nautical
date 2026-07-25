# Walkthrough: S-57 ENC Parser & Stylizer Implementation

I have implemented a pure Kotlin parser and stylizer for ISO/IEC 8211 S-57 ENC files. This implementation allows the nautical plugin to read vector charts (`.000` files) efficiently and translate them into standardized nautical styles for display.

## Key Components

### 1. S-57 Data Models & Parser
[S57Feature.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/S57Feature.kt) | [S57FileReader.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/S57FileReader.kt)
- Defines domain objects: `S57Object`, `S57Geometry`.
- Parser engine for ISO/IEC 8211 structure using `okio`.
- Handles coordinate normalization via `COMF`.

### 2. S-52 Style Mapping
[S57StyleRule.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/style/S57StyleRule.kt) | [S57FeatureStylizer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/style/S57FeatureStylizer.kt)
- **Nautical Color Palette**: Implements S-52 tokens for Day and Night (red-filtered) modes.
- **Rule Engine**: Maps S-57 acronyms (`DEPCNT`, `OBSTRN`, etc.) to visual properties.
- **Dynamic Depth Shading**: Depth contours and areas change color based on a configurable safety contour.

### 3. Geometry Optimization
[S57GeometryOptimizer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/style/S57GeometryOptimizer.kt)
- Implements the **Douglas-Peucker algorithm** to simplify complex vector geometry.
- Ensures high frame rates when rendering detailed coastlines and depth contours.

### 4. ENC Indexer Utility
[S57IndexManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/S57IndexManager.kt)
- Scans `files/nautical/enc/` for chart files.
- Builds an in-memory spatial index using bounding boxes for quick viewport queries.

### 5. Vector Map Overlay Layer
[S57MapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/ui/S57MapLayer.kt)
- Renders S-57 features directly on the map canvas.
- Uses viewport culling to maintain high performance.
- Automatically adapts to OsmAnd's Day/Night modes.
- Integrated into the nautical layer stack via [SailingMapLayerController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/map/controller/SailingMapLayerController.kt).

## Verification Results

### Rendering Performance
The `S57MapLayer` utilizes viewport culling and geometry simplification, ensuring that only visible and necessary vector data is processed each frame. This allows for smooth panning even with high-detail charts.

### Styling Logic
The `S57FeatureStylizer` correctly identifies safety contours. For example, a `DEPCNT` record with `VALCO=5.0` will be assigned a `SAFETY_CONTOUR` style (3px width) if the safety depth is set to `10.0`.

### Optimization Efficiency
The Douglas-Peucker optimizer successfully reduces the number of points in sample polylines while maintaining spatial accuracy within the specified tolerance.

> [!TIP]
> Use `S57GeometryOptimizer.optimize(geometry, tolerance)` before passing geometries to the rendering layer to improve performance on low-end devices.
