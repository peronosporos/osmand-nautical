# Nautical Pilot Usage Guide

The Nautical Pilot is a professional-grade command dashboard for OsmAnd's integrated autopilot system. It provides a "Glass Cockpit" experience with real-time instruments and tactile course adjustments.

## Accessing the Pilot
1. Ensure the **Nautical Plugin** is enabled.
2. Add the **Nautical Pilot widget** to your screen (*Configure Screen* -> *Right panel*).
3. Tap the widget to open the **Pilot Command Dashboard**.

## Dashboard Layout

### 1. Command Header
- **Dynamic Feedback:** The header background color changes to show status at a glance:
    - **Green:** Autopilot is ACTIVE.
    - **Grey:** Autopilot is in STANDBY.
    - **Blue:** Autopilot is in WIND mode.
- **Mode Badge:** Displays current operating mode (AUTO, STANDBY, WIND, etc.).
- **Settings Gear:** Access advanced calibration and dynamics tuning.

### 2. Steering Instrument (Heading Arc)
- **Target Course (Active):** Indicated by the bright blue center mark on the arc.
- **Actual Course (Shadow):** Shown as a red bar behind the arc, allowing you to see off-course drift immediately.
- **Course Selection:** Swipe the arc left or right to set a new target. A projection line on the map confirms your new path.

### 3. Precision Adjustment
Buttons are grouped for rapid use in varying sea conditions:
- **+/- 1°:** Precise fine-tuning.
- **+/- 10°:** Large course corrections (tap once to jump 10 degrees).

### 4. Action Center
- **ENGAGE AUTO:** Large primary button to take command.
- **DISENGAGE / STANDBY:** High-contrast safety button.
- **Emergency STOP:** Long-press the DISENGAGE button (a progress bar will confirm) to immediately kill all navigation commands.

## Tips for Best Performance
- **Night Vision:** The dashboard fully supports OsmAnd's Night Vision mode, shifting all instruments to red-on-black for night passage.
- **Seamless Interaction:** The dashboard is non-modal. You can pan and zoom the map behind it while adjusting course.
- **Rudder Feedback:** Watch the linear Rudder Instrument to see how hard the autopilot is working. If it's constantly at its limit, consider adjusting "Sea State" or "Rudder Gain" in Advanced Settings.
