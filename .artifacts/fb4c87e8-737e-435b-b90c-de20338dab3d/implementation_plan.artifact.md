# Implementation Plan - Phase 8.0Q: Canvas Gestures & Touch Arbitration (Revised)

Refactor touch handling and object selection logic to ensure seamless integration with OsmAnd's native gestures while providing high-precision nautical target selection and disambiguation. This plan avoids any modifications to the AIS Tracker plugin's files.

## User Review Required

> [!IMPORTANT]
> This implementation will intercept taps at the `OnTouchListener` level in `NauticalPlugin`. If a nautical target (AIS, Navtex, or S-57) is found, the native OsmAnd POI context menu will be suppressed in favor of a nautical-optimized BottomSheet.

## Proposed Changes

### 1. Touch Event Delegation & Arbitration

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Refactor the `OnTouchListener` in `mapActivityResume`.
- Add a `NauticalTouchArbitrator` helper to manage tap detection and hit-testing.
- Logic:
  - If `event.pointerCount > 1`, return `false` immediately to allow native map panning/zooming.
  - Use a `GestureDetector` or manual timing to identify `onSingleTapConfirmed` and `onLongPress`.
  - On tap/long-press, perform hit-testing using `selectionHelper.collectObjectsFromMap`.
  - Filter results for nautical objects: `AisObject`, `NavtexMessage`, and `S57Object`.
  - If multiple targets are found, show `NauticalTargetPicker`.
  - If one target is found, show the appropriate BottomSheet (`AisTargetBottomSheet` or `NavtexDetailsBottomSheet`).
  - If handled, return `true` to suppress `ContextMenuLayer`.

### 2. Context Menu & Disambiguation UI

#### [NEW] [NauticalTargetPicker.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/NauticalTargetPicker.kt)
- Implement a `BottomSheetDialogFragment` to display a list of overlapping nautical targets.
- Sort results by Euclidean pixel distance to the touch coordinate.
- Display icons and basic info (Name/MMSI) for each target.

#### [NEW] [AisTargetBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/AisTargetBottomSheet.kt)
- Implement a nautical-optimized BottomSheet for single AIS target details (MMSI, CPA/TCPA, Vessel Info).
- This replaces the standard `AisObjectMenuController` panel with a more modern quick-action UI.

### 3. Layer Hit-Testing Refactoring

#### [MODIFY] [S57MapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/ui/S57MapLayer.kt)
- Refactor `collectObjectsFromPoint` to collect ALL intersecting features within the touch radius instead of returning after the first match.

#### [MODIFY] [NavtexMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/ui/NavtexMapLayer.kt)
- Ensure `showMenuAction` returns `true` to indicate the nautical plugin is handling the hazard display.

## Verification Plan

### Automated Tests
- Verify `NauticalTouchArbitrator` correctly identifies nautical objects from a `MapSelectionResult`.
- Verify sorting logic in `NauticalTargetPicker`.

### Manual Verification
1. **Map Panning**: Verify that two-finger panning and pinching have no "dead zones" near nautical objects.
2. **Cluster Tap**: Tap a dense area of AIS vessels and verify the `NauticalTargetPicker` appears, sorted by proximity.
3. **Single Target**: Tap an AIS vessel and verify the new `AisTargetBottomSheet` appears, and the native OsmAnd POI menu is NOT shown.
4. **Navtex/S-57**: Verify disambiguation works when a Navtex hazard overlaps an S-57 buoy.
