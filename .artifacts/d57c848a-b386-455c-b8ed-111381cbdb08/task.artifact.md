# Task List - Phase 8.0P: Accessibility, RTL & Offline Resilience

- [x] **RTL Mirroring & Colorblind Accessibility**
    - [x] Update [nautical_pilot_bottom_sheet.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/nautical_pilot_bottom_sheet.xml) with RTL-friendly attributes.
    - [x] Finalize [bottom_sheet_nautical_data.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/bottom_sheet_nautical_data.xml).
    - [x] Enhance [NavtexMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/ui/NavtexMapLayer.kt) with hatching and badges.
- [x] **Network Timeouts & Expired Forecast Banners**
    - [x] Harden OkHttp timeouts in [SailingDependencyContainer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/di/SailingDependencyContainer.kt).
    - [x] Harden OkHttp timeouts in [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt).
    - [x] Implement expiration banner in [OceanographicGribMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/map/layers/OceanographicGribMapLayer.kt).
- [x] **Asynchronous Offline Startup**
    - [x] Move mDNS discovery to background with timeouts in [SignalKDiscoveryManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/discovery/SignalKDiscoveryManager.kt).
    - [x] Add timeout to identity resolution in [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt).
- [/] **Verification**
    - [ ] Run build and verify changes.
