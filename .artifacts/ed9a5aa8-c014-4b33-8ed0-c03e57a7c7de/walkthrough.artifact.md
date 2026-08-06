# Walkthrough - Fix Nautical Night Vision Widget Compilation Error

The compilation error was caused by a reference to a non-existent class `NauticalNightVisionWidget` in `MapWidgetsFactory.java`.

## Changes

### `:OsmAnd`

#### [MapWidgetsFactory.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/MapWidgetsFactory.java)

Removed redundant nautical widget cases from the `createMapWidget` method. These widgets are already correctly handled by `NauticalPlugin.kt` through the `PluginsHelper.createMapWidget()` call in the `default` case of the switch statement.

The removed cases include:
- `NAUTICAL_DEPTH`
- `NAUTICAL_WIND`
- `NAUTICAL_VMG`
- `NAUTICAL_COG`
- `NAUTICAL_NIGHT_VISION` (This was the source of the `cannot find symbol` error)
- `NAUTICAL_PILOT`
- `NAUTICAL_ACTUATOR`
- `NAUTICAL_MASTER_TELEMETRY`
- `NAUTICAL_CAMERA`

## Verification Results

### Static Analysis
- Ran `analyze_file` on `MapWidgetsFactory.java`, which reported no compilation errors.
- Verified that `NauticalPlugin.kt` correctly implements `createMapWidgetForParams` for all these widget types, ensuring that the functionality is preserved and consistent.
