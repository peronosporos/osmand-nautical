# Implementation Plan - Anchor System Full Integration

Ensure that anchor-related functionality is seamlessly integrated across maneuvers, HUD, and configuration dialogs, leveraging the newly added Signal K capabilities.

## Proposed Changes

### [Component] Nautical Maneuvers

#### [MODIFY] [AnchoringManeuver.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/AnchoringManeuver.kt)
- If `hasWindlassControl` is true, automatically trigger the windlass **DOWN** action when the maneuver starts executing.
- Monitor `rodeDeployed` (if `hasChainCounter` is true) to stop the windlass once the target `rodeLength` is reached.
- Stop the windlass if the maneuver is completed or aborted.

#### [MODIFY] [WeighingAnchorManeuver.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/WeighingAnchorManeuver.kt)
- If `hasWindlassControl` is true, automatically trigger the windlass **UP** action when the maneuver starts.
- Monitor `rodeDeployed` (if `hasChainCounter` is true) and slow down/stop as it approaches zero.

---

### [Component] Anchor Watch UI

#### [MODIFY] [AnchorWatchDialogFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/anchor/AnchorWatchDialogFragment.kt)
- Observe `rodeDeployed` from `MarineState` and display it in the dialog if `hasChainCounter` is true.
- Add quick-action **UP/DOWN** buttons for the windlass if `hasWindlassControl` is true (respecting the engine running guard).

#### [MODIFY] [AnchorWatchHudView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/anchor/AnchorWatchHudView.kt)
- Ensure it displays both the *set* anchor radius and the *actual* deployed rode for comparison.

---

### [Component] Safety Engine

#### [MODIFY] [AnchorDriftWatchdog.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/AnchorDriftWatchdog.kt)
- Implement an "Anchor Type Consistency" check: if `rodeDeployed` is significantly less than the distance to the anchor drop point, trigger a "Drag Warning" or "Dragging Suspected" alert.

## Verification Plan

### Automated Tests
- Static analysis via `analyze_file`.
- Check if project builds: `./gradlew :OsmAnd:assembleDebug`.

### Manual Verification
- Start "Anchoring" maneuver and verify windlass DOWN is triggered (in logs/simulated state).
- Check `AnchorWatchDialogFragment` while anchor is being weighed to see chain counter updates.
- Verify engine guard prevents windlass operation in the dialog.
