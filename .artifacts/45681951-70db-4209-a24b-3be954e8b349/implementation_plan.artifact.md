# Implementation Plan - Fix Nautical Plugin Errors and Warnings

Fix compilation errors and lint warnings in the Nautical plugin files to improve code quality and reliability.

## User Review Required

> [!IMPORTANT]
> The primary error is an unresolved reference `clearCache()` in `NauticalPlugin.kt`. I will fix this by adding a `close()` method to `S57SpatialIndex` and updating the caller. Other fixes are primarily lint-related (unused properties, clarifying parentheses, Kotlin idioms).

## Proposed Changes

### [Nautical Plugin Core]
#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Fix unresolved reference `clearCache()` by changing it to `close()`.

#### [MODIFY] [S57SpatialIndex.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/S57SpatialIndex.kt)
- Add `close()` method that calls `sqliteHelper.close()`.

### [Nautical Engine]
#### [MODIFY] [MarineState.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/MarineState.kt)
- Remove unused `autopilotMode`.
- Add missing trailing comma.

#### [MODIFY] [SignalKDataBroker.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKDataBroker.kt)
- Remove explicit type arguments that can be inferred.
- Convert foldable `if-then` to idiomatic Kotlin.
- Add clarifying parentheses to expressions.

#### [MODIFY] [SailingWorkflowEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SailingWorkflowEngine.kt)
- Remove unused imports and properties.
- Convert legacy `Long` delay to `Duration`.
- Add clarifying parentheses and missing line breaks.

### [Nautical UI]
#### [MODIFY] [RudderView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/RudderView.kt)
- Use Kotlin `abs()` instead of `Math.abs()`.
- Add clarifying parentheses to range expressions.

#### [MODIFY] [HeadingArcView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/HeadingArcView.kt)
- Use `Canvas.withRotation` KTX extension.
- Add clarifying parentheses and missing trailing commas.

#### [MODIFY] [HeadingErrorLinearView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/HeadingErrorLinearView.kt)
- Add clarifying parentheses to range expressions.

### [Nautical DI & Network]
#### [MODIFY] [SailingDependencyContainer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/di/SailingDependencyContainer.kt)
- Remove unused parameter `app`.

#### [MODIFY] [SignalKRestService.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/network/SignalKRestService.kt)
- Remove unused function `getSelfIdentity`.

## Verification Plan

### Automated Tests
- Run `analyze_file` on each modified file to ensure warnings are resolved.
- Run `./gradlew :OsmAnd:compileDebugKotlin` (if feasible in this environment) to ensure no compilation errors remain.

### Manual Verification
- N/A
