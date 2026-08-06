# Walkthrough - Core App Interoperability & Hijacking Fixes

I have implemented fixes for hardware button hijacking, application profile isolation, and dedicated safety notification channels. These changes ensure that the Nautical plugin coexists gracefully with the core OsmAnd application.

## Changes Made

### Hardware Button Release
- Updated [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt) to strictly guard volume key interception.
- **New Behavior**: Volume keys are only consumed if the application is in `BOAT` mode **AND** a tactical maneuver is in the `EXECUTING` state. In all other states (including `ARMED`), volume keys remain available for map zooming.

### Application Profile Isolation
- Added profile guards to [VhfPoiSearchLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/poi/ui/VhfPoiSearchLayer.kt) and [S57MapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/ui/S57MapLayer.kt).
- **Impact**: Custom nautical rendering and VHF POI searches are immediately aborted if the user switches to a non-marine profile (e.g., Car, Pedestrian, Bicycle). This prevents rendering clutter and unnecessary background search jobs in non-nautical modes.

### Dedicated Safety Notification Channels
- Implemented a high-priority notification channel in [NauticalNotificationManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/NauticalNotificationManager.kt).
- **Channel**: `osmand_marine_critical` ("Marine Safety Alerts").
- **Properties**: `IMPORTANCE_HIGH`, red light enabled, vibration enabled, and bypasses Do Not Disturb.
- **Routing**:
    - **SignalK Alarms**: All critical alerts from the SignalK server are posted to this channel.
    - **Anchor Drift**: Updated [AnchorDriftWatchdog.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/AnchorDriftWatchdog.kt) to post to this channel.
    - **Man Overboard**: Updated [MobAudioAlertManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/mob/viewmodel/MobAudioAlertManager.kt) to post to this channel.
- Added localized string resources for the channel name and description in [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml).

## Verification Results

### Code Integrity
- All new strings are registered in the main `strings.xml`.
- Key event callbacks return `false` correctly for non-engaged states.
- Layer `onDraw` calls return early if `ApplicationMode` is not `BOAT`.

### Visual Verification
> [!NOTE]
> High-priority notifications will appear as "Heads-up" notifications and will persist in the notification shade until the emergency is resolved or the notification is dismissed.

render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/poi/ui/VhfPoiSearchLayer.kt)
render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/ui/S57MapLayer.kt)
render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/NauticalNotificationManager.kt)
