# Implementation Plan: Connect Unused Signal K Paths

Fix warnings for unused properties in `SignalKPaths.kt` by implementing their handling in `SignalKDeltaParser.kt` and mapping them to the appropriate fields in `MarineState`.

## User Review Required

> [!IMPORTANT]
> This change will increase the amount of data processed from Signal K. It might impact performance slightly on very busy servers, though the overhead of parsing a few more JSON fields is usually negligible.

## Proposed Changes

### [Component Name]

#### [MODIFY] [SignalKDeltaParser.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKDeltaParser.kt)

- Update `parseNavigationValue` to handle:
    - `NAV_XTE_RHUMB` and `NAV_XTE_GC` (map to `crossTrackError` if currently null or as fallbacks).
    - `NAV_FLAGS` (map to `flags` list).
    - `NAV_ANCHOR_RODE_DEPLOYED` (map to `rodeDeployed`).
- Update `parseEnvironmentValue` to handle:
    - `ENV_MOON_PHASE` (map to `moonPhase`).
    - `ENV_SUNLIGHT_MODE` (map to `sunlightMode`).
- Update `parseAutopilotValue` to handle:
    - `STEERING_AUTOPILOT_SEA_STATE` (map to `seaState`).
- Update `parseSystemValue` to handle:
    - `RIGGING_LOAD_PREFIX` (map to `riggingLoads` map).
    - `ELECTRICAL_AC_PREFIX` (handle AC electrical data).
- Update `parseOtherValue` to handle:
    - `DESIGN_*` paths (map to `vesselLength`, `vesselBeam`, `airDraft`, `displacement`).
    - `COMMUNICATION_CREW_NAMES` (map to `crewNames`).
    - `MEDIA_*` paths (map to `mediaInfo` object).

#### [MODIFY] [SignalKPaths.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKPaths.kt)

- Keep the constants as they will now be used.
- Ensure all constants are correctly defined and grouped.

## Verification Plan

### Automated Tests
- Since I cannot run local Gradle commands, I will rely on CI.
- I will check the `SignalKDeltaParserTest.kt` if it exists and add test cases if possible.

### Manual Verification
- Deploy to a device (remote CI APK) and verify that the new data fields are populated when the Signal K server sends them.
- I'll check the logs for any "Unknown path" or parsing errors.
