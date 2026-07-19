# Implementation Plan - Perfecting Nautical Integration

A final polish of the Nautical plugin to ensure absolute consistency, robustness, and full support for platform features like Night Vision.

## User Review Required

> [!IMPORTANT]
> This plan includes a major cleanup of redundant code in `MarineTextWidget` and a visual unification of the Autopilot dashboard with the map HUD. It also ensures that the Night Vision (red filter) is consistently applied to all nautical interfaces.

## Proposed Changes

### [Component Name] UI Polish & Consistency

#### [MODIFY] [MarineTextWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/MarineTextWidget.kt)
- Remove duplicate handler functions.
- Ensure all handlers use the unified unit resources.
- Standardize precision (e.g., 3 decimals for XTE, 2 for DTW, 1 for most others).

#### [MODIFY] [NauticalPilotBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotBottomSheet.kt)
- Refactor `updateTelemetryGrid` to use the same icons and unit strings as the map widgets.
- Replace hardcoded suffixes ("NM", "%", etc.) with localized string resources.
- Synchronize conversion logic (e.g., using `SpeedConstants.KNOTS`) with the rest of the plugin.

#### [MODIFY] [NauticalDataBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalDataBottomSheet.kt)
- Apply `NauticalPlugin.applyNightVisionFilter(view)` in `onViewCreated` to ensure the red filter is active when Night Vision is enabled.
- Ensure titles are always consistent with the instrument names in the selection menu.

### [Component Name] Data & Robustness

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Add a safety check for `NaN` values before adding to `CircularBuffer`.
- Ensure all history buffers are correctly initialized and persisted.

## Verification Plan

### Automated Tests
- N/A

### Manual Verification
- **Night Vision**: Enable Night Vision and open the Telemetry Graph bottom sheet; verify it's correctly filtered in red.
- **Consistency**: Open the Autopilot bottom sheet and verify that units (kn, NM) and icons match the map HUD.
- **Accuracy**: Compare values between map HUD and bottom sheets to ensure identical conversion logic.
