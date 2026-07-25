# Implementation Plan - MOB UI (Map Layer and HUD)

Implement the Map Canvas Overlay and High-Contrast Emergency HUD for the Man Overboard (MOB) system.

## Proposed Changes

### [Nautical MOB UI]

#### [NEW] [mob_emergency_hud.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/mob_emergency_hud.xml)
- High-contrast layout with large font metrics for Distance, Bearing, and ETA.
- "SILENCE ALARM" button.
- "CANCEL MOB" button with 2-second hold requirement.

#### [NEW] [MobMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/mob/ui/MobMapLayer.kt)
- Draws MOB target icon at drop location.
- Draws high-visibility dashed return line from boat to target.
- Observes `MobViewModel.uiState`.

#### [NEW] [MobEmergencyHeaderView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/mob/ui/MobEmergencyHeaderView.kt)
- Custom view that manages the emergency HUD.
- Updates metrics from `MobViewModel`.
- Handles button clicks and long-press logic.
- Manages `FLAG_KEEP_SCREEN_ON`.

#### [MODIFY] [SailingMapLayerController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/map/controller/SailingMapLayerController.kt)
- Register `MobMapLayer` with the map view.
- Ensure it has a high z-order.

#### [MODIFY] [SailingIntegrationPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/plugin/SailingIntegrationPlugin.kt) or [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Initialize `MobViewModel` and its dependencies.
- Manage the visibility of the `MobEmergencyHeaderView` in `MapActivity`.

## Verification Plan

### Manual Verification
- Trigger MOB from map context menu.
- Verify HUD appears with correct metrics.
- Verify red marker and dashed line appear on map.
- Verify "Silence Alarm" stops audio.
- Verify "Cancel MOB" requires long press and clears state.
- Verify screen stays awake during active MOB.
