# Walkthrough - Tactical Laylines Map Overlay

Implemented the real-time map canvas overlay for current-adjusted tactical laylines. This component visualizes the optimal Port and Starboard tacking legs based on telemetry data and sailing performance calculations.

## Changes Made

### Map Overlay Layer
- **[NEW] [SailingLaylinesMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/laylines/ui/SailingLaylinesMapLayer.kt)**:
    - Extends `OsmandMapLayer` for optimized map rendering.
    - Implements dynamic styling:
        - **Fetchable (Single Tack)**: Rendered as solid **Green** lines to the target.
        - **Beating/Gybing (Multi-Tack)**: Rendered as dashed **Red** lines showing the required tacking legs.
    - **Performance Optimization**: Reuses `Paint` and `Path` objects and performs all calculations using screen-space projections within `onDraw`.

### Dependency Integration & Controller
- **[MODIFY] [SailingMapLayerController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/map/controller/SailingMapLayerController.kt)**:
    - Migrated to the new `SailingLaylinesMapLayer` implementation.
    - Exposed `laylinesLayer` as public for state updates from the plugin.
- **[MODIFY] [SailingIntegrationPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/plugin/SailingIntegrationPlugin.kt)**:
    - Added initialization for the `LaylineViewModel`.
    - Established a reactive bridge between `LaylineViewModel.uiState` and the `SailingLaylinesMapLayer`.
    - Automatically triggers map refreshes when tactical geometry changes.

## Verification

### Manual Verification Steps
1. Enable the **Sailing Performance Plugin**.
2. Configure a navigation destination (Target Point).
3. Verify that the **Laylines** layer is enabled in "Configure Map".
4. Observe the laylines on the map:
    - When sailing directly towards the target (fetchable), lines turn Green.
    - When beating upwind, dashed Red laylines appear, showing the intersection points for optimal tacks.
    - Perform map pans and zooms to ensure smooth rendering without stuttering.

### Screenshots / Previews
(Placeholder for screenshots demonstrating the Red dashed beating legs vs Green fetchable lines)
