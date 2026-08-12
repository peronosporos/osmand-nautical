# Implementation Plan - Nautical Pilot Functionality Overhaul

This plan addresses 20 identified bugs, architectural issues, and UI/UX improvements in the Nautical plugin's pilot (autopilot) functionality.

## User Review Required

> [!IMPORTANT]
> **Autopilot Consolidation**: We will merge `AutopilotManager` into `AutopilotController`. `AutopilotController` is more robust and handles helm arbitration. This will simplify the codebase and prevent state desynchronization.

> [!WARNING]
> **Module Refactoring**: Item 17 requests moving Pilot UI components to the `:plugins:Osmand-Nautical` module. Currently, the Nautical plugin's core logic resides in the main `:OsmAnd` module. We will move the UI components to `net.osmand.plus.plugins.nautical.ui.widgets` to improve modularity within the plugin's package. If moving to a separate module is strictly required, we will need to convert `:plugins:Osmand-Nautical` from an `application` to a `library`.

## Proposed Changes

### 1. Backend Consolidation & Safety

#### [MODIFY] [AutopilotController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotController.kt)
- Integrate `reconcileState()` logic from `AutopilotManager`.
- Enforce secure connections (HTTPS/Bearer token) for all state-mutating commands.
- Implement a command queue with retry logic for high-priority commands (e.g., EMERGENCY STOP).

#### [DELETE] [AutopilotManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotManager.kt)
- Remove redundant implementation after merging into `AutopilotController`.

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Remove `autopilotManager` initialization and usages.

---

### 2. Logic & Synchronization Fixes

#### [MODIFY] [AutopilotController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotController.kt)
- Fix helm lock release: ensure `releaseLock` is called even when `reconciliationJob` is cancelled due to successful confirmation.
- Implement optimistic UI updates with a "rollback" mechanism in case of server rejection or timeout.

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Fix Route Step Listener: Ensure listeners are triggered by server-side `nextPoint` changes when `hasCourseAutoAdvance` is true.
- Consolidate Dead Reckoning logic to prevent conflicts with internal GPS updates.

#### [MODIFY] [SignalKDataBroker.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKDataBroker.kt)
- Add debounce/thresholding to "Shadow Drive" detection to avoid false positives in heavy seas.
- Make STW reliability delay configurable or more adaptive to vessel speed.

---

### 3. UI/UX Improvements

#### [MODIFY] [NauticalPilotBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotBottomSheet.kt)
- Improve voice announcement debouncing (accumulate heading nudges before speaking).
- Implement a state machine for "Tack/Gybe" labels to prevent flickering during maneuvers.
- Ensure "Course Lock" (Touch Guard) visual state is perfectly synchronized with actual interaction blocking.
- Add an "Abort Pattern" confirmation and avoid dismissing the sheet when starting a pattern.

#### [MODIFY] [NauticalHudManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalHudManager.kt)
- Implement a banner queue to prevent overlapping messages from being lost.

#### [MODIFY] [NauticalPilotWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotWidget.kt)
- Improve visibility of "stale" or "disconnected" state (e.g., color shift or blinking badge).

---

### 4. Architectural & Structural Cleanup

#### [NEW] [RudderLimitSetting](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/settings/backend/OsmandSettings.java)
- Replace hardcoded 35-degree rudder limit with a configurable setting.

#### [MODIFY] [Move UI Files](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/widgets)
- Move `NauticalPilotBottomSheet` and `NauticalPilotWidget` to the `net.osmand.plus.plugins.nautical.ui.widgets` package.
- Standardize bottom sheet base classes for the plugin (ensure they all inherit from `BaseOsmAndFragment` specialized for Nautical).

## Verification Plan

### Automated Tests
- Create unit tests for `AutopilotController` command reconciliation and helm arbitration.
- Create unit tests for `SignalKDataBroker` shadow drive and STW reliability logic.

### Manual Verification
- Deploy to an emulator or device and simulate SignalK data.
- Verify helm lock behavior during maneuvers.
- Verify voice announcement debouncing by rapidly tapping nudge buttons.
- Verify banner queueing by triggering multiple notifications simultaneously.
