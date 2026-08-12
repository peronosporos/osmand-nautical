# Walkthrough - Mooring Maneuver Refactoring & Polish

I have completed the refactoring and polishing of the Nautical plugin's mooring maneuver functionality.

## Changes Made

### Adaptive Thresholds (Logic Scaling)
- **Scaled Mooring Thresholds**: Both `MooringManeuver` and `MedMooringManeuver` now scale their distance checks based on the `vesselLengthMeters` setting.
    - **Approach Trigger**: Now starts at `3 * vesselLength`.
    - **Completion Trigger**: Triggers at `0.2 - 0.3 * vesselLength` (min 2m).
    - **Safety Speed Zone**: The speed limit enforcement now starts when the distance is less than the vessel's length.

### Frontend: Parameter Adjustment UI
- **Tactical Maneuver Sheet**: Added a new "Maneuver Parameters" section to the bottom sheet.
- **Quick Adjustment**: Users can now adjust **Vessel Length** and **Anchor Scope** directly before starting a maneuver using +/- buttons. This updates the underlying OsmAnd settings in real-time.
- **Improved UX**: Combined with the added Med-Mooring option, the sheet now serves as a central cockpit for preparing complex arrivals.

### Backend Engine Improvements
- **Unified Feedback**: All maneuvers use the new `ManeuverEngine.speak()` for consistent, thread-safe voice instructions.
- **Precise Calculation**: Switched to geodesic distance math (`KMapUtils`) to ensure accuracy globally.
- **Phase Logic**: Med-mooring now correctly distinguishes between dropping the anchor, paying out rode, and the final stern approach.
- **State Restoration**: Autopilot mode is preserved and restored after complex maneuvers.

### Resources & Theming
- **Dynamic Icons**: The maneuver widget now correctly displays `ic_action_anchor` for all mooring types.
- **Themed Widget**: The maneuver overlay now respects application themes (Light/Dark) via the `nautical_widget_background` attribute.
- **String Unification**: Consolidated all maneuver-related labels and instruction formats in `strings.xml`.

## Verification Results

### UI Integrity
- Verified the layout of the `NauticalManeuversBottomSheet` on both small and large screens.
- Confirmed that button clicks update the labels and persist to settings.

### Logic Accuracy
- Analyzed the scaling formulas to ensure they provide sensible defaults for boats ranging from 5m to 30m.
- Fixed a method shadowing issue in `MedMooringManeuver`.
