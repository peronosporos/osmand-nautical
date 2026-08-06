# Nautical UI Telemetry Audit

This document outlines observed UI layout violations, state desynchronizations, and map rendering failures based on an inspection of the project's nautical telemetry components.

## 1. Conflicting Telemetry Displays

### Heading Mismatch (Pilot vs. Map)
*   **Components:** `NauticalPilotWidget` and `HeadingArcView`.
*   **Violation:** `NauticalPilotWidget` derives heading from `OsmandApplication` preferences or local state, while `HeadingArcView` is prone to consuming raw AIS-derived data or location-provider data (e.g., `AisObject.heading` vs. `Location.bearing`).
*   **Root Cause:** Different smoothing filters. `NauticalPilotWidget` often applies a temporal average, while `HeadingArcView` reflects near-instantaneous `AisObject` values or GNSS updates, leading to visual flickering or drifting values during maneuvers.

### Depth Metric Conflict
*   **Components:** `TacticalHudView` vs. `NauticalMapLayer`.
*   **Violation:** `TacticalHudView` (using `TacticalHudView.kt`) displays depth from real-time transducer data (e.g., NMEA `DBT` or `DPT` sentences), while `NauticalMapLayer` displays depth contour data from cached `.depth.obf` files (see `IndexConstants.kt`).
*   **Root Cause:** Unsynchronized units (meters vs. feet/fathoms) and different interpolation logic between current water depth (sensor) and historical/charted depth (map).

## 2. Inconsistent Alarm States

*   **Observation:** Shallow water alarms trigger independently.
*   **Violation:** `TacticalHudView` (active telemetry display) can enter a visual warning state based on NMEA sensor inputs, while `NauticalMapLayer` (visual map elements) continues rendering neutral contour colorings because the map layer does not subscribe to the same `AlarmManager` or sensor stream as the HUD widgets.
*   **Effect:** Users encounter a "split-brain" warning state where telemetry shows a warning, but the map interface remains deceptively neutral.

## 3. Race Conditions on Profile Switch

*   **Observation:** View lifecycle management during profile transitions (e.g., BOAT to CAR).
*   **Violation:** Orphaned view components.
*   **Mechanism:** `NauticalPilotWidget` and `HeadingArcView` are UI components attached to the `MapActivity` overlay stack. When the application profile switches, the `OsmandApplication` context updates, but these specific nautical widgets are often not explicitly detached or re-initialized.
*   **Result:** Stale or "frozen" telemetry data remains rendered in an overlay layer, obscuring the new context's UI until a full map refresh or screen rotation forces a UI invalidation.
