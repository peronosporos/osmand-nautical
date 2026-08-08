# Walkthrough - Fixed SignalKEngine.aisCache Access Error

I have resolved the compilation error where `NauticalAisLayer` was unable to access the `aisCache` property in `SignalKEngine`.

## Changes

### Nautical Plugin

#### [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)

- Changed `aisCache` visibility from `private` to `internal`. This allows `NauticalAisLayer` (which is in the same module but a different package) to access the cache for checking virtual targets, while maintaining encapsulation from external modules.

```diff
-    private val aisCache = ConcurrentHashMap<Int, AisObject>()
+    internal val aisCache = ConcurrentHashMap<Int, AisObject>()
```

## Verification Results

### Automated Tests
- **Static Analysis**: Verified with `analyze_file` on [NauticalAisLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/NauticalAisLayer.kt). The access error previously reported at line 112 is now resolved.
