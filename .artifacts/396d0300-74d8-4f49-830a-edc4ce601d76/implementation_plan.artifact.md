# Implementation Plan - PHASE 8.0M: Form Factor Resilience & Responsive Layouts

This plan addresses layout collapse, text truncation, and bottom sheet over-inflation in the OsmAnd Nautical plugin. It focuses on enforcing viewport constraints, refactoring canvas scaling for split-screen resilience, and ensuring typography scales correctly across different form factors and localizations.

## User Review Required

> [!IMPORTANT]
> - `GribManagerBottomSheet.kt` will be created as a new component to manage GRIB data files, as it was specifically requested but not found in the current codebase.
> - `maxHeight` for bottom sheets will be set to 60% of the screen height to ensure the map remains visible.
> - `autoSizeTextType="uniform"` will be applied to telemetry TextViews, which might slightly change their appearance if they need to shrink to fit.

## Proposed Changes

### 1. Viewport Constraints (Bottom Sheets)

Summary: Enforce `BottomSheetBehavior` constraints (maxHeight, peekHeight) in all key nautical bottom sheets.

#### [MODIFY] [TideStationBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/tide/ui/TideStationBottomSheet.kt)
- Add `onStart` override to configure `BottomSheetBehavior`.
- Set `maxHeight` to 60% of screen height.
- Set `peekHeight` to 40% of screen height.
- Ensure `STATE_COLLAPSED` is the default.

#### [MODIFY] [NauticalPilotBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotBottomSheet.kt)
- Refine `onStart` logic for `maxHeight`.
- Add explicit `peekHeight` configuration.

#### [NEW] [GribManagerBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/grib/ui/GribManagerBottomSheet.kt)
- Implement a new bottom sheet for GRIB data management with the same `BottomSheetBehavior` constraints.

---

### 2. Relative Canvas Scaling (Responsive Graphs)

Summary: Refactor custom views to use localized dimensions instead of screen-wide metrics.

#### [MODIFY] [TideGraphView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/tide/ui/TideGraphView.kt)
- Ensure all coordinate calculations in `onDraw` rely strictly on `width` and `height`.
- Remove any implicit or explicit dependencies on `DisplayMetrics.widthPixels`.

#### [MODIFY] [PolarCurveCanvasView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/editor/PolarCurveCanvasView.kt)
- Update `onDraw` and `onTouchEvent` to use `width` and `height` dynamically.
- Ensure the polar plot scales correctly when the view is resized (e.g., in split-screen).

---

### 3. Typography Scaling (Adaptive Telemetry)

Summary: Update UI layouts to use scaled density (`sp`) and auto-sizing for telemetry values.

#### [MODIFY] [nautical_tactical_hud.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/nautical_tactical_hud.xml)
- Update `rot_value`, `drift_value`, and `set_value` TextViews.
- Apply `autoSizeTextType="uniform"`.
- Ensure `textSize` is in `sp`.

#### [MODIFY] [nautical_pilot_bottom_sheet.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/nautical_pilot_bottom_sheet.xml)
- Update all `txt_value_*` TextViews in the telemetry grid.
- Apply `autoSizeTextType="uniform"` and `sp` units.
- Set `maxLines="1"` and `lines="1"` to prevent multi-line breaks for telemetry.

#### [MODIFY] [TacticalHudView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/TacticalHudView.kt)
- Programmatically ensure `TypedValue.COMPLEX_UNIT_SP` is used if any text size is set in code.

## Verification Plan

### Automated Tests
- Build check: `./gradlew :OsmAnd:assembleDebug` (via system instructions, but I won't run it myself).
- I will use `analyze_file` on all modified files to check for syntax errors.

### Manual Verification
- Render Compose Previews if applicable (not for XML/View based components).
- Review the code for logic errors in dimension calculations.
