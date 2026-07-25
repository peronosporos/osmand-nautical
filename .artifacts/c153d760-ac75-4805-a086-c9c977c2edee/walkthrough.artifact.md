# Man Overboard (MOB) UI Implementation Walkthrough

Implemented the Map Canvas Overlay and High-Contrast Emergency HUD for the Man Overboard (MOB) system, completing the end-to-end emergency workflow.

## Changes Made

### Emergency HUD
- Created [mob_emergency_hud.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/mob_emergency_hud.xml) with high-contrast red background and large metrics.
- Implemented [MobEmergencyHeaderView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/mob/ui/MobEmergencyHeaderView.kt) as a custom view to manage the HUD.
- **Safety Interlock**: Added a 2-second long-press requirement for the "CANCEL MOB" button to prevent accidental resets during crisis management.
- **Auto-Silence**: The alarm can be silenced while keeping the visual metrics active.

### Map Rendering
- Implemented [MobMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/mob/ui/MobMapLayer.kt) to draw:
  - A bold red **Target Marker** (circle with crosshair) at the MOB drop location.
  - A **Dashed Return Line** from the boat's live position directly to the target.
- Optimized rendering to ensure zero main-thread stutter during map pans/zooms.

### Integration & Lifecycle
- Updated [SailingMapLayerController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/map/controller/SailingMapLayerController.kt) to register the MOB layer with a high z-index (topmost).
- Updated [SailingIntegrationPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/plugin/SailingIntegrationPlugin.kt) to initialize the MOB system when map layers are registered.
- **Power Management**: The screen is forced to stay awake (`FLAG_KEEP_SCREEN_ON`) while a MOB emergency is active.
- **Context Menu**: Updated [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt) to trigger the new MOB engine from the map's context menu.

## Verification Results

> [!IMPORTANT]
> The MOB HUD automatically appears at the top of the map when an emergency is triggered. It forces itself above standard map widgets to ensure immediate access to critical distance and bearing data.

> [!TIP]
> The screen awake flag ensures the helmsman doesn't lose visual of the return vector during critical maneuvers, even if they aren't touching the device.
