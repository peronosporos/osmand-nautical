# AIS Tracker and Collision Avoidance Audit & Fixes

Audit and fix mathematical precision, target filtering, guard zone alert logic, and map rendering in the OsmAnd AIS tracker.

## User Review Required

> [!IMPORTANT]
> The requested classes `AisCollisionEngine`, `AisTarget`, and `AisRepository` do not exist by these names in the current codebase. I have mapped them to `AisTrackerMath`, `AisObject`, and `AisDataManager` respectively.
> The CPA/TCPA calculation is currently triggered by the UI/Rendering layer. I propose moving this to a background service to ensure continuous monitoring.

## Proposed Changes

### Core Logic (Shared Module)

#### [MODIFY] [AisTrackerMath.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd-shared/src/commonMain/kotlin/net/osmand/shared/aistracker/AisTrackerMath.kt)
- Fix `getLonCorrection` to calculate the factor for each request (per-latitude) instead of using a single global cached value. Use latitude-corrected planar approximation as approved.
- Ensure `getTcpa` returns consistent invalid results for parallel/diverging courses to prevent false alarms.
- Ensure that stationary/moored AIS targets (`SOG < 0.5 knots` OR `navStatus` in [1, 5]) are filtered out of high-priority CPA alarms.

#### [MODIFY] [AisObjectConstants.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd-shared/src/commonMain/kotlin/net/osmand/shared/aistracker/AisObjectConstants.kt)
- Update `SPEED_CONSIDERED_IN_REST` from `0.4` to `0.5` to match industry standards.

### Collision Avoidance Engine & Alarms

#### [NEW] [AisAudioAlertManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/aistracker/AisAudioAlertManager.kt)
- Implement a recurring audible alarm for dangerous targets using `MediaPlayer` and `RingtoneManager.TYPE_ALARM`.

#### [MODIFY] [AisTrackerPlugin.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/aistracker/AisTrackerPlugin.java)
- Move CPA warning assessment from the `Drawable` to `AisDataManager` (AisRepository).
- Implement Class A vs Class B lost target timeout logic:
    - Lost State (slashed icon): Class A > 4m, Class B > 4m (using existing setting for now, but distinguishing logic).
    - Pruning: Class A > 7m, Class B > 18m (as per standards).
- Integrate `AisAudioAlertManager` into the data processing flow.

### Map Layer & UI

#### [MODIFY] [AisObjectDrawable.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/aistracker/AisObjectDrawable.java)
- Remove CPA calculation from `checkCpaWarning` (read from the object's pre-calculated state instead).
- Fix COG predictor vectors to be time-based (e.g., 10-minute predictor vectors) instead of discrete speed brackets.

#### [MODIFY] [AisObjectMenuController.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/aistracker/AisObjectMenuController.java)
- Fix the TCPA minutes calculation bug: `minutes = (cpaTime - hours) * 60.0`.
- Rename "Distance" to "Range" for nautical standard consistency.

## Verification Plan

### Automated Tests
- Add unit tests in `AisTrackerMathTest` for CPA/TCPA math precision (parallel vs diverging, high latitude scaling).

### Manual Verification
- Use `AisSimulationProvider` to simulate busy shipping lanes and verify rendering efficiency.
- Simulate a collision course and verify:
  - Visual alert (Red icon).
  - High-contrast visual alert on map.
  - Recurring audible alarm.
- Verify that stationary targets and anchored vessels do not trigger alerts.
