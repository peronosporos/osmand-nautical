# OsmAnd Nautical Plugin - Development Roadmap

This roadmap prioritizes development tasks based on their impact on vessel safety, navigational reliability, and user situational awareness.

## Phase 1: Safety & Critical Alarms (High Priority)

| Task | Capability | Impact | Effort |
| :--- | :--- | :--- | :--- |
| **Solo Watchdog** | Safety | Critical safety for solo sailors; prevents "vessel unattended" accidents. | **S** |
| **AIS Target Management** | AIS | Reduces alarm fatigue by allowing users to "Ignore" known safe targets and sort threats by TCPA. | **M** |
| **Weather Radar Overlay** | GRIB/Weather | Essential for short-term storm avoidance (RainViewer integration). | **M** |

## Phase 2: Enhanced Context & Navigation (Medium Priority)

| Task | Capability | Impact | Effort |
| :--- | :--- | :--- | :--- |
| **ActiveCaptain POI** | Integrations | Adds massive community-sourced database for anchorages, fuel, and hazards. | **L** |
| **Position-Derived Heading**| Navigation | Provides a reliable heading vector when NMEA sensors or COG are unavailable or unstable. | **M** |
| **Bridge Clearance Alerts** | Hazards | Proactive alerts when approaching Navtex-reported bridge closures or low clearance. | **M** |

## Phase 3: Utility & Community (Low Priority)

| Task | Capability | Impact | Effort |
| :--- | :--- | :--- | :--- |
| **Logbook Media** | Logbook | Improves voyage memory by allowing photos/videos to be attached to snapshots. | **M** |
| **Crowdsourced Depth** | Integrations | Contributes to community bathymetry; improves future chart accuracy. | **M** |
| **Tide-Aware Anchoring** | Tides | Automated checking of anchor scope vs. forecasted tidal range. | **S** |

---

> [!NOTE]
> **Recommended Next Step**: Implementation of the **Solo Watchdog** (Dead Man's Switch). This feature has the highest safety-to-effort ratio and fills a verified gap in the existing safety subsystem.
