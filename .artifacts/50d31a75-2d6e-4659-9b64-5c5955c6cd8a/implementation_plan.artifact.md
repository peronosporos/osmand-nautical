# Implementation Plan - Med-Mooring Maneuver Refinement

This plan addresses all 18 identified bugs and issues in the nautical plugin's Med-Mooring functionality, covering backend logic, safety systems, and UI/UX improvements.

## User Review Required

> [!IMPORTANT]
> **Safety Restoration Policy**: Autopilot restoration will be changed from restoring the previous mode (potentially dangerous "track" mode) to defaulting to **STANDBY** with a voice notification, unless the user explicitly overrides this in settings.

> [!WARNING]
> **Automatic Abort**: The maneuver will now automatically abort with a high-priority alarm if the approach speed exceeds 1.5 knots within one vessel length of the quay.

## Proposed Changes

### Nautical Core Backend

#### [MODIFY] [MedMooringManeuver.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/MedMooringManeuver.kt)
- **Phase Logic**:
    - Implement stern-way detection using `speedOverGround` and comparing `courseOverGroundTrue` vs `headingTrue`.
    - Replace hardcoded instruction strings with `R.string` resources.
    - Fix progress calculation in `APPROACH_DROP_ZONE`.
    - Implement `ANCHOR_DROP` to `PAYOUT_RODE` transition based on stern-way detection instead of a blind timer.
- **Safety**:
    - Add `vesselDraft` check against `depthBelowKeel` in `checkSafetyPreconditions`.
    - Integrate `rodeDeployed` (chain counter) for precise payout monitoring.
    - Dispatch `AlarmType.AUTOPILOT_COMMAND_REJECTED` (or similar high-priority alarm) on critical over-speed abort.
    - Implement Helm Lock override listener to abort the maneuver if the skipper takes manual control.
- **Autopilot**:
    - Update `PAYOUT_RODE` to calculate a perpendicular heading to the quay target instead of locking the current heading.
    - Default `restoreAutopilot` to "standby".

#### [MODIFY] [SafetyPreflightController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SafetyPreflightController.kt)
- **Engine Detection**: Update RPM check to iterate through the `engines` map in `MarineState` instead of using the deprecated `engineRpm` field.

### UI and Visualization

#### [MODIFY] [NauticalMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalMapLayer.kt)
- **Anchor Rendering**: Add logic to draw the anchor icon at `anchorDropLat/Lon` during med-mooring.
- **Dynamic Backing Vector**: Scale the backing vector length based on the distance to the quay target instead of a fixed 50m.

#### [MODIFY] [ManeuverOverlayWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/ManeuverOverlayWidget.kt)
- **Localization**: Use `app.getString(resId)` for maneuver display names instead of string manipulation of the ID.

#### [MODIFY] [NauticalManeuversBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/widgets/NauticalManeuversBottomSheet.kt)
- **Parameter Sync**: Ensure updates to vessel length and scope are propagated to the active maneuver instance if it is currently armed.

### Resources

#### [MODIFY] [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml)
- Add new localized strings for med-mooring instructions, safety warnings, and maneuver names.

## Verification Plan

### Automated Tests
- No new automated tests planned due to the hardware-dependent nature of Signal K, but manual logic verification will be performed via `MarineState` injection if possible in scratch scripts.

### Manual Verification
- Deploy to device/emulator.
- Trigger Med-Mooring from the context menu.
- Verify the progress bar behavior.
- Verify the anchor icon appears on the map at the drop point.
- Verify that backing the boat (simulated speed/COG) triggers the transition to `STERN_APPROACH`.
- Verify the over-speed abort triggers an alarm.
- Verify localization of all strings in the HUD.
