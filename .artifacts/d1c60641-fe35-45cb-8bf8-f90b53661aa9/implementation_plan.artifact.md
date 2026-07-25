# Fix App Startup Hang and Crash in Nautical Plugin

## Problem Statement
When starting the OsmAnd Nautical app, it takes too long, hangs, and eventually crashes with:
`java.lang.RuntimeException: Unable to resume activity {...MapActivity}: kotlin.UninitializedPropertyAccessException: lateinit property connection has not been initialized`
This happens because `connection` (`OkHttpSignalKConnection`) is a `lateinit var` that is initialized in `initConnection()`, but if `mapActivityResume()` is called or the app flow runs before `setEnabled()` or `startEngine()` properly initializes it (or if it's accessed when the plugin/connection isn't fully ready), it throws an uninitialized property access exception and crashes the app. Furthermore, the native library warning in logs (`libosmand.so not found` or library loading failure) contributes to startup delays/issues if not properly handled or if fallback modes are triggered.

## Proposed Changes

### Nautical Plugin (`NauticalPlugin.kt`)
- Make `connection` safely initialized or nullable (`var connection: OkHttpSignalKConnection? = null`) or check `::connection.isInitialized` safely in `mapActivityResume()` before calling `connection.isConnected()`.
- Ensure all plugin entry points safely guard against uninitialized properties.

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)

## Verification Plan

### Automated Tests
- Check code compilation.

### Manual Verification
- Launch the app via ADB and verify that it starts without crashing on `MapActivity` resume.
