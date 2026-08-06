# Fix Compilation Errors in Nautical Plugin

This plan addresses three compilation errors reported in the nautical plugin's engine and NMEA connection modules.

## User Review Required

> [!IMPORTANT]
> The `PatternSteeringEngine.generateExpandingSquare` function had its parameters completely scrambled (types and names mismatched). I am restoring them to match the actual usage in the project. The `app` parameter (previously declared as `Double` and unused except for an invalid `getString` call) will be removed as it's not provided by callers and not needed for the core logic.

## Proposed Changes

### Nautical Engine

#### [MODIFY] [PatternSteeringEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/PatternSteeringEngine.kt)
- Fix the signature of `generateExpandingSquare`:
    - Remove the leading `app: Double` parameter.
    - Change `spacingNm` from `Int` to `Double`.
    - Change `iterations` from `Double` to `Int`.
    - Change `initialHeading` from `Boolean` to `Double`.
- Remove the unused `name` variable and the invalid `app.getString()` call.
- Ensure the logic uses the corrected parameter types (e.g., `iterations` as `Int` in the `for` loop).

### NMEA Connection

#### [MODIFY] [UsbConnectionReceiver.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/connection/UsbConnectionReceiver.kt)
- Fix the call to `SailingDependencyContainer.getNmeaMultiplexer()`:
    - Pass `osmandApp` (OsmandApplication) as the first argument, as required by the dependency container.
    - Reorder the code to ensure `osmandApp` is initialized before it's used.

## Verification Plan

### Automated Tests
- I will attempt to trigger a build of the `:OsmAnd` module (or specifically the nautical plugin parts) if possible, but per instructions I should NOT run Gradle build tasks myself for verification.
- I will use `analyze_file` to verify the fixed files are syntactically correct and don't have obvious type mismatches.

### Manual Verification
- Review the changed files to ensure they align with the expected architecture and usages found during research.
