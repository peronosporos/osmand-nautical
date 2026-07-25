# OsmAnd Nautical Plugin: Strategic Audit & Next-Gen Roadmap

This document provides a targeted feature audit and competitive gap analysis of the OsmAnd Nautical Plugin, followed by a roadmap for high-value mobile-first expansions.

## Executive Summary
OsmAnd's nautical implementation is uniquely positioned due to its **offline-first vector engine** and **Signal K integration**. While it lacks the commercial chart polish of Navionics or the hardware-heavy multiplexing of OpenCPN, it excels in battery efficiency and sensor-to-map visualization for small-to-medium vessels using mobile tablets as their primary MFD (Multi-Function Display).

---

## Step 1: Strategic Gap Analysis

### Competitive Comparison Matrix

| Feature | OsmAnd Nautical | Navionics | OpenCPN (Mobile) | AvNav | Signal K Dashboard |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Chart Data** | OpenSeaMap / OSM | Proprietary (Navionics) | S-57 / S-63 / MBTiles | MBTiles / S-57 | N/A (Data only) |
| **Connectivity** | Signal K / NMEA 0183 | Bluetooth (Internal) | NMEA 0183 / 2000 | NMEA / Signal K | Native Signal K |
| **Offline Performance** | **High (Native)** | Medium (Caching) | Medium (Heavy UI) | High (Web-Cached) | Low (Web-based) |
| **Tactical Tools** | Laylines / Wind Shifts | Basic | Advanced (Plugins) | Advanced | N/A |
| **Community Data** | Via OSM Edits | **SonarChart / Live** | Crowdsourced Plugins | N/A | N/A |
| **Multi-Display** | Client Only | N/A | Master/Slave | **Web Gateway** | Native |

### Strengths & Weaknesses

#### **OsmAnd Strengths**
- **Unified Sensor Engine:** The use of `SignalKEngine` as a central data bus allows OsmAnd to treat Bluetooth NMEA, TCP NMEA, and Signal K WebSockets as equivalent inputs.
- **Battery & UI Consistency:** Unlike OpenCPN, which feels like a desktop port, OsmAnd follows Material Design, ensuring low battery drain and intuitive gesture control.
- **Tactical Real-time Overlays:** Existing support for **Laylines** and **Wind Shift Arcs** provides racing-grade utility that is often missing in standard recreational apps like Navionics.
- **Anchor Watchdog:** Robust implementation with signal filtering (accuracy-based rejection) and time-delayed triggers.

#### **Identified Gaps**
- **Dynamic Hazard Reporting:** Navionics users can report a new rock or shallow area instantly. OsmAnd relies on the standard `OsmEditingPlugin`, which isn't specialized for nautical workflows (e.g., depth soundings).
- **Voyage Reconstruction:** While OsmAnd logs GPX tracks, it does not currently store the raw NMEA telemetry (Wind, Depth, Engine data) associated with those coordinates for post-voyage analysis.
- **Anchor Drag Visualization:** The current Anchor Watch is "binary" (In/Out). Competitive marine apps provide a "Snail Trail" to help skippers distinguish between a swinging anchor and a dragging one.

---

## Step 2: Next-Gen High-Value Roadmap

### 1. Community Nautical Edits (Hazard Reporting)
**Concept:** A specialized UI overlay for the `OsmEditingPlugin` focused on marine hazards.
- **Architectural Blueprint:**
  - Create `NauticalOsmEditFragment` that extends `BaseOsmAndFragment`.
  - Add specific presets for nautical hazards: *Unmarked Obstruction*, *Buoy Off Station*, *Incorrect Depth*, *Temporary Restriction*.
  - Integrate with OpenSeaMap tags (e.g., `seamark:type=obstruction`).
  - **Value:** Leverages the existing OSM ecosystem while providing the "instant reporting" feel of Navionics.

### 2. NMEA Telemetry Logging & Replay
**Concept:** Capture the raw data stream for offline "debriefing" and simulation.
- **Architectural Blueprint:**
  - **Logger:** Extend `NmeaClient` to optionally pipe raw sentences to a `.nmea.log` file in the OsmAnd data directory, synced with the GPX track ID.
  - **Replay Engine:** Implement `NmeaPlaybackEngine` (implementing `NmeaClient` interface). It reads from the log file and emits sentences to the `DirectNmeaMultiplexer` at real-time or 2x/4x speed.
  - **UI:** A "Logbook Playback" mode where the map displays the vessel's past position and all HUD widgets (SOG, Wind, Depth) update according to the log.
  - **Value:** Essential for tactical analysis (sailing) and engine monitoring history.

### 3. Anchor Drag Track History ("Snail Trail")
**Concept:** Visualizing the vessel's movement within the anchor circle over the last 12-24 hours.
- **Architectural Blueprint:**
  - **Persistence:** Modify `AnchorDriftWatchdog` to maintain a circular buffer of the last *N* positions (e.g., one point every 60 seconds).
  - **Rendering:** Update `AnchorWatchMapLayer` to draw a "faded path" connecting these points. Use a color gradient (e.g., blue for older, white for newer) or decreasing opacity.
  - **Context:** Display the path *relative to the anchor drop point*, allowing the user to see the "swing pattern" (arc) vs. "drag pattern" (line).
  - **Value:** Prevents "false panic" by showing that the boat is simply swinging with the tide.

---

## Architectural Guidelines for Implementation
- **Module:** All nautical logic remains in `:OsmAnd` under `net.osmand.plus.plugins.nautical`.
- **Performance:** Replay engine must run on a background thread to avoid jank in the main UI loop.
- **Storage:** Use `okio` for efficient NMEA log streaming to prevent I/O bottlenecks.
- **Consistency:** All new UI must support **Night Vision (Red Filter)** by using standard theme attributes or `NIGHT_VISION_FILTER`.
