# Walkthrough - Fix Visibility of SignalK managers in SignalKEngine

I have resolved the compilation errors where `resourceManager` and `controlManager` were inaccessible from UI components because they were marked as `private` in `SignalKEngine`.

## Changes Made

### Nautical Plugin Engine

#### [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)

- Changed the visibility of `controlManager` and `resourceManager` from `private` to public (default).

```diff
-    private val controlManager = SignalKControlManager(app, dataBroker, engineScope)
-    private val resourceManager = SignalKResourceManager(app, engineScope)
+    val controlManager = SignalKControlManager(app, dataBroker, engineScope)
+    val resourceManager = SignalKResourceManager(app, engineScope)
```

## Verification Results

### Automated Tests
- Performed `analyze_file` on [NauticalChecklistFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/NauticalChecklistFragment.kt) and [NauticalMediaPlayerWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/NauticalMediaPlayerWidget.kt).
- Confirmed that the "Cannot access ...: it is private in ..." errors are no longer reported.
- Note: A general Gradle environment issue prevented a full project build, but the specific Kotlin compilation errors related to visibility have been verified as resolved via file analysis.
