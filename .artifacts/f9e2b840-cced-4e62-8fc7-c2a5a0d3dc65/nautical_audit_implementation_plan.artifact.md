# Nautical Plugin Audit & Hardening Plan

This plan addresses the identified defects in the Nautical Plugin related to settings UI, background service duplication, and redundant code.

## User Review Required

> [!IMPORTANT]
> - **Service Consolidation**: I propose merging `MaritimeOperationsService` and `SailingDataService` into the core `NavigationService` to reduce background overhead and notification clutter.
> - **GPS Bridge Removal**: `NauticalLocationProvider` currently mutes the system GPS. I will refactor this to use OsmAnd's standard external location source mechanism.

## Proposed Changes

### 1. Settings UI Restructuring

Group all nautical settings into logical categories in `NauticalSettingsFragment` and `nautical_settings.xml`.

#### [MODIFY] [nautical_settings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/xml/nautical_settings.xml)
- Add categories: Display, Telemetry, Safety, Hardware.
- Add missing preferences for Vessel Draft, Safety Margin, and Corridor Width.
- Add new preferences for Autopilot Damping (Filter Sensitivity) and AIS NMEA UDP Port.

#### [MODIFY] [NauticalSettingsFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalSettingsFragment.kt)
- Update `setupPreferences` to match the new XML structure.
- Add setup methods for the newly added preferences.
- Implement proper mapping for all `OsmandSettings` nautical parameters.

### 2. Background Service & Location Refactoring

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Remove `startMaritimeOperationsService` and its calls.
- Integrate `AnchorDriftWatchdog` and `ManeuverManager` monitoring directly into the plugin's location/state listener.
- Use `NavigationService.USED_BY_NAUTICAL` to ensure a single foreground service handles all background needs.

#### [DELETE] [MaritimeOperationsService.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/MaritimeOperationsService.kt)
#### [DELETE] [SailingDataService.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/service/SailingDataService.kt)

#### [MODIFY] [NauticalLocationProvider.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/NauticalLocationProvider.kt)
- Refactor to remove intrusive `muteHardwareGps` logic.
- Register as a standard external location provider within OsmAnd's location framework.

### 3. Redundant Code & Placeholder Elimination

#### [MODIFY] [ManeuverManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/ManeuverManager.kt)
- Add `getManeuverId(engine: ManeuverEngine): String?` to avoid reflection hacks.

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Replace reflection hack with `maneuverManager.getManeuverId()`.
- Add logging to empty `catch` blocks or properly handle exceptions.
- Add "Night Vision" toggle to the Settings UI.

## Verification Plan

### Automated Tests
- `NauticalSettingsTest.kt`: Verify all settings correctly persist to `OsmandSettings`.
- `NauticalLocationBridgeTest.kt`: Verify SignalK positions are injected without disabling system GPS entirely.

### Manual Verification
- Deploy to device/emulator.
- Check "Nautical Settings" for new categories and missing fields.
- Start a maneuver and check that only one OsmAnd notification is active in the status bar.
- Enable Anchor Watch and verify background alerts work using only `NavigationService`.
