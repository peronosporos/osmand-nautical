# Clean up warnings in Nautical Plugin and SignalK Engine

The goal is to resolve remaining warnings in the core Nautical Plugin files. These warnings include redundant fully qualified names (FQNs), wildcard imports, and visibility issues.

## Proposed Changes

### [Component Name] SignalK Engine

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Import `SignalKCourse` and remove its redundant FQN.
- Import `SignalKRestService` and remove its redundant FQN.
- Import `LatLon` and remove its redundant FQN.
- Import `LaylineData` and remove its redundant FQN.
- Replace redundant FQN for `TemporalUtils` with imports where appropriate.
- Tighten visibility of some internal properties if possible.
- Use `import kotlinx.coroutines.channels.Channel` instead of FQN on line 66.

#### [MODIFY] [SignalKDataBroker.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKDataBroker.kt)
- Replace wildcard imports with explicit imports.
- Import `KMapUtils` and remove its redundant FQN.

#### [MODIFY] [SignalKControlManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKControlManager.kt)
- Replace `val log` with `private val log` if it's not already.

#### [MODIFY] [SignalKResourceManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKResourceManager.kt)
- Replace wildcard imports with explicit imports.
- Replace redundant FQN for `TemporalUtils` with imports.

### [Component Name] Nautical Plugin

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Replace wildcard imports with explicit imports.
- Import `CompassMode` and remove its redundant FQN.
- Import `AlertDialog` and remove its redundant FQN.
- Import `VhfHistoryBottomSheet` and remove its redundant FQN.
- Replace `pluginScope!!` with a safe call or local variable if it can be null.

#### [MODIFY] [NauticalSettingsFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalSettingsFragment.kt)
- Fix string concatenation warnings by using template strings or `getString` arguments.

## Verification Plan

### Automated Tests
- Run `analyze_file` on the modified files to verify that no new errors were introduced and some warnings were resolved (if it doesn't timeout).

### Manual Verification
- None required as these are code style and warning fixes.
