# Implementation Plan - Core App Interoperability & Hijacking Fixes

This plan addresses hardware button hijacking, profile pollution, and notification channel collisions between the Nautical plugin and the core OsmAnd app.

## User Review Required

> [!IMPORTANT]
> The volume button interception will now only work when a nautical maneuver is in the `EXECUTING` state. In the `ARMED` state, volume buttons will still perform their default action (zoom) unless the user explicitly starts the maneuver. This avoids accidental hijacking when a maneuver is just "ready" but not yet active.

## Proposed Changes

### 1. Hardware Button Release

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Refactor the `KeyEvent.Callback` in `mapActivityResume`.
- Ensure volume keys are ONLY consumed if `maneuverManager.state == ManeuverState.EXECUTING`.
- Add a check for `ApplicationMode.BOAT` to ensure the nautical context is active.

### 2. Application Profile Isolation

#### [MODIFY] [VhfPoiSearchLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/poi/ui/VhfPoiSearchLayer.kt)
- Guard `onDraw` and `triggerSearch` with a check for `ApplicationMode.BOAT`.
- This prevents VHF POIs from appearing and search jobs from running when in Car, Pedestrian, or other non-marine profiles.

#### [MODIFY] [S57MapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/ui/S57MapLayer.kt)
- Guard `onDraw` with a check for `ApplicationMode.BOAT`.
- This ensures S57 nautical charts are only rendered when the nautical profile is active.

### 3. Dedicated Safety Notification Channels

#### [MODIFY] [NauticalNotificationManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/NauticalNotificationManager.kt)
- Create a dedicated Android Notification Channel `osmand_marine_critical` with `IMPORTANCE_HIGH`.
- Implement channel initialization.
- Route critical safety alarms (MOB, Anchor Drift, Collision/CPA) through this channel using `NotificationManagerCompat`.
- Ensure non-critical warnings use a different channel or standard priority.

## Verification Plan

### Automated Tests
- Since these are mostly UI and system-level interactions, manual verification on device/emulator is preferred. I will ensure the code compiles.

### Manual Verification
- Deploy the app and switch between Boat and Car profiles. Verify that VHF POIs and S57 layers disappear in Car mode.
- Start a tactical maneuver (e.g., Tacking) and verify that volume buttons execute/abort the maneuver.
- Finish/Abort the maneuver and verify volume buttons return to map zooming.
- Trigger a mock MOB or Anchor Drift (if possible via settings) and verify the notification appears and follows high-priority channel settings.
