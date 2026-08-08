# Walkthrough - Fixing Nautical Plugin Errors

I have resolved the compilation errors in the core Nautical Plugin files by addressing missing imports and unresolved string resources.

## Changes Made

### SignalK Engine
- Added missing imports for `MutableSharedFlow` and `asSharedFlow` in [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt).

### Resources
- Added the following missing string resources to [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml):
    - `nautical_offline_status`
    - `nautical_switch_panel_desc`
    - `nautical_boat_ai_desc`
    - `nautical_notifications_desc`
    - `nautical_server_routes_desc`
    - `nautical_server_charts_desc`
    - `nautical_disconnected_performance_msg`
    - `nautical_logbook_sync_msg`
    - `nautical_clear_data_confirm`

### Settings Fragment
- Resolved unresolved references to string resources in [NauticalSettingsFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalSettingsFragment.kt) by adding them to the resource file. This also fixed the type inference issues in the "Clear Data" confirmation dialog.

## Verification Results

- **SignalKEngine.kt**: Analysis confirmed no more unresolved references.
- **NauticalPlugin.kt**: Analysis confirmed no errors.
- **NauticalSettingsFragment.kt**: Analysis confirmed that adding the missing strings resolved all 12 reported errors.

All targeted files are now free of compilation errors.
