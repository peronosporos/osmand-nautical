# Implementation Plan - Nautical Plugin UI & Configuration Audit

This plan addresses all identified gaps in the Nautical plugin's UI, ensuring that all backend functionalities have corresponding frontend implementations and that fragmented settings are consolidated for better discoverability.

## User Review Required

> [!IMPORTANT]
> **Settings Consolidation**: I will move several settings that are currently only in the "Configure Map" menu (like laylines, tides, and vessel indicators) into the main Nautical Settings screen. This improves discoverability but may feel redundant to users used to the old menu.

> [!WARNING]
> **Display Mode Migration**: I will replace the "Night Vision" and "Sunlight Mode" toggles with a single "Display Mode" list preference (Normal, Dark, Sunlight). This matches the modern backend structure but changes the UI pattern for these features.

## Proposed Changes

### [OsmAnd App]

#### [MODIFY] [nautical_settings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/xml/nautical_settings.xml)
- Add "Autopilot Tuning" section with gain, counter-rudder, trim, sensitivity, and limit.
- Add "Advanced Anchor Configuration" section with depth, tide rise, bow offset, and scope ratio.
- Add "Safe Corridor" settings under Safety.
- Replace legacy Night Vision/Sunlight toggles with a single Display Mode `ListPreference`.
- Add "Heavy Weather Mode" toggle.
- Integrate "Map Indicators" and "Map Overlays" toggles for better discoverability.

#### [MODIFY] [NauticalSettingsFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalSettingsFragment.kt)
- Implement `mDNS` discovery dialog when "Discovery (mDNS)" is clicked.
- Add logic for all new settings sections (Autopilot, Anchor, Corridor).
- Synchronize unit conversions for new depth/distance settings using `SignalKUnitConverter`.
- Implement selection logic for `NauticalDisplayMode`.

#### [MODIFY] [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml)
- Add missing labels and summaries for all new settings.
- Add strings for mDNS discovery dialog and server selection.

#### [MODIFY] [SignalKDiscoveryManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/discovery/SignalKDiscoveryManager.kt)
- Ensure it properly exposes the discovery state for the UI dialog.

## Verification Plan

### Automated Tests
- No new automated tests are required for UI-only changes, but I will verify that `NauticalPlugin` receives updates for these new settings via the `prefChangeListener`.

### Manual Verification
- Deploy to device and navigate to **Settings -> Nautical Plugin**.
- Verify "Autopilot Tuning" and "Advanced Anchor" sections appear and values are persisted.
- Test "Discovery (mDNS)" and ensure it finds local Signal K servers and populates the IP/Port fields on selection.
- Verify "Display Mode" correctly toggles between Normal, Dark (Red Filter), and Sunlight modes.
- Toggle "Map Overlays" (e.g. Laylines) in settings and verify they reflect immediately on the map.
