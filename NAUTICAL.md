# Nautical Plugin - User Guide & Features

OsmAnd's Nautical Plugin provides advanced tools for sailing and marine navigation, integrating seamlessly with SignalK and providing critical telemetry directly on your map.

## 1. Map Overlays

Configure these in the **"Configure Map"** menu under the **"Nautical"** category.

### Laylines
- Visualizes the upwind sailing angles (tacks).
- **Tack Angle**: Default is 45°, but can be configured in **Nautical Settings** to match your vessel's performance.
- Automatically adjusts based on True Wind Direction from SignalK.

### Wind Shifts
- Displays an arc on the boat representing the history of wind direction.
- Uses a "Smallest Arc" algorithm to correctly show shifts even when they cross North (0°).
- Helps identify "lifts" and "headers" for tactical advantage.

### Trajectory
- Draws a high-resolution trail of your boat's path.
- Stores up to **1000 points** for a comprehensive view of your recent track.
- Correctly stays anchored to the map during panning, zooming, and rotation.

---

## 2. Vessel Indicators (Projections)

Found in **"Configure Map" > "Nautical" > "Vessel Indicators"**. These lines are scaled by a configurable **"Projection time"** (e.g., 10 minutes).

### Heading Line (Dashed)
- Shows the direction the boat's bow is pointing.
- Length represents the distance the boat will travel through water in the projection time.
- **Fallback**: If "Speed Through Water" is missing, it uses "Speed Over Ground" for length calculation.

### Course (COG) Line (Long Dashed Green with Arrow)
- Shows the boat's actual path over ground (Course Over Ground).
- The difference between the Heading and COG lines visualizes the effect of current and leeway.

### Current Vector (Short Dashed Blue with Arrow)
- Directly visualizes the **Set and Drift** (water current).
- If not provided by SignalK, it is automatically calculated locally from the vector difference between Heading/STW and COG/SOG.

### Target Heading Line (Orange Dashed with Arrow)
- Appears only when an autopilot is connected and active.
- Visualizes the course the autopilot is currently trying to maintain.

---

## 3. Nautical HUD Widgets

Add these to your screen via **"Configure Screen"**.

- **SOG**: Speed Over Ground.
- **STW**: Speed Through Water.
- **COG**: Course Over Ground.
- **Set & Drift**: Unified current direction and speed.
- **Depth**: Water depth below transducer.
- **VMG**: Velocity Made Good to wind or waypoint.
- **Wind**: True or Apparent wind speed and direction.
- **Pilot**: Interactive autopilot control and status.

---

## 4. Night Vision

The Nautical plugin includes a specialized **Night Vision** mode.
- Apply a deep red filter to the entire UI to preserve your night vision.
- Toggle it via the **Night Vision widget** or in the plugin settings.
- All map indicators (laylines, projections, etc.) automatically switch to high-contrast red in this mode.

---

## 5. Technical Integration (SignalK)

The plugin connects to **SignalK** servers via WebSockets.
- Supports secure connections (wss://).
- **Magnetic Heading Fallback**: If the server does not provide True Heading, the plugin automatically calculates it using Magnetic Heading and Variation (if available).
- **Trust all certificates**: Use this option for local boat networks with self-signed certificates.
- Local calculation engine ensures a "Single Source of Truth" for all displays, even when server data is incomplete.

- Local calculation engine ensures a "Single Source of Truth" for all displays, even when server data is incomplete.

---

## 6. Nautical Settings Organization

Open the plugin settings from **Plugins > Nautical**. The main screen is a compact hub of quick toggles:

- **Display**: vessel context, display mode, heavy weather mode.
- **Modules**: enable or disable the AIS, radar, depth profile, polar overlay, cockpit strip, tides, GRIB, VHF, logbook, ENC, raster charts, and Navtex modules.
- **Chart Overlays**: quickly toggle laylines, dead reckoning, isochrones, anchor zone, and overlay layers.
- **Smart Tools**: Boat AI, notifications, and checklists.

The rest of the settings are grouped into five dedicated screens, reachable under **More settings**:

### Vessel & Maneuvers
Vessel type (conventional or proa), draft, keel offset, wind alignment, heading reference, autopilot tuning, maneuver parameters, and sailing performance (polars, layline tack angle, leeway, wave bias).

### Safety & Anchor
Safety margins, XTE and off-course alarms, corridor width and buffer, actuator alarm, MOB audio guidance and interval, arrival radius, look-ahead, safety regions, and the anchor watch configuration.

### AIS, VHF & Navtex
AIS target display options, the VHF backend, and Navtex station filters.

### Connection & Instruments
Signal K server address and authentication, NMEA sources (network, Bluetooth, USB), sensor fallback timeout, compass calibration wizard, power management, hardware health, Signal K diagnostics, and the telemetry text widgets configuration.

### Charts & Voyage Data
ENC, S-63 permits (BYOK), tides, GRIB weather downloads, server routes and charts, raster charts, passage planning, the voyage logbook, and data cleanup.
