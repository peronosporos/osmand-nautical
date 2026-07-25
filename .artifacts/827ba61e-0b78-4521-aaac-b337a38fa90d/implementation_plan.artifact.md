# S-57 Vector Map Canvas Overlay Layer Implementation Plan

Implement the final rendering layer for S-57 vector charts, integrating the parser, indexer, and stylizer into the OsmAnd map view.

## User Review Required

> [!IMPORTANT]
> The `S57MapLayer` will be integrated into the `SailingMapLayerController`.
> Zoom-level filtering will be implemented to prevent clutter at low zoom levels.

## Proposed Changes

### S-57 UI Module (`net.osmand.plus.plugins.nautical.s57.ui`)

#### [NEW] [S57MapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/ui/S57MapLayer.kt)
- Inherits from `OsmandMapLayer`.
- Manages a pool of `Paint` and `Path` objects to avoid allocations in `onDraw`.
- Queries `S57IndexManager` for features in the current viewport.
- Applies `S57FeatureStylizer` rules and `S57GeometryOptimizer` simplification.
- Handles Day/Night mode transitions.

### Controller Integration

#### [MODIFY] [SailingMapLayerController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/map/controller/SailingMapLayerController.kt)
- Add `s57Layer` as a member.
- Register/unregister the layer in the map view.

#### [MODIFY] [SailingIntegrationPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/plugin/SailingIntegrationPlugin.kt)
- Initialize `S57IndexManager`.
- Provide `S57IndexManager` to `S57MapLayer`.

## Verification Plan

### Automated Tests
- Unit tests for viewport culling logic in `S57MapLayer` (if extractable).

### Manual Verification
- Verify that S-57 features appear on the map when a `.000` file is present.
- Toggle Night mode and verify colors change.
- Zoom in and out to verify performance and feature filtering.
