# Implementation Plan - Tacking Maneuver Bug Fixes & UX Refinement

Address identified bugs and architectural flaws in the Nautical plugin's tacking maneuver functionality, covering both backend logic and frontend UI/UX.

## User Review Required

> [!IMPORTANT]
> **Helm Lock Synchronization**: The proposed change to `AutopilotController` and `NauticalHelmArbitrator` will allow maneuvers to hold a "sticky" lock that cannot be accidentally released by standard command reconciliation. This ensures tactical safety during turns.

> [!WARNING]
> **Coroutine-Based Timers**: Transitioning from `java.util.Timer` to Kotlin Coroutines for "In-Irons" detection will change the thread context of these checks. This is safer for UI updates but requires careful scope management to avoid leaks.

## Proposed Changes

### 1. Maneuver Infrastructure (Backend)

#### [MODIFY] [ManeuverEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/ManeuverEngine.kt)
- Add `ManeuverEngineListener` interface.
- Add `registerListener` and `unregisterListener` methods.
- Invoke listener callbacks in `transitionToCompleted()` and `transitionToAborted()`.

#### [MODIFY] [ManeuverManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/ManeuverManager.kt)
- Implement `ManeuverEngineListener`.
- Register as a listener to the `activeManeuver`.
- Properly clear `activeManeuver` and reset `state` to `IDLE` when the engine signals completion or abortion.
- Ensure Screen Touch Lock is released when the maneuver ends.

#### [MODIFY] [NauticalHelmArbitrator.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/NauticalHelmArbitrator.kt)
- Add a `force` parameter to `releaseLock` (default `false`).
- Prevent lock release at `PRIORITY_TACTICAL_MANEUVER` if a maneuver is active, unless `force = true`.

#### [MODIFY] [AutopilotController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotController.kt)
- Update `tack()` and `gybe()` to accept a `manageLock: Boolean` parameter.
- If `manageLock` is false, skip lock acquisition and pass `null` to `startReconciliation` to prevent auto-release.

---

### 2. Tacking Maneuver Logic

#### [MODIFY] [TackingManeuver.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/TackingManeuver.kt)
- Replace `java.util.Timer` with a Coroutine `Job` for "In-Irons" detection.
- Ensure existing jobs are cancelled in `transitionToExecuting()` to prevent leaks.
- Call `tack(..., manageLock = false)` to let the maneuver control the helm lock lifetime.
- Smooth the `pushProgress` values by calculating interpolation between tack phases based on AWA.
- Standardize performance reporting to use the state passed in `onStateUpdate`.

---

### 3. UI/UX Refinement (Frontend)

#### [MODIFY] [widget_maneuver_overlay.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/widget_maneuver_overlay.xml)
- Add an `ImageView` for the maneuver icon.
- Replace hardcoded hex colors with theme attributes (e.g., `?attr/nautical_status_green`).

#### [MODIFY] [ManeuverOverlayWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/ManeuverOverlayWidget.kt)
- Update `setupView` to find the new `ImageView`.
- Add `updateIcon()` to set the appropriate icon based on the active maneuver ID.
- Use `ContextCompat.getColor` with theme-aware resource IDs for dynamic styling.
- Improve `statusText` feedback to avoid showing raw enum names.

## Verification Plan

### Automated Tests
- Run `./gradlew :OsmAnd:testDebugUnitTest --tests TackingManeuverTest` (if exists, or create a basic harness to verify state transitions).
- Verify `ManeuverManager` state transitions via unit tests.

### Manual Verification
1. Open OsmAnd with Nautical plugin enabled.
2. Trigger a Tacking maneuver from the context menu or HUD.
3. Verify the overlay appears with the correct boat icon.
4. Verify the progress bar moves smoothly as the boat turns (simulated Signal K data).
5. Verify the overlay disappears automatically once the tack is completed.
6. Verify the screen touch lock is released upon completion.
7. Verify that standard autopilot commands do not break the tactical lock during the tack.
8. Check Night Vision mode to ensure colors adapt correctly.
