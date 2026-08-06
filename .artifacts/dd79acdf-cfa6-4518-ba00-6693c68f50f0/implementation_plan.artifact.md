# Implementation Plan - Combinatorial State Collisions & Mutex Arbitration

Fix critical state machine conflicts, missing arbitration locks, and propulsion-blind algorithms in the Nautical plugin.

## Proposed Changes

### [Engine]

#### [NEW] [HelmLockedException.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/HelmLockedException.kt)
- Define a custom exception for helm arbitration failures.

#### [NEW] [NauticalHelmArbitrator.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/NauticalHelmArbitrator.kt)
- Singleton arbitrator governing `AutopilotController` commands.
- Implements priority levels: `EMERGENCY_MOB` (1) to `STANDBY` (5).
- Rejects lower-priority commands with `HelmLockedException`.
- Triggers UI Toast and Audio alerts on rejection.

#### [NEW] [PropulsionContextManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/PropulsionContextManager.kt)
- Singleton monitoring `MarineState` for propulsion and navigation states.
- Provides status for engine running (RPM > 100 or state "started").

#### [MODIFY] [AutopilotController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotController.kt)
- Update command methods to consult `NauticalHelmArbitrator`.

#### [MODIFY] [MarineState.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/MarineState.kt)
- Ensure all necessary propulsion fields are present (they seem to be already).

### [UI / Widgets]

#### [MODIFY] [NauticalPilotWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotWidget.kt)
- Disable interactions if helm is locked by a higher-priority maneuver (especially MOB).

#### [MODIFY] [NauticalPilotBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotBottomSheet.kt)
- Disable controls and show "Helm Locked" status if arbitrator priority is high.

### [Maneuvers & Laylines]

#### [MODIFY] [ManOverboardManeuver.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/ManOverboardManeuver.kt)
- Set `EMERGENCY_MOB` priority in arbitrator on activation.
- Use `PropulsionContextManager` to determine recovery mode.
- Prompt user with a modal if engine state is unknown.

#### [MODIFY] [LaylineMathEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/laylines/engine/LaylineMathEngine.kt)
- Update to check `PropulsionContextManager` (or pass propulsion state as param).

#### [MODIFY] [SailingLaylinesMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/laylines/ui/SailingLaylinesMapLayer.kt)
- Suppress rendering if engine is running.

### [Routing & Hazards]

#### [MODIFY] [SafetyCorridorChecker.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/engine/SafetyCorridorChecker.kt)
- Add `isPointSafe(lat: Double, lon: Double): Boolean` for isochrone node validation.

#### [MODIFY] [IsochroneRoutingEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/routing/algorithm/IsochroneRoutingEngine.kt)
- Integrate `SafetyCorridorChecker.isPointSafe` into node expansion loop.

## Verification Plan

### Automated Tests
- N/A (Focus on manual verification via device/emulator if possible, or build verification).

### Manual Verification
1. Activate MOB and try to change autopilot mode via widget - should be rejected.
2. Start engine (RPM > 100) and verify sailing laylines disappear.
3. Calculate a weather route near a reef and verify it avoids depths shallower than draft.
4. Activate MOB with unknown engine state and verify the recovery mode prompt appears.
