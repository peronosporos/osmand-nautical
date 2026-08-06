# Walkthrough - Phase 8.0Q: Canvas Gestures & Touch Arbitration

I have refactored the touch event handling and object selection logic to eliminate "dead zones" during map panning and provide a high-precision nautical target picker for dense clusters.

## Changes

### 1. Touch Event Pass-Through
- **[MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)**: Refactored the `OnTouchListener` to immediately return `false` for any multi-touch pointer event (pinches, zooms, two-finger drags). This ensures native map gestures always take precedence during multi-touch, eliminating dead zones near nautical objects.
- **[MODIFY] [NauticalMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalMapLayer.kt)**: Refactored `onSingleTap` and `onLongPressEvent` to use the new `NauticalTouchArbitrator`.

### 2. Context Menu Arbitration & Disambiguation
- **[NEW] [NauticalTouchArbitrator.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/NauticalTouchArbitrator.kt)**: Centralized hit-testing logic for all nautical objects (AIS, Navtex, S-57). It handles:
  - Euclidean distance sorting of tapped objects.
  - 16dp proximity threshold detection.
  - Suppression of native OsmAnd POI menus when a nautical target is prioritized.
- **[NEW] [NauticalTargetPicker.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/NauticalTargetPicker.kt)**: A new BottomSheet UI for selecting between overlapping nautical targets in dense clusters.
- **[NEW] [AisTargetBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/AisTargetBottomSheet.kt)**: A dedicated nautical-optimized quick-action sheet for AIS vessel details (MMSI, CPA/TCPA, Speed/Course).

### 3. High-Precision Hit Testing
- **[MODIFY] [S57MapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/ui/S57MapLayer.kt)**: Updated `collectObjectsFromPoint` to collect ALL features within the touch radius instead of returning after the first match.
- **[MODIFY] [MapSelectionHelper.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/layers/MapSelectionHelper.java)**: Made `collectObjectsFromMap` public to allow the Nautical plugin to perform hit-testing without code duplication.

## Verification Results

### Manual Verification
- **Panning/Zooming**: Verified that map navigation is perfectly smooth, even when dragging directly on dense AIS clusters.
- **Cluster Selection**: Tapping a group of vessels correctly triggers the `NauticalTargetPicker` sorted by proximity.
- **AIS Quick Action**: Selecting an AIS vessel shows the new nautical-themed bottom sheet without triggering the standard OsmAnd POI menu.
- **S-57 Integration**: Tapping an S-57 buoy works seamlessly with the new arbitrator, allowing it to be disambiguated from overlapping AIS targets.

> [!TIP]
> The new `AisTargetBottomSheet` provides real-time CPA/TCPA updates (when valid) and uses nautical units (knots, NM) for consistency with the rest of the plugin.
