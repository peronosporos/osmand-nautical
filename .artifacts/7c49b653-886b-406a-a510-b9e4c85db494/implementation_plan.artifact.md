# Implementation Plan: Fix Nautical Shunting Maneuver Functionality

This plan addresses critical data transformation bugs, workflow inconsistencies, and UI gaps in the "Shunting" maneuver, primarily used by multihulls like Proas.

## User Review Required

> [!IMPORTANT]
> - **COG Flipping Fix**: I will stop flipping Course Over Ground (COG) and Set/Drift direction by 180° in `MultihullShuntManager`. These are ground-referenced vectors and should remain absolute regardless of which end of the boat is the "bow".
> - **Autopilot Target Transformation**: I will add logic to flip autopilot heading and wind angle targets when shunting. This prevents the autopilot from attempting a 180° turn immediately after a shunt.

## Proposed Changes

### Core Engine & Data Transformation

#### [MODIFY] [MultihullShuntManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/MultihullShuntManager.kt)
- Remove `courseOverGroundTrue` from the flipping logic.
- Remove `setTrue` (if it were there, but I should ensure it stays absolute if added later).
- Add transformation for:
    - `targetHeading`
    - `pendingTargetHeading`
    - `autopilotHeadingSet`
    - `targetWindAngleApparent`
    - `autopilotWindAngleSet`
- Invert the sign of `leeway`.

#### [MODIFY] [ShuntingManeuver.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/ShuntingManeuver.kt)
- Fix SOG safety check to use `SignalKUnitConverter.msToKnots(sog)`.
- Use localized strings for instructions and TTS.
- Implement automatic completion logic based on COG/Heading alignment after shunt.
- Ensure proper helm lock acquisition and release (using `force = true`).

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Ensure `setShunted` properly propagates state to `MultihullShuntManager`.

### Workflow & Trigger Consolidation

#### [MODIFY] [AutopilotController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotController.kt)
- Update `shunt()` to trigger the `ShuntingManeuver` via `ManeuverManager` instead of toggling state directly. This ensures pre-flight checks and overlays are active.

#### [MODIFY] [ManeuverManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/ManeuverManager.kt)
- Fix `releaseLocks()` to use `PRIORITY_TACTICAL_MANEUVER` when releasing the tactical lock.

### UI Enhancements

#### [MODIFY] [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml)
- Add new strings for shunting maneuver states (active, starting TTS, completed).

#### [MODIFY] [NauticalManeuversBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/widgets/NauticalManeuversBottomSheet.kt)
- Add "Shunting" option to the list when `VesselType` is `PROA`.

#### [MODIFY] [ManeuverOverlayWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/ManeuverOverlayWidget.kt)
- Add mapping for "shunting" ID to localized label and appropriate icon (using `ic_action_sail_boat_dark`).

## Verification Plan

### Automated Tests
- I will verify the logic in `MultihullShuntManager` by checking vector math (0 -> 180, 90 -> 270) for heading and wind, while ensuring COG remains unchanged.

### Manual Verification
- Verify that triggering a shunt in the Pilot Widget now shows the Maneuver Overlay with the 3-second pre-flight check.
- Verify that the map vessel icon correctly flips its orientation but maintains its COG vector direction.
- Verify that the "Helm Locked by Shunting" message appears and blocks manual heading nudges during the maneuver.
