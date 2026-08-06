# Implementation Plan - Enhanced Nautical Notifications and UX

Improve the user experience of the Nautical plugin by providing comprehensive, user-friendly notifications, toasts, and voice messages for key events like connection status changes, navigation updates, and safety warnings.

## User Review Required

> [!IMPORTANT]
> Some notifications will trigger voice announcements (TTS). Ensure that the user's volume settings are appropriate.

## Proposed Changes

### [Nautical Core]

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Enhance `marineStateListener` to track `connectionStatus` transitions:
    - `DISCONNECTED` -> `CONNECTING`: Show "Connecting to Signal K..." toast.
    - `DISCONNECTED`/`CONNECTING` -> `CONNECTED`: Show "Signal K Connected" toast and announce via TTS.
    - `STALE` -> `CONNECTED`: Show "Signal K Connection Restored" toast.
    - `CONNECTED` -> `STALE`: Show "Signal K Connection Stale" toast.
    - `ANY` -> `DISCONNECTED`: Show "Signal K Connection Lost" toast (complementing existing alarm logic).
- Update `routeStepListener`:
    - Add toast message for "Waypoint reached".
    - Announce next waypoint bearing if available: "Proceeding to next waypoint, course [X] degrees".
- Update `checkOffCourseAlert`:
    - Add toast message for "Off Course" warning.
- Enhance `checkEmergencyPower`:
    - Add voice announcement for critical low battery.
- Ensure all nautical voice alerts use `NauticalAudioArbiter` for consistent priority management.

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Ensure consistent triggering of `onConnectionRestored` and `onConnectionLost` callbacks during all status transitions.

### [Resources]

#### [MODIFY] [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml)
- Add or update strings for:
    - `nautical_sk_connected`: "Signal K Connected"
    - `nautical_sk_connection_lost`: "Signal K Connection Lost"
    - `nautical_sk_connection_stale`: "Signal K Connection Stale"
    - `nautical_proceeding_to_waypoint`: "Proceeding to next waypoint, course %1$.0f degrees"
    - `nautical_critical_low_battery`: "Critical low battery warning"

## Verification Plan

### Automated Tests
- Unit tests for `NauticalPlugin` status transition logic (if applicable).
- Verify `NauticalAudioArbiter` correctly queues and plays expected `AlarmType`s.

### Manual Verification
- Deploy to device/emulator.
- Toggle Signal K server connection and verify toasts and voice announcements.
- Simulate waypoint arrival and verify "Waypoint reached" toast and next course announcement.
- Simulate off-course condition and verify toast.
- Simulate low battery telemetry and verify banner and voice alert.
