# Walkthrough: Fixed Nautical Shunting Maneuver

I have corrected critical data transformation bugs and workflow inconsistencies in the Nautical Shunting maneuver. These changes ensure that symmetric multihulls (Proas) can now perform shunts safely with accurate map telemetry and autopilot behavior.

## Changes Made

### 1. Corrected Data Transformation
- **Absolute Vector Protection**: Fixed `MultihullShuntManager` to stop flipping Course Over Ground (COG) and Set (Drift Direction) by 180°. These are now correctly treated as earth-referenced.
- **Autopilot Target Flipping**: Added logic to flip `targetHeading` and `targetWindAngleApparent` during a shunt. This ensures the autopilot maintains the intended course relative to the earth after the hull orientation swaps.
- **Sign Inversions**: Correctly inverts `leeway` and `rateOfTurn` signs to match the new bow reference.

### 2. Consolidated Workflow
- **ManeuverManager Integration**: Updated `AutopilotController` and UI triggers to use the `ManeuverManager` for shunting. This enables:
    - 3-second pre-flight safety checks (skipped in MOB emergencies).
    - On-screen progress overlays.
    - Automated helm locking.
- **Automatic Completion**: Implemented logic in `ShuntingManeuver` to automatically complete the maneuver once the vessel stabilizes and COG aligns with the new Heading.

### 3. Safety & UI Improvements
- **Unit Conversion**: Fixed the SOG safety check to correctly convert m/s to Knots, ensuring a precise 0.5 kn threshold.
- **Emergency Speed**: Optimized `SafetyPreflightController` to skip the mandatory 3-second delay if a shunt is triggered during a Man Overboard (MOB) situation.
- **UI Localization**: Added missing strings for shunting instructions and TTS announcements to `strings.xml`.
- **Maneuver List**: Added the "Shunting" option to the tactical maneuvers bottom sheet specifically for Proa vessel types.

## Verification Results

### Telemetry Logic
- Verified `headingTrue` flips (e.g., 0° -> 180°) while `courseOverGroundTrue` remains constant (e.g., 0° remains 0°).
- Verified `leeway` sign inversion (0.1 rad -> -0.1 rad).

### Workflow Execution
- Triggering a shunt now correctly shows the "Shunting Maneuver Active" overlay.
- During the shunt, manual heading nudges are blocked by the `PRIORITY_TACTICAL_MANEUVER` helm lock.
- The maneuver completes automatically when the boat gathers speed in the new direction.

render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/MultihullShuntManager.kt)
render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/ShuntingManeuver.kt)
render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotController.kt)
render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SafetyPreflightController.kt)
