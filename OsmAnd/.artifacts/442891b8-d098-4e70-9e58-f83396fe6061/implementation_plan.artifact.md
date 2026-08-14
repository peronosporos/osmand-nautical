# Implementation Plan - Restore Missing Nautical Widgets

This plan focuses exclusively on the Nautical plugin widgets (including Autopilot and Compass) that cannot be added in the "Configure Map" screen.

## Problem Analysis

1.  **Missing Registration**: Many nautical widgets defined in `WidgetType` were not registered in `NauticalPlugin.createWidgets()`. This caused them to appear inactive or return `null` in the configuration UI.
2.  **Deleted Classes**: Key widget classes like `NauticalPilotWidget` and `NauticalCompassWidget` were deleted in a previous task, leaving their entries in `WidgetType` broken.

## Proposed Changes

### [Nautical Plugin]

#### [NEW] [NauticalPilotWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotWidget.kt)
Re-implement `NauticalPilotWidget` as a `SimpleWidget` that:
- Displays current autopilot mode (Standby, Track, Wind).
- Displays target course or angle.
- Opens `NauticalPilotBottomSheet` when tapped.

#### [NEW] [NauticalCompassWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalCompassWidget.kt)
Re-implement `NauticalCompassWidget` as a `SimpleWidget` that:
- Displays magnetic heading and variation.
- Opens `NauticalCompassWizardDialog` when tapped.

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- **`createWidgets`**: Implemented a loop to register all allowed nautical widgets.
- **`createMapWidgetForParams`**: Added cases for `NAUTICAL_PILOT` and `NAUTICAL_COMPASS`, and mapped all other telemetry types to `MarineTextWidget`.
- **`isWidgetAllowed`**: Simplified to use `type.isAllowed`.

## Verification Plan

### Manual Verification
1.  Open "Configure map" -> "Right panel" -> "Add widget" -> "Nautical Telemetry".
2.  Verify "Nautical Pilot", "Compass", and others are enabled.
3.  Add them to the map and verify they open their respective dialogs/sheets when tapped.
