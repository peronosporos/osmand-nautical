# Fix Compilation Errors in Nautical Plugin

The project is failing to compile due to missing or renamed properties in `MarineState` and related classes in the Nautical plugin.

## Proposed Changes

### [Nautical Engine]

#### [MODIFY] [MarineState.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/MarineState.kt)
- Rename `autopilotTargetHeading` to `autopilotHeadingSet` in `MarineState` data class.
- Rename `location` to `position` in `ForwardHazard` data class.

## Verification Plan

### Automated Tests
- Run the build command again: `./gradlew :OsmAnd:compileAndroidFullLegacyArm64DebugKotlin`

### Manual Verification
- None required as these are compilation fixes.
