# Implementation Plan - Phase 8.0C: Pilot Tuning Bottom Sheet

This plan details the implementation of a live, glove-friendly autopilot tuning interface within the `NauticalPilotBottomSheet`. This interface will replace transient settings dialogs by providing direct access to essential tuning parameters.

## User Review Required

> [!IMPORTANT]
> The existing `NauticalPilotBottomSheet` will be enhanced with a new "PILOT TUNING" section containing four touch-friendly sliders. This might increase the overall height of the bottom sheet, but it will be wrapped in a `NestedScrollView` to ensure accessibility on all screen sizes.

## Proposed Changes

### Nautical Plugin

#### [MODIFY] [nautical_pilot_bottom_sheet.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/bottom_sheet_nautical_pilot.xml)
- Remove the `sea_state_container` from the `steering_card`.
- Add a new "PILOT TUNING" section at the bottom of the layout (before the digital switching section or between telemetry and switching).
- Include four sliders with appropriate labels and $>48\text{ dp}$ touch targets:
    - Rudder Gain (`slider_rudder_gain`)
    - Counter Rudder (`slider_counter_rudder`)
    - Auto Trim (`slider_auto_trim`)
    - Sea State (`slider_sea_state`, moved here)

#### [MODIFY] [NauticalPilotBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotBottomSheet.kt)
- Bind the three new tuning sliders (`rudder_gain`, `counter_rudder`, `auto_trim`) in `onViewCreated`.
- Initialize their values from `OsmandSettings`.
- Implement `addOnChangeListener` for each slider to:
    - Update the corresponding `OsmandSettings`.
    - Call the appropriate method in `AutopilotController` (e.g., `setRudderGain`) for real-time hardware updates.
    - Trigger haptic feedback (`CLOCK_TICK`).
- Ensure `seaStateSlider` logic remains consistent with "AUTO" mode handling.

## Verification Plan

### Automated Tests
- Since this is primarily a UI change with direct hardware/settings interaction, verification will focus on manual testing and ensuring the code compiles.

### Manual Verification
- Deploy to an Android device.
- Open the `NauticalPilotWidget` and tap to show the bottom sheet.
- Verify that the "PILOT TUNING" section is visible.
- Interact with the Rudder Gain, Counter Rudder, and Auto Trim sliders.
- Verify (via logs or observed behavior if hardware is available) that `AutopilotController` receives the updates.
- Verify that "Sea State" still works and respects the "AUTO" toggle.
- Verify that sliders are easy to touch (glove-friendly).
