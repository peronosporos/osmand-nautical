# Audit Fixes Tasks

- [x] **Phase 1: Resources & Theming**
    - [x] Update `strings.xml` with missing labels
    - [x] Define `nautical_alert_bg/text` attributes in `attrs.xml`
    - [x] Apply theme attributes in `osmand_light_style.xml` and `osmand_dark_style.xml`
- [x] **Phase 2: UI & HUD Fixes**
    - [x] Update `navtex_urgent_hud.xml` to use theme attributes
    - [x] Fix HUD stacking in `SailingIntegrationPlugin.kt`
- [x] **Phase 3: Map Layers**
    - [x] Adjust Z-orders in `SailingMapLayerController.kt`
    - [x] Fix Night Mode in `NavtexMapLayer.kt`
    - [x] Fix Night Mode in `AnchorWatchMapLayer.kt`
- [x] **Phase 4: Settings & Cleanup**
    - [x] Remove redundant entries in `nautical_settings.xml`
    - [x] Use string resources in `NauticalPlugin.kt` context menu
    - [x] Use string resources in `S63PermitManagerFragment.kt`
