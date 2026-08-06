# Walkthrough - Fixing Nautical Plugin Compilation Errors

I have fixed the compilation errors in the `:OsmAnd` module specifically related to the nautical plugin.

## Changes Made

### Nautical Engine
I fixed the `PatternSteeringEngine.generateExpandingSquare` function which had scrambled parameter types and invalid logic.
- **Before:** Parameters like `initialHeading` were incorrectly typed as `Boolean` while being used as `Double`. An unused `app: Double` parameter was causing `getString` to fail.
- **After:** Corrected parameter types (`Double` for headings and spacing, `Int` for iterations) and removed the invalid `app` parameter.

### NMEA Connection
Fixed an argument mismatch in `UsbConnectionReceiver.kt`.
- **Before:** `SailingDependencyContainer.getNmeaMultiplexer(scope)` was called with only a `CoroutineScope`, but it requires an `OsmandApplication` as the first argument.
- **After:** Initialized `osmandApp` from context and passed it to the `getNmeaMultiplexer` call.

## Verification Results

### Automated Tests
- Ran `analyze_file` on both modified files.
- `PatternSteeringEngine.kt`: All 3 reported compilation errors (argument type mismatch, unresolved operator '+') are now resolved. Only minor stylistic warnings remain.
- `UsbConnectionReceiver.kt`: The reported argument type mismatch is resolved. The file now passes static analysis without errors.

render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/PatternSteeringEngine.kt)
render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/connection/UsbConnectionReceiver.kt)
