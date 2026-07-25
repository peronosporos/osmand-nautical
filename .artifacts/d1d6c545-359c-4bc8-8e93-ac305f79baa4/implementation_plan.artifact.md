# PyPilot Integration Safety & Reliability Audit Fixes

This plan addresses several critical safety and functional defects in the OsmAnd nautical plugin's PyPilot autopilot integration.

## Proposed Changes

### 1. Mode Switch Safety & Command Validation

#### [MODIFY] [AutopilotController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotController.kt)
- Fix `isWindSafeForManeuver` to return `false` if wind data is missing.
- Update `setAutopilotMode` to validate preconditions:
    - `wind`: Verify `windDirectionApparent` is present in `MarineState`.
    - `track`: Verify `engine.isFollowingRoute` is true.
- Alert the user via toast/voice if switching fails due to missing data.

#### [MODIFY] [NauticalPilotBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotBottomSheet.kt)
- Disable or show warning on the `WIND` and `ROUTE` buttons if their preconditions are not met.

---

### 2. Automated Tack / Gybe Execution & Interlocks

#### [MODIFY] [ManeuverEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/ManeuverEngine.kt)
- Introduce a global maneuver timeout (e.g., 60s) to prevent the system from hanging in `EXECUTING` state if the autopilot fails to complete a maneuver.
- Update `checkSafetyPreconditions` to correctly distinguish between Tacking (Upwind) and Gybing (Downwind) safety checks.

#### [MODIFY] [TackingManeuver.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/TackingManeuver.kt)
- Ensure safety check uses `tacking = true`.

#### [MODIFY] [GybingManeuver.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/GybingManeuver.kt)
- Store the "boom secure" timer and cancel it in `transitionToAborted` to prevent uncommanded gybes after an abort.
- Ensure safety check uses `tacking = false`.

---

### 3. Emergency Disengagement & Hardware Watchdog

#### [MODIFY] [AutopilotController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotController.kt)
- For "STANDBY" mode, clear all pending target headings and modes in the engine immediately.
- Use a high-priority request for "STANDBY" commands to bypass potential network congestion in the `OkHttpClient` queue.

#### [MODIFY] [NauticalMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalMapLayer.kt)
- Enhance the `drawConnectionWarning` to make it blinking or use a more aggressive "Emergency Red" color scheme to ensure it's unmissable.

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Ensure "AUTOPILOT DISCONNECTED" alert persists and uses a continuous/repeated audible warning until acknowledged or connection is restored.

## Verification Plan

### Automated Tests
- Unit tests for `AutopilotController` mode validation logic.
- Unit tests for `ManeuverEngine` timeout and safety check routing.

### Manual Verification
- Deploy to device/emulator.
- Trigger "STANDBY" while multiple telemetry requests are pending to verify priority.
- Simulate wind data loss and attempt to switch to `WIND` mode.
- Start a gybe and abort during the countdown to verify the autopilot command is never sent.
- Simulate connection loss while engaged and verify the persistent alert.
