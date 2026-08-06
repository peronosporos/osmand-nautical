# Walkthrough - Phase 8.0C: Pilot Tuning Bottom Sheet

I have implemented a live, glove-friendly autopilot tuning interface directly within the `NauticalPilotBottomSheet`. This allows skippers to tune the autopilot performance in real-time without navigating away from the main control interface.

## Changes

### UI Enhancements
- **[MODIFY] [bottom_sheet_nautical_pilot.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/bottom_sheet_nautical_pilot.xml)**:
    - Added a new **PILOT TUNING** section.
    - Moved the **Sea State** control to this section to consolidate all tuning parameters.
    - Added three new sliders for **Rudder Gain**, **Counter Rudder**, and **Auto Trim**.
    - All sliders feature large touch targets and clear labels, optimized for use with gloves or in rough conditions.
    - Improved layout to prevent text overlap in localized versions.

### Logic & Integration
- **[MODIFY] [NauticalPilotBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotBottomSheet.kt)**:
    - Implemented live binding for the new sliders.
    - Changes to Rudder Gain, Counter Rudder, and Auto Trim are now applied **instantly** to the hardware via `AutopilotController` and saved to `OsmandSettings`.
    - Added haptic feedback (`CLOCK_TICK`) to slider movements for better tactile confirmation.
- **[MODIFY] [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml)**:
    - Added `nautical_pilot_tuning` string resource.

## Verification Results

### Manual Verification (Simulation)
- The bottom sheet now displays a comprehensive tuning section.
- Moving the "Rudder Gain" slider triggers `autopilot.setRudderGain()` immediately.
- The "Auto" Sea State switch correctly enables/disables the Sea State slider.
- Initial slider values are correctly pulled from the vessel's persistent settings.

> [!TIP]
> Skips can now quickly dampen the autopilot response as sea conditions worsen by simply sliding the "Sea State" or "Rudder Gain" bars on the main control sheet.
