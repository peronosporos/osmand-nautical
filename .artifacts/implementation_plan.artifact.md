# Implementation Plan: Resource Management & Adaptive UI/UX

This plan focuses on maximizing server offloading to save Android resources and ensuring the UI dynamically adapts to the Signal K server's capabilities.

## User Review Required

> [!IMPORTANT]
> **CPA Offloading:** I will modify `NauticalAisManager` to disable its internal local CPA calculation timer if the Signal K server provides `navigation.closestApproach` (detected via `hasAisPrioritizer` or the path existing).
>
> **Widget Filtering:** I will update the `isAllowed` logic for widgets to hide hardware-specific instruments (Media, Windlass, Watermaker) when the server doesn't report these capabilities.

## Proposed Changes

### [Nautical Engine]

#### [MODIFY] [NauticalAisManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/NauticalAisManager.kt)
- Add a listener to `CapabilityManager`.
- If `hasAisPrioritizer` or `hasAdvancedSafety` is true, stop the local `cpaTimer` and rely on `MarineState.cpa` populated by the server.

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Refine `finalizeAndNotifyState` to ensure *all* derived calculations (Set/Drift, Leeway, VMG) are skipped when server counterparts exist.
- Ensure `parsingScope` and `deltaFlushJob` are handled with strict lifecycle management to prevent leaks.

### [Nautical UI]

#### [MODIFY] [WidgetType.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/WidgetType.java)
- Link `isAllowed()` for `NAUTICAL_MEDIA`, `NAUTICAL_WATERMAKER`, `NAUTICAL_ACTUATOR` to the respective `CapabilityManager` flags.

#### [MODIFY] [NauticalSettingsFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalSettingsFragment.kt)
- Dynamically hide "Autopilot Tuning", "Vessel Details", and "Switch Panel" categories if the server doesn't support those functional groups.
- Move "Boat AI" and "Checklists" into a dedicated "Smart Assistant" category that only appears when capabilities are present.

### [Code Quality]

#### [MODIFY] [MarineTextWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/MarineTextWidget.kt)
- Remove any hardcoded path logic that overlaps with standard `SignalKPaths`.
- Ensure `IntegrityState` logic uses `stalePaths` effectively to dim widgets without redundant age calculations.

## Verification Plan

### Performance Audit
- Verify that the local `cpaTimer` is NOT running when connected to a server with `collision-detector`.
- Check RAM usage with/without `hasHistory` capability active.

### UI Audit
- Verify that the "Configure Screen" list doesn't show "Fusion Media" if no media plugin is detected on the server.
