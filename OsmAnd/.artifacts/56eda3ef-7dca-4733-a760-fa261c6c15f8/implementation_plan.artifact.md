# Implementation Plan - Nautical UI Streamlining & De-cluttering

This plan aims to restore the "clean OsmAnd way" to the nautical telemetry UI by removing intrusive elements, complex HUD components, and redundant graphical layering that cause visual clutter and map occlusion.

## User Review Required

> [!IMPORTANT]
> - **Widget Removal**: The following widgets will be **removed entirely** from the Map HUD to reduce clutter and redundancy:
>     - `NauticalPilotWidget` (Redundant, controls are in the Bottom Sheet).
>     - `NauticalCompassWidget` (Redundant, native OsmAnd compass is sufficient).
>     - `NauticalGraphWidget` (Redundant, history graphs are better suited for the Bottom Sheet).
> - **Standardization**: `ActuatorLoadWidget` will be converted from a custom vertical layout with progress bars to a standard OsmAnd `SimpleWidget` (horizontal icon + text).
> - **Telemetry Grid**: All "graphical layering" (Sparklines and Mini-Roses) will be removed from the grid items.
> - **Integrity Signaling**: All intrusive "Alarm" states (flashing red, strike-through text, "STALE" badges) will be replaced with subtle icon color changes.

## Proposed Changes

### 1. HUD Cleanup & Redundancy Removal
#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Remove `WidgetType.NAUTICAL_PILOT`, `WidgetType.NAUTICAL_COMPASS`, `WidgetType.NAUTICAL_DEPTH` (graph version), and `WidgetType.NAUTICAL_WIND` (graph version) from `createMapWidgetForParams`.
- This ensures these complex or redundant widgets cannot be added to the map.

#### [DELETE] [NauticalPilotWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/widgets/NauticalPilotWidget.kt)
#### [DELETE] [map_hud_pilot_widget.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/map_hud_pilot_widget.xml)
#### [DELETE] [NauticalCompassWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalCompassWidget.kt)
#### [DELETE] [widget_nautical_compass.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/widget_nautical_compass.xml)
#### [DELETE] [NauticalGraphWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalGraphWidget.kt)
#### [DELETE] [widget_nautical_graph.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/widget_nautical_graph.xml)

### 2. Widget Standardization (Actuator Load)
#### [MODIFY] [ActuatorLoadWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/ActuatorLoadWidget.kt)
- Refactor to inherit from `SimpleWidget` without a custom layout.
- Use `setText(load, current)` to display data in the standard icon + two-line text format.

#### [DELETE] [map_hud_actuator_widget.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/map_hud_actuator_widget.xml)

### 3. Telemetry Grid Simplification
#### [MODIFY] [NauticalTelemetryGridBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalTelemetryGridBottomSheet.kt)
- Remove `bindGraphicalViews` and all logic related to `NauticalSparklineView` and `NauticalMiniRoseView`.
- Ensure `textContainer` is always visible and icons are always shown (no hiding icons for roses).

#### [MODIFY] [item_nautical_telemetry_grid.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/item_nautical_telemetry_grid.xml)
- Remove `NauticalSparklineView` and `NauticalMiniRoseView` from the layout.
- Clean up the `FrameLayout` to prioritize the central text and icon.

### 4. Data Integrity & Signaling
#### [MODIFY] [MarineTextWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/MarineTextWidget.kt)
- **Integrity Styling**: Update `applyIntegrityStyling` to:
    - Remove strike-through text.
    - Remove red background flashing/pulsing and background color changes.
    - Use only icon color changes (Yellow for Stale, Red for Alarm) and alpha reduction (0.5f) to signal status.

### 3. Hardware Widget Consistency
#### [MODIFY] [map_hud_actuator_widget.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/map_hud_actuator_widget.xml)
- Refactor to a horizontal `LinearLayout` that matches the standard OsmAnd widget style.
- Remove the embedded progress bar if it's too distracting, or make it a very thin divider.

#### [MODIFY] [ActuatorLoadWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/ActuatorLoadWidget.kt)
- Update to use the simplified horizontal layout.
- Ensure it uses standard `setText` methods from `SimpleWidget` for consistency.

## Verification Plan

### Manual Verification
1.  Deploy the app and enable Nautical Plugin.
2.  Engage Autopilot and verify the map HUD remains clean (no nudge buttons).
3.  Simulate a SignalK disconnection and verify that widgets show a subtle status (e.g., yellow icon) instead of flashing red/strike-through.
4.  Check the Autopilot Bottom Sheet to ensure all control functionality is still accessible.
