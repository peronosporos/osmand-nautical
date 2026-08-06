# Walkthrough - PHASE 8.0M: Form Factor Resilience & Responsive Layouts

This phase has improved the resilience and responsiveness of the OsmAnd Nautical plugin across various form factors, including split-screen and different display densities.

## Changes Made

### 1. Viewport Constraints & Bottom Sheet Resilience
- **Constraint Enforcement**: Added `BottomSheetBehavior` configuration to key bottom sheets to prevent them from obscuring the entire map canvas.
    - [TideStationBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/tide/ui/TideStationBottomSheet.kt): Set `maxHeight` to 60% and `peekHeight` to 40% of screen height.
    - [NauticalPilotBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotBottomSheet.kt): Standardized behavior with similar constraints.
- **New Component**: Created [GribManagerBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/grib/ui/GribManagerBottomSheet.kt) and its layout [bottom_sheet_grib_manager.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/bottom_sheet_grib_manager.xml) to manage GRIB weather data files with resilient viewport constraints.

### 2. Responsive Canvas Scaling
- **Localized Coordinates**: Refactored custom views to rely on their own dimensions rather than global display metrics, ensuring perfect scaling in Android Split-Screen mode.
    - [TideGraphView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/tide/ui/TideGraphView.kt): Updated `onDraw` to use dimensions captured in `onSizeChanged`.
    - [PolarCurveCanvasView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/editor/PolarCurveCanvasView.kt): Migrated coordinate logic and touch event handling to use localized cached width/height.

### 3. Adaptive Typography Scaling
- **Telemetry Uniformity**: Applied `autoSizeTextType="uniform"` to critical telemetry displays to prevent text truncation in localized translations or when rendering large values.
    - [nautical_tactical_hud.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/nautical_tactical_hud.xml): Added auto-sizing to ROT, Drift, and Set values.
    - [nautical_pilot_bottom_sheet.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/nautical_pilot_bottom_sheet.xml): Updated the telemetry grid (SOG, STW, etc.) with auto-sizing TextViews.
- **SP Unit Enforcement**: Standardized on `sp` units for typography and ensured dynamic text in custom views (like `TideGraphView`) scales with system font settings.

## Verification Results

### Automated Tests
- **Static Analysis**: Ran `analyze_file` on all modified Kotlin and XML files. All syntax errors were resolved, and minor warnings (unused imports/properties) were cleaned up.
- **Namespace Integrity**: Verified `xmlns:app` bindings in XML layouts to support modern layout attributes.

### Manual Verification
- Reviewed layout logic for `maxHeight` calculations ensuring the `60%` threshold correctly preserves map visibility.
- Verified that `TideGraphView` now uses `TypedValue.applyDimension` with `COMPLEX_UNIT_SP` for labels, ensuring consistency with system typography scales.
