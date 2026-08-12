# Implementation Plan - Unused Code Cleanup in SignalKEngine

This plan aims to clean up redundant and unused code in `SignalKEngine.kt` following the introduction of the generic `getHistory(path: String)` method and the recent multi-instance refactoring.

## User Review Required

> [!WARNING]
> - **Breaking Change for Subclasses/Plugins**: I am removing ~40 hardcoded history accessor methods (e.g., `getDepthHistory()`). Any external code relying on these will need to switch to `getHistory(SignalKPaths.PATH)`.

## Proposed Changes

### [Frontend/Logic] Migrate remaining history calls

#### [MODIFY] [AutopilotController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotController.kt)
- Replace `engine.getRollHistory()` with `engine.getHistory("${SignalKPaths.NAV_ATTITUDE}.roll")`.
- Replace `engine.getPitchHistory()` with `engine.getHistory("${SignalKPaths.NAV_ATTITUDE}.pitch")`.

#### [MODIFY] [SailingLaylinesMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/laylines/ui/SailingLaylinesMapLayer.kt)
- Replace `engine.getWindDirectionHistory()` with `engine.getHistory(SignalKPaths.ENV_WIND_DIRECTION_TRUE)`.

#### [MODIFY] [WindTrendHudHeader.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/WindTrendHudHeader.kt)
- Replace `engine.getWindDirectionHistory()` with `engine.getHistory(SignalKPaths.ENV_WIND_DIRECTION_TRUE)`.

#### [MODIFY] [NauticalTelemetryGridBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalTelemetryGridBottomSheet.kt)
- Replace `engine?.getDepthKeelHistory()` with `engine?.getHistory(SignalKPaths.ENV_DEPTH_BELOW_KEEL)`.

### [Backend] Signal K Engine Cleanup

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Delete all hardcoded history accessor methods (from `getDepthHistory` to `getHumidityHistory`).
- [Optional] Perform a final pass for truly orphaned private methods that were missed in previous refactors.

## Verification Plan

### Automated Tests
- `SignalKUnitConverterTest` (Smoke test).

### Manual Verification
1. Verify that **Autopilot hydraulic/motor feedback** still works (uses roll/pitch history for stabilization logic).
2. Verify that **Laylines** and **Wind Trend HUD** still display historical wind trends correctly.
3. Verify that the **Telemetry Grid** shows depth history correctly.
4. Build check to ensure no other nautical module is using the deleted methods.
