# Walkthrough - Preference & Engine Binding Refactor

Fixed disconnected preferences and hardcoded engine parameters by binding them to `OsmandSettings` and exposing them in the UI.

## Changes Made

### Core Settings
- **[OsmandSettings.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/settings/backend/OsmandSettings.java)**:
    - Registered `NAUTICAL_ACTUATOR_ALARM_THRESHOLD` (Float, default 85.0%).
    - Registered `NAUTICAL_NAVTEX_EXPIRY_HOURS` (Integer, default 48h).
    - Registered `NAUTICAL_LOOK_AHEAD_RADIUS_NM` (Float, default 1.5 NM).

### Nautical UI
- **[nautical_settings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/xml/nautical_settings.xml)**:
    - Added UI fields for the new settings with appropriate dialog titles.
- **[NauticalSettingsFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalSettingsFragment.kt)**:
    - Set up summaries for new settings.
    - Implemented `onPreferenceChange` logic to clamp user inputs and persist them to `OsmandSettings`.

### Engines & Repositories
- **[SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)**:
    - Bound telemetry loop delay to `NAUTICAL_TELEMETRY_REFRESH_RATE` with a minimum of 1s to prevent spin loops.
    - Bound actuator overload threshold to `NAUTICAL_ACTUATOR_ALARM_THRESHOLD`.
- **[NavtexRepository.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/data/NavtexRepository.kt)**:
    - Bound message expiry to `NAUTICAL_NAVTEX_EXPIRY_HOURS`.
    - Used long literal multiplication (`60L * 60L * 1000L`) to prevent integer overflow.
- **[SafetyCorridorChecker.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/engine/SafetyCorridorChecker.kt)**:
    - Added `settings: OsmandSettings? = null` with `@JvmOverloads` to support existing constructor calls.
    - Read `NAUTICAL_CORRIDOR_WIDTH` and `NAUTICAL_SAFETY_CORRIDOR_BUFFER` directly from settings.
    - Implemented `checkLookAhead` using `NAUTICAL_LOOK_AHEAD_RADIUS_NM`.

## Verification Results

### Code Analysis
- Verified that all new settings are correctly registered and used.
- Confirmed that `SafetyCorridorChecker` call sites in `NauticalMapLayer.kt` and `WeatherRoutingMapLayer.kt` remain compatible due to `@JvmOverloads`.
- Confirmed that integer overflow in `NavtexRepository` is prevented.
- Confirmed that input validation and persistence are implemented in `NauticalSettingsFragment`.
