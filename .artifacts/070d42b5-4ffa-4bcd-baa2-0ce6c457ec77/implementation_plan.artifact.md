# Fix Errors and Warnings in Nautical Plugin Components

This plan outlines the fixes for unresolved references, logic errors, and potential null pointer exceptions in `NauticalPlugin`, `SignalKEngine`, `MobViewModel`, `ManOverboardManeuver`, and `SignalKUnitConverter`.

## Proposed Changes

### [Nautical]

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Add `import net.osmand.shared.aistracker.AisObject`.
- Fix unresolved `target` reference in `engine?.registerAisListener` by ensuring `AisObject` is correctly imported and the lambda parameter type is recognized.

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Add `import net.osmand.shared.aistracker.AisObject`.
- Fix `updateAisTarget` to correctly use `AisObject` constructors.
- Fix unresolved references to `mmsi` and `position` on `aisTarget` by ensuring its type is correctly inferred as `AisObject`.
- Ensure all calls to `AisObject` constructors match the signatures available in `OsmAnd-shared`.

#### [MODIFY] [MobViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/mob/viewmodel/MobViewModel.kt)
- Fix potential `null` issue with `trueWindAngle` when calling `Math.toDegrees`. Use `?.let` or default value.
- Ensure `isSearching` is updated correctly from the engine.

#### [MODIFY] [ManOverboardManeuver.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/ManOverboardManeuver.kt)
- Fix bugs in `calculateDistance` and `calculateBearing` where `lat2` was used instead of `lon2` for longitude conversion.
- Fix potential `null` issue with `trueWindAngle` in `isUpwind`.

#### [MODIFY] [SignalKUnitConverter.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKUnitConverter.kt)
- Ensure all values passed to `OsmAndFormatter` are correctly cast to `Float`.
- Double-check all unit conversions and string resource usages.

## Verification Plan

### Automated Tests
- Since I cannot run Gradle builds directly to verify compilation, I will rely on `analyze_file` to check for remaining errors after applying changes.

### Manual Verification
- N/A (Standard for this task)
