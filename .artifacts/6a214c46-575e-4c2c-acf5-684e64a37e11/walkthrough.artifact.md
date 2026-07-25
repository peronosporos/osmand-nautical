# Walkthrough - Sea State Automation

I have implemented an intelligent "Auto Sea State" feature for the Nautical Pilot. This feature uses the vessel's motion telemetry (Roll and Pitch) to automatically tune the autopilot's sensitivity, ensuring optimal performance as conditions change.

## Changes Made

### 1. Intelligence Engine
- **Motion Analysis**: Added a standard deviation heuristic in `AutopilotController` that analyzes the last 60 seconds of IMU data.
- **Dynamic Tuning**: Automatically maps motion intensity (in degrees) to one of 5 Sea State levels.
- **Efficiency**: Throttled the calculation to every 30 seconds to prevent unnecessary network traffic and allow for stable filtering.

### 2. UI/UX Enhancements
- **AUTO Toggle**: Added a modern `MaterialSwitch` to the Pilot dashboard to enable/disable automation.
- **Visual Feedback**:
    - When **AUTO** is active, the manual slider is disabled and dimmed to indicate system control.
    - The slider position updates in real-time to reflect the level chosen by the automation engine.
- **Manual Override**: Turning AUTO off immediately restores full manual control via the slider.

### 3. Safety & Robustness
- **Quiet Operation**: Automation updates no longer trigger toasts, preventing UI clutter while sailing.
- **Hysteresis**: The engine only sends updates when a distinct level change is detected.

## Verification Results

### Logic Check
- **Calm Waters**: Motion < 1° Std Dev -> Level 1 (Precision).
- **Moderate Sea**: Motion 3-6° Std Dev -> Level 3.
- **Heavy Weather**: Motion > 10° Std Dev -> Level 5 (Deadband).

### UI Integration
- Verified the `NauticalPilotBottomSheet` correctly binds the switch and slider states.
- Verified that `NauticalPlugin` correctly hooks into the `MarineState` stream to trigger updates.

render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotController.kt)
render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/bottom_sheet_nautical_pilot.xml)
