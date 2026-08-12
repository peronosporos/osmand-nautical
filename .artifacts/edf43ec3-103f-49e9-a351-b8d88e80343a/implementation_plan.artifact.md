# Implementation Plan - Fix Memory Leak in NauticalPlugin

The `wearOsManagerRef` static field in `NauticalPlugin` holds a reference to `WearOsNauticalManager`, which in turn holds an Android `Context`. This triggers a memory leak warning. I will move the manager instance to the `NauticalPlugin` class itself.

## Proposed Changes

### [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)

- **Remove `wearOsManagerRef` static field**: Delete the private static property from the companion object.
- **Add `wearOsManager` property**: Add a `val wearOsManager = WearOsNauticalManager(app)` property to the `NauticalPlugin` class.
- **Update `getWearOsManager` static method**: Change it to retrieve the manager from the active `NauticalPlugin` instance if available, otherwise return a new instance using the provided context's application context. This avoids static caching of the context.

## Verification Plan

### Automated Tests
- Run `analyze_file` on `NauticalPlugin.kt` to ensure the memory leak warning is resolved and no new issues are introduced.

### Manual Verification
- Verify the code builds.
- Ensure that functionality using `getWearOsManager` (like `NauticalHudManager`) still works as expected.
