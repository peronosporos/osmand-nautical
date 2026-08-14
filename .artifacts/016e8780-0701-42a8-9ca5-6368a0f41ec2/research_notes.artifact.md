# Research Findings: Regression Analysis (HEAD vs Commit 3b6ba78)

I have carefully inspected the diffs between the current state and commit `3b6ba78`. Below is the identified list of features and behaviors that were better in that commit.

## 1. Pilot Widget & Rudder View Integration
In `3b6ba78`, the `NauticalPilotWidget` was a functional HUD element that **directly incorporated a rudder indicator bar and marker** at the top of the widget.
- **Lost Feature**: The HUD-based rudder indicator is missing in the current version.
- **Issue**: The layout file `map_hud_pilot_widget.xml` has been deleted in `HEAD`, rendering the `NauticalPilotWidget` code effectively dead or broken.
- **Regressed Interaction**: Previously, the widget supported double-tap for a tactical gate popup. Now, it only toggles autopilot modes or opens a separate bottom sheet, which is less "integrated" for live navigation.

## 2. Telemetry Widget Visuals & Stability
The telemetry widgets (e.g., Depth, Wind, SOG) in `3b6ba78` were simpler and more reliable.
- **Visuals**: They used standard profile colors (Day/Night) and didn't change color based on data age.
- **"Looking Better"**: The current implementation introduces "Integrity States" (`VALID`, `STALE`, `ALARM`). While intended for safety, they cause the widgets to turn yellow or red and display "TIMEOUT" or "X" if data is slightly delayed (e.g., > 3s). This creates a "flickering" or "broken" feel if the SignalK server has high latency.
- **Complexity**: The formatting logic in `MarineTextWidget` and `SignalKUnitConverter` has become significantly more complex, increasing the surface area for bugs in unit conversions and trend indicators.

## 3. GPS Functionality & Location Source
GPS functionality was robust in `3b6ba78` because it didn't interfere with the system location source.
- **The "Forced Source" Bug**: In `HEAD`, `NauticalLocationProvider` **automatically forces** the location source to `EXTERNAL_SIGNALK` whenever the nautical plugin starts (unless source is INTERNAL). This hijacks the GPS and prevents users from using their phone's internal GPS if SignalK position data is missing or poor.
- **Staleness Logic**: Aggressive position staleness checks (10 seconds) in the current version may cause the map position to freeze if the NMEA stream is slow, even if internal GPS is perfectly fine.

## 4. Laylines & Wind Shifts
Laylines and wind shift arcs were "working" because they used a simpler, more direct rendering approach.
- **Wind Shift Rotation Bug**: In `3b6ba78`, the map rotation was **subtracted** (`- tileBox.rotate`) for the wind shift arc. in `HEAD`, it is **added** (`+ tileBox.rotate`). This sign flip likely causes the wind shift arc to point in the wrong direction when the map is rotated.
- **Over-engineered Caching**: The new `SailingLaylinesMapLayer` uses a complex `LaylineCache` that calculates 100 intermediate points for each segment. Any error in the Great Circle math (`calculateIntermediatePoint`) or projection will cause laylines to disappear or look distorted.
- **Server Dependency**: `HEAD` tries to use "Server Laylines" if the server supports them. If the server implementation is buggy, local laylines are suppressed, leading to "laylines not working."

## Summary Table of Regressions

| Feature | Commit 3b6ba78 | Current (HEAD) | Why it was better |
| :--- | :--- | :--- | :--- |
| **Pilot HUD** | Integrated Rudder bar + Marker | Text only; Rudder moved to Sheet | Immediate feedback on helm position in HUD. |
| **GPS Source** | Respects system settings | Forces SignalK Source | Internal GPS worked reliably; now hijacked. |
| **Widget UI** | Stable colors; clean formatting | Yellow/Red "TIMEOUT" alerts | Less distracting; higher perceived reliability. |
| **Laylines** | Simple, correct rotation | Complex caching; sign bug in rotation | They actually appeared and pointed correctly. |
| **Wind shifts** | Correct orientation | Inverted rotation relative to map | Correctly showed wind range on the map. |
