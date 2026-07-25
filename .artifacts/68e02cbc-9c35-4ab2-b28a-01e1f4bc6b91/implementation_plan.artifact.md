# Implementation Plan - Navtex UI Layer (HUD and Map Layer)

Implement the user-facing Alert HUD and the Spatial Map Marker Layer for Navtex safety broadcasts.

## Proposed Changes

### Nautical Resources
#### [MODIFY] [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml)
- Add `navtex_hud_urgent_title`: "URGENT NAVTEX WARNING"
- Add `navtex_layer_name`: "Maritime Safety Broadcasts (NAVTEX)"
- Add `navtex_dialog_title`: "Navtex Details"

#### [NEW] [navtex_urgent_hud.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/navtex_urgent_hud.xml)
- Layout for the high-contrast alert banner.

#### [NEW] [bottom_sheet_navtex_details.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/bottom_sheet_navtex_details.xml)
- Layout for the Navtex message details bottom sheet.

### Nautical Hazard UI Layer
#### [NEW] [NavtexMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/ui/NavtexMapLayer.kt)
- Implement `OsmandMapLayer` to render Navtex markers.
- Render amber/red triangles or warning icons at Navtex coordinates.
- Handle single tap to show `NavtexDetailsBottomSheet`.

#### [NEW] [NavtexHudView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/ui/NavtexHudView.kt)
- Custom view for the map HUD alert banner.
- High-contrast (amber/red) styling.
- Observe `NavtexViewModel` for urgent messages.

#### [NEW] [NavtexDetailsBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/ui/NavtexDetailsBottomSheet.kt)
- `BottomSheetDialogFragment` to display Navtex message details.

### Nautical Integration
#### [MODIFY] [SailingDependencyContainer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/di/SailingDependencyContainer.kt)
- Add `NavtexRepository` singleton (lazy).

#### [MODIFY] [SailingMapLayerController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/map/controller/SailingMapLayerController.kt)
- Add `NavtexMapLayer` instance.
- Register/unregister `NavtexMapLayer` in `mapView`.

#### [MODIFY] [SailingIntegrationPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/plugin/SailingIntegrationPlugin.kt)
- Implement `initNavtexSystem`.
- Instantiate `NavtexViewModel` and `NavtexHudView`.
- Add HUD view to `map_hud_container`.
- Link ViewModel state to HUD and Map Layer.

## Verification Plan

### Manual Verification
- Deploy to a device or emulator.
- Use `NavtexSentenceParser` to inject an urgent message with coordinates (simulated via replay or test injection).
- Verify the urgent HUD alert appears at the top of the map.
- Verify the Navtex marker appears on the map at the correct coordinates.
- Tap the marker/HUD and verify the `NavtexDetailsBottomSheet` opens with the correct message content.
- Verify dismissing/acknowledging the warning behaves as expected.
