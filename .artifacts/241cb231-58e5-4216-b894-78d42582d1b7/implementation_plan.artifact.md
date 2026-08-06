# Implementation Plan - Full Android Smartwatch Support

This plan refactors the WearOS / Smartwatch detection to support "Full Android" watches (rugged maritime devices like Kospet/Lemfo) which do not report themselves as watches via the standard OS flag.

## Proposed Changes

### 1. Settings & Infrastructure

#### [MODIFY] [OsmandSettings.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/settings/backend/OsmandSettings.java)
- Add `NAUTICAL_FORCE_WATCH_LAYOUT` (Boolean) preference.

#### [MODIFY] [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml)
- Add `nautical_force_watch_layout` and `nautical_force_watch_layout_desc`.

#### [MODIFY] [nautical_settings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/xml/nautical_settings.xml)
- Add the "Force Smartwatch Layout" toggle in the Hardware category.

#### [MODIFY] [NauticalSettingsFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalSettingsFragment.kt)
- Bind the new toggle in `setupHardwareCategory()`.

### 2. Refined Detection Logic

#### [MODIFY] [WearOsNauticalManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/WearOsNauticalManager.kt)
- Update `isWatchMode()` to:
    1. Check the manual override `NAUTICAL_FORCE_WATCH_LAYOUT`.
    2. Check `UI_MODE_TYPE_WATCH`.
    3. Check if `smallestScreenWidthDp < 300` (heuristic for full-Android watches).

### 3. UI Safety & Robustness

#### [MODIFY] [nautical_heartbeat_hud.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/nautical_heartbeat_hud.xml)
- Increase padding and use a more centered layout to ensure no data is cut off on round screens.
- Use `16dp` horizontal padding as a safe zone for round displays.

#### [MODIFY] [HeartbeatHudView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/HeartbeatHudView.kt)
- Apply extra padding programmatically if `isRoundScreen()` is detected.

## Verification Plan

### Automated Tests
- Unit test for `WearOsNauticalManager` with various `Configuration` mocks (small width, watch mode, etc.).

### Manual Verification
- Test on a standard phone (normal layout).
- Test on a phone with "Smallest Width" set to 280dp in Developer Options (should trigger watch layout).
- Test the "Force Smartwatch Layout" toggle in settings.
