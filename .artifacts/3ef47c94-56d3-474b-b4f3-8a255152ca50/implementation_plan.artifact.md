# Nautical Plugin Comprehensive Fixes Plan

This plan compiles all identified bugs, performance issues, and UX improvements discussed. The goal is to stabilize the Nautical plugin, optimize its performance, and align its behavior with OsmAnd's professional standards.

## 1. Bug Fixes (Stability & Resources)

### [Component] Resources
- **[NEW] [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml)**: Add `nautical_steer_here` (value: "Steer here") to prevent `Resources.NotFoundException`.
- **[NEW] [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml)**: Add `nautical_undo` (value: "Undo").

### [Component] Connectivity
- **[MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)**:
    - Fix `reconnect()` to call `connection.disconnect()` first.
    - Ensure "Steer here" is visible in the context menu if the plugin is enabled, providing feedback if the autopilot is not yet connected instead of silently hiding the option.
- **[MODIFY] [NauticalLocationProvider.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/NauticalLocationProvider.kt)**: Wrap GPS muting in a lifecycle-safe handler to ensure hardware GPS is always restored when the plugin is disabled or the activity finishes.

## 2. Performance Optimizations

### [Component] Map Rendering
- **[MODIFY] [NauticalMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalMapLayer.kt)**:
    - Implement screen-pixel path caching for the 1000-point trajectory.
    - Only rebuild the path when zoom level changes or new points are added (currently it rebuilds every frame during any map movement).

### [Component] Engine & Data
- **[MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)**:
    - Move JSON parsing from the WebSocket thread to `Dispatchers.Default` using a coroutine.
    - Consolidate 40+ history buffer files (`.dat`) into a single `marine_history.bin` using a unified `DataOutputStream` approach.

### [Component] UI/Widgets
- **[MODIFY] [NauticalGraphWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalGraphWidget.kt)**:
    - Pass raw history lists to `NauticalGraphView`.
- **[MODIFY] [NauticalGraphView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalGraphView.kt)**:
    - Perform coordinate conversion and min/max calculation within `onDraw` to avoid high-frequency GC allocations caused by `.map {}` calls in the widget.

## 3. UX & Engineering Practices

### [Component] Map Interaction
- **[MODIFY] [NauticalMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalMapLayer.kt)**:
    - Remove the broken "Immediate Waypoint" logic in `onLongPressEvent`. Rely on the Map Context Menu as the single source of truth for autopilot commands.
    - Add haptic feedback to autopilot-related menu actions.

### [Component] Theming & Style
- **[MODIFY] [NauticalPilotWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotWidget.kt)**: Replace hardcoded `Color.BLACK` and `Color.MAGENTA` with theme-aware attributes (`?attr/active_color_primary`, etc.).

### [Component] Code Health
- **[MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)**: Replace reflection-based `PLUGINS` preference listener with the public `app.plugins.addPluginListener()` API.

## Verification Plan

### Automated Tests
- `SignalKEngineTest`: Test parsing reliability and buffer consolidation.
- `CircularBufferTest`: Verify no memory leaks on overflow.

### Manual Verification
1. Open Map Context Menu -> Actions: Verify "Steer here" is present and functional.
2. Verify that disabling the Nautical plugin immediately restores the device's hardware GPS icon/status.
3. Verify that history graphs (Depth, Wind, etc.) update smoothly without UI jank.
4. Verify Night Vision mode correctly colors all layers in high-contrast red.
