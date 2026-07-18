# Nautical UI & Unit Handling Analysis

This document explains the technical rationale behind the recent changes and describes the expected behavior for each component.

## 1. The "XTE" (Cross-Track Error) Change

### Why it was added
Previously, the Pilot widget lacked a direct way to know if the boat was deviating from a route defined in the SignalK server. **Cross-Track Error (XTE)** is the standard marine navigation metric representing the perpendicular distance between the vessel's current position and the intended track.

### Expected Behavior
- **SignalK Source**: We now parse `navigation.crossTrackError` (provided in meters by SignalK).
- **Pilot Widget**: It compares this XTE value against your "Off Course Threshold" (configured in Nautical Plugin Settings, usually in Nautical Miles).
- **Alerting**: If the boat drifts further than the threshold (e.g., > 0.1 NM), the Pilot widget displays **"OFF COURSE"** in red. Following your instructions, we removed the distance number and units (e.g., "0.15 NM") from this display to keep the Pilot UI focused purely on status.

---

## 2. Widget Behavior & Unit Handling

Every nautical widget now operates on a **Per-Widget Toggle** found in its individual settings ("Use Nautical Standard Units").

### Marine Text Widgets (Depth, Wind, VMG, COG)
| Setting | Speed (VMG, Wind) | Depth | COG / Heading |
| :--- | :--- | :--- | :--- |
| **Nautical (ON)** | Knots (`kn`) | Meters (`m`) | Degrees (`°`) |
| **Local (OFF)** | `km/h` or `mph`* | `m` or `ft`* | Degrees (`°`) |
*\*Based on global OsmAnd Metrics settings.*

### Nautical Pilot Widget
- **Title**: The "Nautical Pilot" text is now **permanently removed** to maximize map visibility.
- **Status Text**: Shows "STBY" (standby) or the current target heading (e.g., "145°").
- **Units**: No distance or unit labels are shown here anymore.

---

## 3. Graph Widgets Behavior

The graphs now mirror the unit logic of the text widgets.

### Expected Graph Data Conversion
- **Depth Graph**:
    - **Nautical**: Displays in Meters.
    - **Local**: Displays in Meters or Feet (depending on OsmAnd settings).
- **Wind & VMG Graphs**:
    - **Nautical**: Data is converted from m/s to **Knots** (multiplier 1.94).
    - **Local**: Data is converted from m/s to **km/h** (multiplier 3.6) or **mph**.

### UI Consistency
- The unit label at the top/side of the graph (e.g., "kn", "m", "ft") will update automatically when you toggle the setting.
- This ensures that if you look at the VMG text widget and the VMG graph widget, they both show the same units if configured identically.

> [!TIP]
> To change these settings, long-press any widget on the map, select "Configure Widget", and toggle "Use Nautical Standard Units".
