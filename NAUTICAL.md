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

### Course (COG) Line (Solid Green with Arrow)
- Shows the boat's actual path over ground (Course Over Ground).
- The difference between the Heading and COG lines visualizes the effect of current and leeway.

### Current Vector (Solid Blue with Arrow)
- Directly visualizes the **Set and Drift** (water current).
- If not provided by SignalK, it is automatically calculated locally from the vector difference between Heading/STW and COG/SOG.

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
- **Trust all certificates**: Use this option for local boat networks with self-signed certificates.
- Local calculation engine ensures a "Single Source of Truth" for all displays, even when server data is incomplete.
