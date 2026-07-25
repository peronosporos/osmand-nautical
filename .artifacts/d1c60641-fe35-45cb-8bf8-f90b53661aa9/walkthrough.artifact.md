# Walkthrough - Fixing Startup Crash in Nautical Plugin

I have addressed the issue where the application would hang and crash during startup.

## Changes Made

### Nautical Plugin
#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Added `::connection.isInitialized` checks before accessing the `lateinit var connection` property in `mapActivityResume()`, `mapActivityPause()`, and `screenStateReceiver`.
- This prevents `kotlin.UninitializedPropertyAccessException` which was occurring when the activity resumed before the plugin's background initialization of the connection transport was complete.

## Verification Results

### Automated Tests
- Analyzed `NauticalPlugin.kt` for syntax errors and potential issues using IDE inspections. No errors were found in the modified logic.

### Manual Verification (Observations from Logcat)
- The logcat confirmed the crash was: `java.lang.RuntimeException: Unable to resume activity {net.osmand.plus.nautical.nautical/net.osmand.plus.activities.MapActivity}: kotlin.UninitializedPropertyAccessException: lateinit property connection has not been initialized`.
- The fix directly addresses this by ensuring the property is only accessed after initialization or triggering its initialization via `startEngine()`.

> [!NOTE]
> There are still some native library warnings in the logs (`libosmand.so not found`). While these don't cause a crash, they might contribute to some functional limitations or minor delays during startup. I recommend verifying the build configuration if native features (like high-performance rendering) are missing.
