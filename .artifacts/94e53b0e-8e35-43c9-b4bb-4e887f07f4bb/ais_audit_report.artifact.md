# AIS Audit Report - OsmAnd Nautical Plugin

## 1. CPA/TCPA Spherical Trigonometry Precision

### [BUG] Inaccurate Planar Projection Scaling (Latitude Drift)
**File:** [AisTrackerMath.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd-shared/src/commonMain/kotlin/net/osmand/shared/aistracker/AisTrackerMath.kt#L106-L113) / `getLonCorrection`
**Bug:** The `correctionFactor` is cached globally for 60 minutes. In a multi-target scenario where vessels are at different latitudes, the planar approximation becomes inaccurate because it uses a single longitude scaling factor for all CPA calculations.
**Fix:** Remove global caching of `correctionFactor`. Calculate the correction factor on-the-fly based on the average latitude of the two vessels involved in the CPA calculation.

### [BUG] Incorrect Stationary Filtering Threshold
**File:** [AisObjectConstants.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd-shared/src/commonMain/kotlin/net/osmand/shared/aistracker/AisObjectConstants.kt#L14) / `SPEED_CONSIDERED_IN_REST`
**Bug:** Threshold for filtering stationary targets is set to `0.4` knots. Standard marine safety protocols (and user requirements) specify `0.5` knots to effectively filter jitter in GPS SOG for moored/anchored vessels.
**Fix:** Update `SPEED_CONSIDERED_IN_REST` to `0.5`.

### [BUG] TCPA Formatting Mathematical Error
**File:** [AisObjectMenuController.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/aistracker/AisObjectMenuController.java#L64) / `addCpaInfo`
**Bug:** The calculation `minutes = (cpaTime % 1 - hours) * 60.0` is incorrect when `hours > 0`. It results in negative minutes.
**Fix:** Change to `minutes = (cpaTime - hours) * 60.0`.

---

## 2. Guard Zone Interlocks & Alarm Escalation

### [BUG] CPA Warning Decoupled from Background Logic
**File:** [AisObjectDrawable.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/aistracker/AisObjectDrawable.java#L164) / `checkCpaWarning`
**Bug:** Collision risk assessment is performed within the `Drawable` class during the map rendering cycle. Targets off-screen or not being drawn will not trigger collision warnings or alarms.
**Fix:** Move `checkCpaWarning` logic to `AisDataManager` (AisRepository) and run it periodically for all active targets.

### [BUG] Missing Audible Alarm Implementation
**File:** [AisTrackerPlugin.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/aistracker/AisTrackerPlugin.java)
**Bug:** There is no logic to trigger a recurring audible alarm for dangerous targets.
**Fix:** Create `AisAudioAlertManager` (similar to `MobAudioAlertManager`) and invoke it when `checkCpaWarning` returns true for any target.

### [BUG] Fixed Timeout for Lost Targets (Class A/B)
**File:** [AisTrackerPlugin.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/aistracker/AisTrackerPlugin.java#L78-L82)
**Bug:** Uses a fixed `AIS_SHIP_LOST_TIMEOUT` (4m) for all vessels. Standards require longer timeouts for Class B vessels (e.g., 18 minutes).
**Fix:** Implement variable timeout logic based on the AIS message type (Class A vs Class B).

---

## 3. AIS Map Layer & Target Selection Bottom Sheet

### [BUG] Discrete Speed Vectors instead of Predictor Vectors
**File:** [AisObjectDrawable.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/aistracker/AisObjectDrawable.java#L143) / `getMovement`
**Bug:** The COG vector length is determined by discrete speed brackets rather than a projected path (e.g., 10-minute predictor).
**Fix:** Calculate vector length as `SOG * predictor_time` to show where the vessel will be in X minutes.

### [BUG] Missing Range/Bearing Label Consistency
**File:** [AisObjectMenuController.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/aistracker/AisObjectMenuController.java#L133) / `addPlainMenuItems`
**Bug:** The data sheet uses "Distance" instead of "Range", which is the nautical standard.
**Fix:** Rename "Distance" menu item to "Range".
