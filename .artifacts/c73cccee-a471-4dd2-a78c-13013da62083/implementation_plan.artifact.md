# Implementation Plan - CORRECTION: Batch 2 Platform Integration & Lifecycle

This plan fixes critical gaps in OsmAnd platform integration, expanding the VHF tag schema and ensuring correct component lifecycles and dynamic scaling.

## User Review Required

> [!IMPORTANT]
> **VHF Context Menu**: VHF channel information will now be injected into the native OsmAnd context menu for any POI containing marine communication tags.
> **NAVTEX Scaling**: Hazard rendering will now scale dynamically with zoom to ensure visibility.

## Proposed Changes

### 1. Expanded VHF Tag Schema & Context Hooks
Enhance `VhfPoiSearchLayer.kt` to act as a proper platform citizen.

#### [MODIFY] [VhfPoiSearchLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/poi/ui/VhfPoiSearchLayer.kt)
- **Tag Expansion**: Add support for `seamark:harbour:radio:channel`, `radio:channel`, and `vhf` tags.
- **Context Menu Provider**:
    - Implement `registerContextMenuActions()` to add VHF info to the selection dialog.
    - Implement `collectContextMenuItems()` to populate the action list.
- **Improved Name Extraction**: Dynamically build the object name using the first available radio tag.

---

### 2. NAVTEX HUD Lifecycle Fix
Ensure the HUD ticker doesn't leak memory or update when detached.

#### [MODIFY] [NavtexHudView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/ui/NavtexHudView.kt)
- **Coroutine Scoping**: Migrate from `Handler` to `CoroutineScope` tied to the view lifecycle.
- **Lifecycle Hooks**: Cancel ticker jobs in `onDetachedFromWindow()`.
- **Priority Refresh**: Ensure display updates immediately when priority messages arrive.

---

### 3. Dynamic Hazard Scaling
Improve legibility of NAVTEX hazard warnings across all zoom levels.

#### [MODIFY] [NavtexMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/ui/NavtexMapLayer.kt)
- **Spatial Scaling**: Use `RotatedTileBox.getDistance()` to calculate dynamic stroke widths and marker sizes.
- **Visibility Assurance**: Ensure polygon borders remain clear and markers don't obscure map details at high zoom, while remaining visible at low zoom.

---

### 4. Integration Wiring

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Explicitly call `VhfPoiSearchLayer.registerContextMenuActions()` in the plugin's context menu hook.

## Verification Plan

### Manual Verification
- **Context Menu**: Long-press a marina node and verify "VHF Channel" info appears in the native menu.
- **HUD Leak Test**: Rapidly open/close map settings and verify no background ticker coroutines remain active (via logs).
- **Zoom Scaling**: Zoom from level 10 to 18 on a NAVTEX hazard area and verify border stroke and marker visibility.
