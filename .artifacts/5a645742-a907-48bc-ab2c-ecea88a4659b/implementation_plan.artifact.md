# Audit Fixes for Nautical Plugin Components

This plan addresses layout bugs, visual overlaps, missing string resources, and broken theme integrations in the Nautical Plugin.

## Proposed Changes

### 1. String Resources & Theme Attributes

#### [MODIFY] [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml)
- Add missing strings:
    - `nautical_mob_label`: "MAN OVERBOARD"
    - `s63_user_permit_label`: "S-63 User Permit"
    - `navtex_hud_urgent_title`: "URGENT NAVTEX ALERT" (used in `navtex_urgent_hud.xml`)

#### [MODIFY] [attrs.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/attrs.xml)
- Add theme attributes for maritime alerts:
    - `nautical_alert_bg` (color/reference)
    - `nautical_alert_text` (color/reference)

#### [MODIFY] [osmand_light_style.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/osmand_light_style.xml)
- Define `nautical_alert_bg` as `#FF8F00` (Amber) and `nautical_alert_text` as `#FFFFFF` (White).

#### [MODIFY] [osmand_dark_style.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/osmand_dark_style.xml)
- Define `nautical_alert_bg` as `@color/marker_red` (or similar dark amber) and `nautical_alert_text` as `#FFFFFF`.

---

### 2. UI Layout & HUD Stacking

#### [MODIFY] [navtex_urgent_hud.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/navtex_urgent_hud.xml)
- Use theme attributes `?attr/nautical_alert_bg` and `?attr/nautical_alert_text` instead of hardcoded hex values.

#### [MODIFY] [SailingIntegrationPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/plugin/SailingIntegrationPlugin.kt)
- Fix HUD stacking logic in `initNavtexSystem`, `initDrSystem`, and `initMobSystem`. Instead of always using index `0` (which causes overlaps), use `hudContainer.childCount` or a dedicated vertical layout if possible. Given the current structure, adding to the end of the container is safer.

---

### 3. Map Layers & Z-Order

#### [MODIFY] [SailingMapLayerController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/map/controller/SailingMapLayerController.kt)
- Adjust Z-orders to avoid collisions with core OsmAnd layers:
    - `navtexLayer`: Change from `7.0f` to `7.5f` (above standard navigation).
    - `drLayer`: Change from `6.0f` to `6.5f` (above location layer).

---

### 4. Night Mode Compliance in Drawing

#### [MODIFY] [NavtexMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/ui/NavtexMapLayer.kt)
- Remove hardcoded `Color.RED` and amber hex.
- Use `settings.isNightMode` in `drawWarningMarker` to select appropriate colors (e.g., using `NauticalColor` enum or theme colors).

#### [MODIFY] [AnchorWatchMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/anchor/AnchorWatchMapLayer.kt)
- Update `boundaryPaint` and `boundaryFillPaint` to respect `settings.isNightMode`.
- Use translucent red for night and a less intense red/orange for day.

---

### 5. Settings & Hardcoded String Cleanup

#### [MODIFY] [nautical_settings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/xml/nautical_settings.xml)
- Remove redundant `s63_permit_manager` preference entries.

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Use `R.string.nautical_mob_label` in `registerMapContextMenuActions`.

#### [MODIFY] [S63PermitManagerFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s63/ui/S63PermitManagerFragment.kt)
- Use `R.string.s63_user_permit_label` for clipboard label.

## Verification Plan

### Automated Tests
- No specific automated tests required for these UI/UX fixes, but will ensure the project builds successfully.

### Manual Verification
- Mentally verify Z-order hierarchy against `MapLayers.java` constants.
- Verify string keys match between XML and code.
- Verify theme attributes are correctly applied in layout XML.
