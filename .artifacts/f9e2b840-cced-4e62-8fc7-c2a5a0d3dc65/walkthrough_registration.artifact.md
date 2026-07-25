# Walkthrough - Harmonic Data Management Registration

I have integrated the `TideDataManagerFragment` into the OsmAnd nautical settings menu.

## Changes Made

### Resource Strings
- Added `nautical_settings_tides_menu` and `nautical_settings_tides_summary` to [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml).

### Settings Registry
- Registered `TIDE_DATA_MANAGER` in [SettingsScreenType.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/settings/fragments/SettingsScreenType.java). This allows the fragment to be launched using the standard `showInstance` mechanism.

### UI Integration
- Added a new preference item to [SailingPerformanceSettingsFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/settings/SailingPerformanceSettingsFragment.kt).
- The menu item is labeled "Manage Tide & Current Harmonics" and is located under the Sailing Performance category.
- Clicking this item launches the `TideDataManagerFragment`.

## Verification Results

### Code Integrity
- Verified that all new identifiers (`R.string.nautical_settings_tides_menu`, `SettingsScreenType.TIDE_DATA_MANAGER`, etc.) are correctly referenced.
- Used an existing nautical icon (`ic_action_nautical_depth`) for the menu item.
