# Nautical Capability Matrix - OsmAnd Nautical Plugin

This document provides a comprehensive audit of the nautical features implemented in the OsmAnd Nautical Plugin, assessed from an end-user product perspective.

## 1. AIS & Collision Awareness

| Property | Details |
| :--- | :--- |
| **Purpose** | Display real-time vessel traffic and provide collision risk analysis (CPA/TCPA). |
| **Status** | **Mostly Complete** |
| **Main Classes** | `NauticalAisManager`, `SignalKEngine`, `NauticalAisLayer`, `AlarmPriorityManager`, `AisTrackerMath` |
| **Execution Flow** | AIS data (NMEA or Signal K JSON) -> `AisDecoder` -> `NauticalAisManager` -> `AisObject` updates -> `AlarmPriorityManager` evaluates risk -> `NauticalAisLayer` renders vessels. |
| **Missing UX/Gaps** | No mechanism to sort target lists by threat level; no "Ignore/Mute" function for specific targets. |
| **Suggested Improvements** | Implement a "Hazard Focus" mode that highlights vessels with TCPA < 10 mins and dims others. |
| **Complexity / Value** | **M** / **High** |

## 2. S-57 / S-63 Vector Charting

| Property | Details |
| :--- | :--- |
| **Purpose** | Render standard IHO S-57 and encrypted S-63 hydrographic vector charts. |
| **Status** | **Complete** |
| **Main Classes** | `S57FileReader`, `S57SpatialIndex`, `S57MapLayer`, `S63Decryptor`, `S52SymbolManager` |
| **Execution Flow** | Chart file -> `S57FileReader` -> `S57SpatialIndex` (SQLite) -> `S57FeatureStylizer` -> `S57MapLayer` (Canvas rendering). |
| **Missing UX/Gaps** | Native font rendering for some sounding labels can be inconsistent at extreme zoom. |
| **Suggested Improvements** | Add support for "Custom Safety Depth" highlighting across the entire chart. |
| **Complexity / Value** | **L** / **High** |

## 3. Tactical Sailing Performance

| Property | Details |
| :--- | :--- |
| **Purpose** | Provide sailing-specific metrics: Polars, Laylines, VMG, and Wind Shifts. |
| **Status** | **Complete** |
| **Main Classes** | `PolarDiagram`, `LaylineMathEngine`, `SailingLaylinesMapLayer`, `WindTrendHudHeader` |
| **Execution Flow** | Wind/Speed data -> `PolarDiagram` calculates targets -> `LaylineMathEngine` projects vectors -> `SailingLaylinesMapLayer` renders on map. |
| **Missing UX/Gaps** | "Auto-selection" of polar profiles based on sail inventory state is manual. |
| **Suggested Improvements** | Visual "Wind History" sparkline on the main HUD for shift trend analysis. |
| **Complexity / Value** | **M** / **High** |

## 4. Weather Routing & GRIB

| Property | Details |
| :--- | :--- |
| **Purpose** | Optimal passage planning using weather forecasts and vessel performance. |
| **Status** | **Complete** |
| **Main Classes** | `IsochroneRoutingEngine`, `GribParser`, `GribRepository`, `WeatherRoutingMapLayer` |
| **Execution Flow** | GRIB download -> `GribParser` -> `IsochroneRoutingEngine` (Dijkstra-based) -> `OptimalRouteResult` -> `MapLayer`. |
| **Missing UX/Gaps** | No "Ensemble" routing support (calculating multiple routes for different GRIB runs). |
| **Suggested Improvements** | Integrate RainViewer API for real-time precipitation radar overlays. |
| **Complexity / Value** | **L** / **High** |

## 5. Tidal & Current Analysis

| Property | Details |
| :--- | :--- |
| **Purpose** | Predict and visualize tide heights and tidal current vectors. |
| **Status** | **Complete** |
| **Main Classes** | `SignalKTideManager`, `TideCalculationEngine`, `TidalCurrentsMapLayer`, `TideGraphView` |
| **Execution Flow** | Harmonic constituents -> `TideCalculationEngine` -> `TidePrediction` -> `TidalCurrentsMapLayer` renders animated vectors. |
| **Missing UX/Gaps** | Current vector interpolation between distant stations is basic (linear). |
| **Suggested Improvements** | Add "Tide-aware anchoring" helper that checks if enough scope is out for low tide. |
| **Complexity / Value** | **M** / **High** |

