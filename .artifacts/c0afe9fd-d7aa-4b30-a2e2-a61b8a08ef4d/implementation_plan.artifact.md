# Nautical Plugin Audit and Consolidation Plan

The goal is to consolidate the Nautical-related logic into a single, clean, and modern Kotlin-based plugin, while maintaining compatibility with the core OsmAnd architecture to facilitate future merging.

## User Review Required

> [!IMPORTANT]
> **Consolidation**: I propose merging `NauticalMapsPlugin.java` into `NauticalPlugin.kt`. This will result in a single "Nautical" plugin in the UI instead of two, providing a much better user experience.
>
> **Night Vision**: I plan to make "Night Vision" (the red filter) a manual toggle that doesn't force the global `DAYNIGHT_MODE`. Instead, it will apply its filter on top of the current mode.

## Proposed Changes

### 1. Plugin Consolidation & Modernization

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Integrate all features from `NauticalMapsPlugin.java` (added app modes, rendering properties, router names).
- Implement `registerLayerContextMenuActions` to handle depth contours, similar to `NauticalMapsPlugin`.
- Add proper `getName()` and `getDescription()` that covers both chart and instrument features.
- Fix indentation and minor warnings (missing commas, parentheses).

#### [DELETE] [NauticalMapsPlugin.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/openseamaps/NauticalMapsPlugin.java)
- Logic migrated to `NauticalPlugin.kt`.

#### [MODIFY] [PluginsHelper.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/PluginsHelper.java)
- Remove registration of `NauticalMapsPlugin`.
- Move `NauticalPlugin` registration next to other marine-related plugins (like `AisTrackerPlugin`).

### 2. Engine & Performance Optimization

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Move `saveBuffersToDisk` and `loadBuffersFromDisk` to background threads to avoid blocking the UI thread during plugin state changes.
- Switch from `ObjectInputStream/OutputStream` to a more resilient format (e.g., CSV or simple JSON) for history buffers.

#### [MODIFY] [AutopilotController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotController.kt)
- Fix indentation in `executePut` callbacks.
- Improve error reporting for missing server configuration.

#### [MODIFY] [OkHttpSignalKConnection.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/OkHttpSignalKConnection.kt)
- Fix indentation in `WebSocketListener` callbacks.

### 3. UI/UX & Integration

#### [MODIFY] [NauticalSettingsFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalSettingsFragment.kt)
- Add controls for depth contours and other rendering properties that were previously in `NauticalMapsPlugin`.

#### [MODIFY] [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml)
- Consolidate strings to avoid duplication (e.g., "Nautical map view" vs "Nautical Marine Controls").

## Verification Plan

### Automated Tests
- I will run `analyze_file` on all modified files to ensure no new errors are introduced.
- I will check the build configuration to ensure no classpath issues remain.

### Manual Verification
- Verify that only one Nautical plugin appears in the plugin list.
- Verify that both map features (depth contours) and instrument features (Signal K) are available under the single plugin.
- Verify that "Night Vision" works as expected without disrupting the global day/night settings unnecessarily.
