# Walkthrough - Heave-To Maneuver and MOB Refactoring

I have completed the refactoring of the "Heave-To" maneuver and improved the robustness of the Man Overboard (MOB) system. These changes address safety concerns, architectural inconsistencies, and bugs identified during the inspection.

## Key Changes

### 1. Enhanced Autopilot Control
- **New Capability**: Added [setRudderAngle](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotController.kt#L317-L325) to `AutopilotController`. This allows the maneuver engine to command specific helm positions rather than just setting physical limits.
- **Improved Security**: All Signal K PUT commands now strictly enforce HTTPS for non-localhost connections to prevent man-in-the-middle attacks on steering systems.

### 2. Robust Heave-To Maneuver
- **Dynamic Tack Detection**: Replaced fixed timing with [AWA Sign Flip Detection](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/ManOverboardManeuver.kt#L182-L210). The rudder is now locked only *after* the boat has successfully passed through the wind, ensuring the maneuver doesn't stall in varying conditions.
- **Automatic Completion**: Integrated a [stabilization check](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/ManOverboardManeuver.kt#L225-L235) that monitors Speed Over Ground (SOG) and True Wind Angle (TWA). The maneuver automatically transitions to COMPLETED when the vessel stabilizes (SOG < 0.5kt).
- **Correct Rudder Lock**: Switched from `setRudderLimit` to `setRudderAngle` to correctly stabilize the vessel to windward.

### 3. MOB Integration & UI
- **Path Standardization**: Unified the MOB notification path to `notifications.security.mob` across the broadcaster and the listener in [SignalKPaths.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKPaths.kt).
- **Aligned Propulsion State**: The [MobViewModel](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/mob/viewmodel/MobViewModel.kt#L70-L80) now uses the central `PropulsionContextManager` for motoring detection, preventing UI/Backend desync.
- **Improved Usability**: Relaxed button constraints in the [Emergency HUD](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/mob/ui/MobEmergencyHeaderView.kt#L181-L188). Skippers can now trigger a Heave-To even if not perfectly close-hauled, with visual hints indicating when the vessel is in an optimal position for the maneuver.

## Verification Results

### Automated Tests
- Verified the logic for `isUpwind` (< 60°) and `isMotoring` (via PropulsionContextManager).
- Validated tack detection state machine transitions in isolation.

### Manual Verification
- Simulated MOB event on a Signal K connected testbed.
- Confirmed that the "Heave To" button remains enabled while sailing.
- Verified that the `tack` command is followed by a `rudderAngle` command upon rotation.
- Confirmed the MOB HUD activates immediately upon server-side notification on the new path.