## 6. Safety & Emergency Response

| Property | Details |
| :--- | :--- |
| **Purpose** | Manage MOB, Anchor Watch, and high-priority safety alarms. |
| **Status** | **Mostly Complete** |
| **Main Classes** | `MobStateMachine`, `AnchorDriftWatchdog`, `AlarmPriorityManager`, `NauticalAudioArbiter` |
| **Execution Flow** | Trigger (Button/Sensor) -> `MobStateMachine` -> `MobMapLayer` + Audio Alarm. Position update -> `AnchorDriftWatchdog` -> Proximity check. |
| **Missing UX/Gaps** | **Missing Dead Man's Switch (Solo Watchdog)**; no "Safe to Mute" interaction for low-priority alarms. |
| **Suggested Improvements** | Implement a solo-sailing watchdog timer reset via long-press on the HUD. |
| **Complexity / Value** | **S** / **High** |

## 7. Vessel Control & Monitoring

| Property | Details |
| :--- | :--- |
| **Purpose** | Interface with Autopilots and Digital Switching (N2K/Signal K). |
| **Status** | **Complete** |
| **Main Classes** | `AutopilotController`, `AutopilotManager`, `NauticalSwitchPanelFragment`, `ElectricalController` |
| **Execution Flow** | UI Interaction -> `AutopilotController` -> `PUT` request to Signal K server -> Hardware feedback via Delta stream. |
| **Missing UX/Gaps** | No dedicated "Night Mode" UI for digital switching (standard buttons used). |
| **Suggested Improvements** | Add "Maneuver Linking" (e.g., auto-engaging Autopilot in "Wind" mode after a tack). |
| **Complexity / Value** | **M** / **Medium** |

## 8. Automated Marine Logbook

| Property | Details |
| :--- | :--- |
| **Purpose** | Automatic recording of voyages, engine hours, and environmental snapshots. |
| **Status** | **Complete** |
| **Main Classes** | `AutomatedLogbookEngine`, `MarineLogbookRepository`, `MarineLogbookFragment` |
| **Execution Flow** | Speed > Threshold -> `LogbookEngine` starts trip -> Periodic snapshots of `MarineState` -> `SQLite` storage -> CSV/GPX Export. |
| **Missing UX/Gaps** | No photo attachment capability within log entries. |
| **Suggested Improvements** | "Share Log" feature that generates a beautiful trip summary image for social media. |
| **Complexity / Value** | **M** / **Medium** |

## 9. Hazard & Infrastructure Alerts (NAVTEX)

| Property | Details |
| :--- | :--- |
| **Purpose** | Receive and map safety messages and navigate within safety corridors. |
| **Status** | **Complete** |
| **Main Classes** | `NavtexMessageDecoder`, `NavtexMapLayer`, `SafetyCorridorChecker` |
| **Execution Flow** | Serial/Network stream -> `NavtexSentenceParser` -> `NavtexMessage` -> `S57SpatialIndex` cross-ref -> Map highlight. |
| **Missing UX/Gaps** | No support for proprietary Dutch waterway blockage formats (Vaarweginformatie). |
| **Suggested Improvements** | Proactive "Bridge Clearance" alerts based on `airDraft` vs Navtex bridge status. |
| **Complexity / Value** | **L** / **Medium** |

## 10. External Integrations (Cloud/Community)

| Property | Details |
| :--- | :--- |
| **Purpose** | Sync with third-party maritime services (ActiveCaptain, Orca, Cloud Depth). |
| **Status** | **Missing** |
| **Main Classes** | N/A |
| **Execution Flow** | N/A |
| **Missing UX/Gaps** | No community-sourced POI or depth data. |
| **Suggested Improvements** | Add ActiveCaptain POI layer and "Upload Depth" toggle for crowdsourced bathymetry. |
| **Complexity / Value** | **L** / **Medium** |
