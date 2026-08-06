# Implementation Plan - Comprehensive Nautical Plugin Optimization (Phase 2: Completion)

Complete the implementation by solving all remaining warnings, integrating unused functionalities, and ensuring a fully reactive tuning system.

## User Review Required

> [!IMPORTANT]
> This phase ensures that the technical parameters (EMA alpha, timeouts) actually impact the system live and that the new Signal K v2 APIs are actively used for route synchronization.

## Proposed Changes

### 1. Robust Engine & Reactive Tuning
Ensure configuration changes in the UI are immediately propagated to the processing engine.

#### [MODIFY] [SignalKDataBroker.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKDataBroker.kt)
- Fix legacy `delay(1000)` and unused lambda parameters in `stalenessJob`.
- Enhance `updateTuning()` to refresh the internal watchdog timeout and speed thresholds from settings.

#### [MODIFY] [NauticalAdvancedSettingsFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/NauticalAdvancedSettingsFragment.kt)
- Implement `onPreferenceChange` to trigger `NauticalPlugin.engine?.dataBroker?.updateTuning()` on every edit.
- Ensure all technical categories are correctly linked to their backend counterparts.

---

### 2. Signal K v2 Course Synchronization
Transition from passive route observation to active route "Push" capabilities.

#### [MODIFY] [SignalKServerRoutesFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/SignalKServerRoutesFragment.kt)
- Update `pushToAutopilot` logic:
    - If server has v2 `hasAutopilot` capability, use `SignalKRestService.updateCourse()` with a full `SignalKCourse` object.
    - Fallback to legacy `setAutopilotMode("track")` for v1 servers.

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Integrate `getCourse()` result into the main `MarineState` update flow.
- Clean up unused method warnings and ensure `processCourseObject` handles arrival radius.

---

### 3. Safety State & HUD Coordination
Flesh out the arbitrator to manage the visual and audible "Emergency Stack".

#### [MODIFY] [SafetyStateArbitrator.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SafetyStateArbitrator.kt)
- Implement `arbitrateHud` to hide non-emergency widgets (Environment, Media) when `isMobActive` is true.
- Add logic to suppress "XTE" audio alerts if an "Anchor Drift" or "Collision" alarm is already firing.

---

### 4. Cleanup & Integrity
Final sweep of warnings and missing links.

#### [MODIFY] [MarineTextWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/MarineTextWidget.kt)
- Ensure `Rigging Loads` and `AC Systems` widgets are correctly mapped to their multi-instance sources in `MarineState`.
- Fix destructuring warnings in `updateSimpleWidgetInfo`.

#### [MODIFY] [SignalKModels.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/network/SignalKModels.kt)
- Remove unused `PATH_HEADING` and fix trailing comma issues.

## Verification Plan

### Automated Tests
- `SignalKDataBrokerTest`: Verify that changing settings updates the EMA smoothing values in the broker.
- `CourseApiTest`: Mock a v2 server and verify the `updateCourse` REST call structure.

### Manual Verification
- Change a Heading EMA alpha in Settings and verify the Heading widget becomes more/less "twitchy" instantly.
- Start a MOB and verify that the "Media Player" HUD header (if active) is automatically hidden by the Arbitrator.
