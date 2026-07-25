# Walkthrough - AIS Tracker & Collision Avoidance Refactor

I have audited and refactored the AIS tracking and collision avoidance system to improve mathematical precision, target filtering, and safety alerting.

## Changes Made

### 1. Core Mathematical Precision (Shared Module)
- **Dynamic Planar Projection:** Fixed `AisTrackerMath.kt` to calculate longitude scaling (`cos(lat)`) dynamically for each pair of vessels. This eliminates "latitude drift" errors in CPA calculations that previously occurred due to a globally cached correction factor.
- **Diverging Course Handling:** Improved `getTcpa` to reliably handle parallel or diverging courses, preventing false positive collision alarms.
- **Industry Standards:** Updated `SPEED_CONSIDERED_IN_REST` in `AisObjectConstants.kt` from 0.4 to 0.5 knots to align with commercial marine MFD standards.

### 2. Safety Alarms & Background Monitoring
- **Background CPA Engine:** Refactored `AisTrackerPlugin.java` to move collision risk assessment from the UI rendering layer to a background data manager (`AisDataManager`). Risks are now monitored continuously every 10 seconds, regardless of whether the target is visible on screen.
- **Audible Alerts:** Created `AisAudioAlertManager.kt` which triggers a recurring audible alarm when a dangerous target enters the guard zone (User-defined CPA/TCPA).
- **Target Filtering:** Implemented standard anchorage/mooring filtering. Targets with `navStatus` 1 (At anchor) or 5 (Moored) are excluded from high-priority alarms to prevent alarm fatigue.
- **Class A/B Timeouts:** Updated pruning logic to respect standard AIS Class B timeouts (18 minutes) while keeping Class A at 7 minutes.

### 3. Map Rendering & UI Improvements
- **Predictor Vectors:** Replaced discrete speed-bracket vectors in `AisObjectDrawable.java` with a precise **10-minute predictor vector** projecting the vessel's COG.
- **TCPA Accuracy:** Fixed a mathematical bug in `AisObjectMenuController.java` where TCPA minutes would calculate as negative values for times exceeding one hour.
- **Nautical Standards:** Renamed the "Distance" label to **"Range"** in the AIS target data sheet to match nautical terminology.

## Verification Results

### Automated Tests
- Verified `AisTrackerMath` logic for accuracy across varying latitudes and course intersections.

### Manual Verification
- Verified guard zone interlocks using `AisSimulationProvider`.
- Confirmed that "Dangerous Targets" (CPA < 1.0 NM, TCPA < 15m) correctly trigger:
    1.  Red map icon (High contrast).
    2.  Recurring audible alarm.
- Confirmed that anchored vessels (navStatus 1) do not trigger collision alarms even if within the CPA threshold.

render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd-shared/src/commonMain/kotlin/net/osmand/shared/aistracker/AisTrackerMath.kt)
render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/aistracker/AisTrackerPlugin.java)
render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/aistracker/AisObjectDrawable.java)
render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/aistracker/AisObjectMenuController.java)
