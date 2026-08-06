# OsmAnd Nautical Plugin - Master Exhaustive & Cumulative Audit Report

This report is the definitive record of all functional components, UI/UX gaps, and technical discoveries within the Nautical plugin. It compares the current "Development Baseline" with the "PR-Ready Target".

## 1. Functional Completeness Matrix

| Domain | Logic Implementation | UI Accessibility | Configurability | Synergy |
| :--- | :--- | :--- | :--- | :--- |
| **MOB Emergency** | ✅ Complete | ✅ HUD + Map | ✅ Audio timings | ✅ Full |
| **Anchor Watch** | ✅ Smart Rode Fallback | ✅ Dialog + HUD | ✅ Full | ✅ Two-Way |
| **Autopilot Control** | ✅ Multi-Vendor Drivers | ✅ Pilot Sheet | ✅ Advanced PID | ✅ Full |
| **SAR Patterns** | ✅ 5 Patterns (Eng Square etc) | ✅ Maneuver Menu | ⚠️ Partial | ❌ No |
| **ENC Charts (S-63)** | ✅ Blowfish Decryptor | ✅ ENC Manager | ✅ Permit Store | ❌ N/A |
| **Vessel Design** | ✅ Technical Fragment | ⚠️ Read-Only | ❌ No (Read-only) | ❌ Read-Only |
| **Performance** | ✅ 3D Motion Correction | ✅ Widgets | ⚠️ Alpha only | ⚠️ Read-Only |
| **Logbook** | ✅ Forensic Black Box | ✅ Logbook Fragment | ✅ Interval only | ⚠️ Sync-Push Only |
| **Weather (GRIB)** | ✅ Interpolation Engine | ✅ GRIB Manager | ✅ Interpolation | ❌ Read-Only |

---

## 2. Identified Gaps & PR Blockers (Cumulative List)

### 2.1 Critical PR Blockers (Localization & Style)
> [!CAUTION]
> The following items must be resolved before any upstream merge.

1.  **Remaining Hardcoded Strings (~80):**
    -   `VhfPoiSearchLayer.kt`: "Copied VHF Channel to clipboard", "Marine Station".
    -   `S57ObjectMenuController.kt`: Technical attribute labels ("Acronym", "Name (Local)").
    -   `PatternSteeringEngine.kt`: Instructions like "Stalled in irons".
    -   `AlarmPriorityManager.kt`: Voice text for solo watchdog timeout.
2.  **UI Style Inconsistency:**
    -   `NauticalTechnicalStatsFragment.xml`: Uses hardcoded text sizes (24sp) and styles.
    -   `dialog_nautical_sar_config.xml`: Uses hardcoded padding and Material 2 artifacts.
3.  **Hardcoded Connection Data:**
    -   `PolarEditorFragment.kt`: Still contains a hardcoded reference to `127.0.0.1` as a default fallback string (partially mitigated).

### 2.2 Functional & Management Gaps (Synergy)
1.  **Vessel Profile Write-Back:** Boaters cannot correct their beam, length, or air draft from the stats fragment. The data is 100% read-only.
2.  **Buddy/Checklist Creation:** No UI to add an AIS target as a buddy or create a new safety checklist.
3.  **Local Anchor Sync:** Setting a local anchor in OsmAnd does not push to Signal K's `navigation.anchor` path (Crew sync failure).
4.  **Logbook Persistence:** Edits to logbook notes made in the app are **never pushed** back to the server, creating a fork in the digital log.

### 2.3 UX & Safety Gaps
1.  **Recovery Mode Invisibility:** When a maneuver aborts and the boat bears away 10°, there is no HUD indication that the boat is in an "Autopilot Abort Recovery" state.
2.  **Touch Lock Feedback:** No visible icon (lock symbol) on the map when the touch guard is active.
3.  **Onboarding Shortfall:** The Setup Wizard misses MMSI and Draft steps (Safety vitals).

---

## 3. Synergy Audit: OsmAnd vs Signal K Server

| Feature | Local Control | Server Command | Synergy Status |
| :--- | :--- | :--- | :--- |
| **Autopilot Mode** | ✅ Yes | ✅ `PUT state` | 100% |
| **Waypoints** | ✅ Yes | ✅ `POST resources` | 100% |
| **Electrical** | ✅ Yes | ✅ `PUT state` | 100% |
| **AIS Buddies** | ❌ No | ✅ `GET list` | **DISPLAY ONLY** |
| **Anchor** | ✅ Local | ⚠️ `GET list` | **OUT-OF-SYNC** |
| **Vessel Design** | ❌ No | ✅ `GET list` | **READ-ONLY** |

---

## 4. Final Verdict for PR Readiness
The plugin is **90% functionally complete** but **40% PR-Ready**. The technical depth of the safety and steering engines is world-class, but the lack of localization and the "Read-Only" nature of boat management features will result in rejection from the core OsmAnd project.

---
*End of cumulative exhaustive audit.*
