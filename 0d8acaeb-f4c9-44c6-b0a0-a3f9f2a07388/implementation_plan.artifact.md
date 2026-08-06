# Fix Warnings and Implement Placeholder/Unused Code in Nautical Plugin

This plan addresses all warnings and addresses "not used" code by providing complete implementations across 12 specific Kotlin files in the Nautical plugin.

## User Review Required

> [!IMPORTANT]
> Some unused methods like `checkLookAhead` and `exportRouteGpx` will be integrated into the UI (Map Layer and Context Menu), which might add new visible elements or actions.

## Proposed Changes

### Discovery and Engine Components

#### [MODIFY] [SignalKDiscoveryManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/discovery/SignalKDiscoveryManager.kt)
- Fix deprecated `NsdServiceInfo.host` and `NsdManager.resolveService`.
- Use `registerServiceInfoCallback` for modern APIs and handle `ResolveListener` deprecation for older ones with appropriate suppressions or alternative logic if available.

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Fix unchecked casts in `loadLegacyBuffers` by using safe casting (`as?`) and explicit type checks.

#### [MODIFY] [SignalKControlManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKControlManager.kt)
- Fix unused `setAutopilotTargetHeadingMagnetic` by integrating it into `AutopilotController`.
- Add missing trailing comma.

#### [MODIFY] [AutopilotController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotController.kt)
- Use `setAutopilotTargetHeadingMagnetic` when the heading reference is set to Magnetic.
- Enhance `showArbitrationWarning` to use `activePriority` from `HelmLockedException`.

### Safety and Hazards

#### [MODIFY] [SafetyCorridorChecker.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/engine/SafetyCorridorChecker.kt)
- No code changes needed in this file itself as the methods are implemented but unused. Their usage will be implemented in `NauticalMapLayer` and `NauticalPlugin`.

#### [MODIFY] [NauticalMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalMapLayer.kt)
- Implement usage of `SafetyCorridorChecker.isPointSafe` to provide visual feedback if the vessel is in a dangerous area.
- Integrate `SafetyCorridorChecker.checkLookAhead` to warn about upcoming hazards along the current trajectory.

#### [MODIFY] [NavtexMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/ui/NavtexMapLayer.kt)
- Fix deprecated `Path.computeBounds`.
- Add clarifying parentheses and fix foldable `if-then` warnings.

#### [MODIFY] [NavtexListFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/ui/NavtexListFragment.kt)
- Implement `DiffUtil` for `NavtexAdapter` to replace inefficient `notifyDataSetChanged()`.

### UI and Widgets

#### [MODIFY] [ManeuverOverlayWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/ManeuverOverlayWidget.kt)
- Fix `setText` concatenation and string literal warnings by using resource strings with placeholders.
- Observe `ScreenTouchLockManager.isTouchLockActive` and update `touchLockText`.
- Fix missing trailing comma and add clarifying parentheses.

#### [MODIFY] [ScreenTouchLockManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/ScreenTouchLockManager.kt)
- Fix boolean literal without parameter name. usage of `isTouchLockActive` is now in `ManeuverOverlayWidget`.

#### [MODIFY] [TideViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/tide/ui/TideViewModel.kt)
- Ensure `vesselTide` is used or documented if intended for external binding. I will add a check to make sure it's properly exposed for UI.
- Fix clarifying parentheses and missing comma.

### GPX and Plugins

#### [MODIFY] [GpxStreamer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/GpxStreamer.kt)
- Fix foldable `if` in `parseGpx`.
- Usage of `exportRouteGpx` will be added to `NauticalPlugin`.

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Add "Export Route as GPX" action to the map context menu using `GpxStreamer.exportRouteGpx`.
- General cleanup and ensuring all safety checks from `SafetyCorridorChecker` are utilized.

## Verification Plan

### Automated Tests
- Since I cannot run Gradle builds or tests directly, I will rely on `analyze_file` to verify that warnings are gone.

### Manual Verification
- Verify that the "Export Route as GPX" option appears in the context menu when a route is active.
- Verify that the "LOCKED" indicator appears in the Maneuver widget when screen touch lock is active.
- Verify that safety warnings (vessel in danger area) are triggered if a hazard is intersected.
