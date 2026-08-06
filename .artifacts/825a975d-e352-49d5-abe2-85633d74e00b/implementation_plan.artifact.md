# Implementation Plan: Preference & Engine Binding Refactor

Fix disconnected preferences and hardcoded engine parameters by binding them to `OsmandSettings` and exposing them in the UI.

## Proposed Changes

### [Component] Core Settings

#### [MODIFY] [OsmandSettings.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/settings/backend/OsmandSettings.java)
- Add `NAUTICAL_ACTUATOR_ALARM_THRESHOLD` (Float, default 85.0f).
- Add `NAUTICAL_NAVTEX_EXPIRY_HOURS` (Integer, default 48).
- Add `NAUTICAL_LOOK_AHEAD_RADIUS_NM` (Float, default 1.5f).

### [Component] Nautical UI

#### [MODIFY] [nautical_settings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/xml/nautical_settings.xml)
- Add UI elements for Actuator Alarm Threshold, NAVTEX Expiry Hours, and Look Ahead Radius.

#### [MODIFY] [NauticalSettingsFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalSettingsFragment.kt)
- Setup the 3 new preferences in `setupSafetyCategory()`.
- Implement validation/clamping in `onPreferenceChange`.

### [Component] Nautical Engines

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Bind `startWatchdog` delay to `NAUTICAL_TELEMETRY_REFRESH_RATE`.
- Bind `checkActuatorLoad` threshold to `NAUTICAL_ACTUATOR_ALARM_THRESHOLD`.

#### [MODIFY] [NavtexRepository.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/data/NavtexRepository.kt)
- Bind `cleanupExpired` to `NAUTICAL_NAVTEX_EXPIRY_HOURS`.

#### [MODIFY] [SafetyCorridorChecker.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/engine/SafetyCorridorChecker.kt)
- Inject `OsmandSettings` into constructor.
- Read `NAUTICAL_CORRIDOR_WIDTH` and `NAUTICAL_SAFETY_CORRIDOR_BUFFER` directly in `checkCorridor`.
- Implement `checkLookAhead` using `NAUTICAL_LOOK_AHEAD_RADIUS_NM`.

### [Component] Map Layers (Fixing Signature Changes)

#### [MODIFY] [NauticalMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalMapLayer.kt)
#### [MODIFY] [WeatherRoutingMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/map/layers/WeatherRoutingMapLayer.kt)
- Update `SafetyCorridorChecker` instantiation and `checkCorridor` calls.

## Verification Plan

### Automated Tests
- I will verify the changes by inspecting the code logic and ensuring all references are updated.
- Since I cannot run the app, I will rely on code analysis.

### Manual Verification
- User should verify that the new settings appear in Nautical Settings.
- User should verify that changing "Telemetry Refresh Rate" affects the UI update frequency (Signal K data).
- User should verify that NAVTEX expiry and Safety Corridor checks respond to the new settings.
