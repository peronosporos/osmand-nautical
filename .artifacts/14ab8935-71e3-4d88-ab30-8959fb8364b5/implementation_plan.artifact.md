# Implementation Plan - Fixing Heave-To Maneuver and MOB Integration

This plan addresses a comprehensive list of bugs and architectural issues in the Nautical plugin's "Heave-To" maneuver and Man Overboard (MOB) functionality, spanning UI, backend logic, and Signal K integration.

## User Review Required

> [!IMPORTANT]
> **Autopilot Control Protocol**: The fix changes the mechanism for locking the helm during a Heave-To from `rudderLimit` (a config param) to `rudderAngle` (a command). This assumes the underlying autopilot (Garmin/Furuno/Pypilot) supports rudder commands via Signal K.

> [!WARNING]
> **MOB Notification Path Change**: I will be standardizing the MOB notification path to `notifications.security.mob`. This may affect external tools listening to the older `notifications.mob` path, but it is necessary for internal consistency with the broadcaster.

## Proposed Changes

### [Nautical Autopilot & Signal K Engine]

#### [MODIFY] [AutopilotController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotController.kt)
- Add `setRudderAngle(radians: Double)` method.
- Implement vendor-specific pathing for rudder commands if necessary, defaulting to `steering/autopilot/rudderAngle`.

#### [MODIFY] [SignalKPaths.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKPaths.kt)
- Update `NOTIFICATIONS_MOB` to `"notifications.security.mob"`.

---

### [Maneuver Engine & Logic]

#### [MODIFY] [ManOverboardManeuver.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/ManOverboardManeuver.kt)
- **Fix `executeHeaveTo()`**:
    - Replace `setRudderLimit` with `setRudderAngle`.
    - Replace the fixed 8s delay with a dynamic "Tack Detection" (wait for Apparent Wind Angle sign to flip).
    - Ensure the autopilot remains engaged in a mode that honors the rudder command (e.g., avoid disengaging to "standby" immediately if it releases the drive).
- **Integrate Completion Logic**:
    - Explicitly call `HeavingToManeuver.updateState()` or incorporate its "stabilization" check (SOG < 1kt, TWA 45-60°) to automatically complete the maneuver.
- **Improve Feedback**:
    - Add meaningful toasts/audio alerts if the autopilot is disconnected or rejects the command.

#### [MODIFY] [HeavingToManeuver.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/HeavingToManeuver.kt)
- Ensure it exposes its stabilization check for reuse by the MOB engine.

---

### [MOB UI & ViewModel]

#### [MODIFY] [MobViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/mob/viewmodel/MobViewModel.kt)
- **Align Propulsion Checks**: Use `PropulsionContextManager.getInstance(app).isEngineRunning()` for `isMotoring` to ensure consistency with the backend.
- **Relax "Heave To" Constraints**:
    - Allow the "Heave To" button to be enabled even if not strictly upwind (threshold increased or check removed with a warning).
    - Refine `isUpwind` to a tighter 45° threshold for "optimal" status but maintain usability.

#### [MODIFY] [MobEmergencyHeaderView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/mob/ui/MobEmergencyHeaderView.kt)
- Update the `updateState` logic to handle the relaxed button constraints.
- Ensure visual feedback is given when a maneuver button is pressed but the autopilot is unavailable.

## Verification Plan

### Automated Tests
- Create unit tests for `MobViewModel` to verify propulsion state alignment and button enablement logic.
- Mock `MarineState` and verify `ManOverboardManeuver` transitions through tacking -> rudder lock -> completion.

### Manual Verification
- Deploy to an emulator or device.
- Simulate MOB event.
- Trigger "Heave To" and verify (via logs) that:
    1. A PUT request is sent to `tack`.
    2. After the AWA sign flips, a PUT request is sent to `rudderAngle`.
    3. The maneuver eventually transitions to COMPLETED when simulated SOG drops.
- Verify that receiving a Signal K delta on `notifications.security.mob` correctly triggers the MOB HUD in the app.
