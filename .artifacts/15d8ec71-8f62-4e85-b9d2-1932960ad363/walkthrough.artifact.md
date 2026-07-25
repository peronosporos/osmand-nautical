# Walkthrough - Fixing Nautical Build Errors

I have fixed 13 compilation errors related to nautical preferences and plugin initialization.

## Changes Made

### Core Preference System
- Added a `map` method to [CommonPreference.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/settings/backend/preferences/CommonPreference.java). This allows type-safe mapping between different preference types (e.g., storing a `Double` value in a `Float` preference).
- The implementation uses a decorator pattern to wrap the original preference and apply transformations during read/write operations.

### Nautical Settings
- Updated nautical preferences in [OsmandSettings.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/settings/backend/OsmandSettings.java) to use the new `map` method.
- Fixed invalid Kotlin-style method references (`Float::toDouble`, `Double::toFloat`) with their Java counterparts (`Float::doubleValue`, `Double::floatValue`).

### Plugin Initialization
- Fixed a broken plugin reference in [PluginsHelper.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/PluginsHelper.java).
- Replaced the non-existent `ManeuverPlugin` with [SailingIntegrationPlugin](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/plugin/SailingIntegrationPlugin.kt), which is the correct plugin for advanced sailing features including maneuvers.

## Verification Results

### Static Analysis
- Ran `analyze_file` on all modified files.
- [CommonPreference.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/settings/backend/preferences/CommonPreference.java): **CLEAN** (except for unrelated IDE warnings).
- [OsmandSettings.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/settings/backend/OsmandSettings.java): **CLEAN**.
- [PluginsHelper.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/PluginsHelper.java): **CLEAN**.

### Build Log Verification
- All 13 errors from the original build log have been addressed:
    - 12 errors related to `.map(...)` usage in `OsmandSettings.java`.
    - 1 error related to missing `ManeuverPlugin` in `PluginsHelper.java`.
