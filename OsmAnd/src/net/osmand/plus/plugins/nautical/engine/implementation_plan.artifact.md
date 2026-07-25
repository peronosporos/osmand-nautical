# MOB and Close-Quarters Autopilot Integration Plan

Update `ManOverboardManeuver`, `DockingManeuver`, and `MooringManeuver` to automatically manage the autopilot state for safety during critical and close-quarters operations.

## User Review Required

> [!CAUTION]
> The `ManOverboardManeuver` will immediately disengage the autopilot to prevent the boat from sailing away from the person in the water. Ensure the crew is aware that manual steering will be required instantly.

## Proposed Changes

### [Maneuver System]

#### [MODIFY] [ManeuverStateMachine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/ManeuverStateMachine.kt)
- Add `transitionToArmed()` to the interface.

#### [MODIFY] [ManeuverEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/ManeuverEngine.kt)
- Implement `transitionToArmed()` to update `currentState` to `ARMED`.

#### [MODIFY] [ManeuverManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/ManeuverManager.kt)
- In `arm()`, call `activeManeuver?.transitionToArmed()`.

#### [MODIFY] [ManOverboardManeuver.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/ManOverboardManeuver.kt)
- In `activate()`, call `NauticalPlugin.autopilotManager?.disengage()` immediately.
- Add a TTS announcement: "Man Overboard. Autopilot disengaged."

#### [MODIFY] [DockingManeuver.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/DockingManeuver.kt) & [MooringManeuver.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/MooringManeuver.kt)
- Implement `transitionToArmed()`:
    - Check `autopilotManager.state`.
    - If not `standby`, trigger TTS warning: "Autopilot active. Disengage before approach."
- Implement `transitionToExecuting()`:
    - Check `autopilotManager.state`.
    - If not `standby`, call `autopilotManager.disengage()` and announce: "Autopilot disengaged for approach."

## Verification Plan

### Manual Verification
1.  **MOB Test**: Engage autopilot in 'auto' mode. Trigger MOB. Verify autopilot instantly drops to 'standby' and the TTS alarm sounds.
2.  **Docking/Mooring Armed Test**: Engage autopilot. Select "Docking" maneuver. Verify the "Autopilot active. Disengage before approach." warning sounds.
3.  **Docking/Mooring Executing Test**: With autopilot engaged, press "EXECUTE" for a docking maneuver. Verify the autopilot is automatically dropped to 'standby'.
