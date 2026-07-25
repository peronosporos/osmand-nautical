# Walkthrough - Targeted Null-Safety and Lifecycle Guard Check

I have completed the targeted null-safety and lifecycle guard check in the `plugins/nautical` component. While the existing `lateinit` properties were found to be well-guarded, I identified and fixed several memory leaks and activity-lifecycle issues in `SailingIntegrationPlugin.kt`.

## Changes Made

### Memory Leak and Lifecycle Fixes

#### [SailingIntegrationPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/plugin/SailingIntegrationPlugin.kt)
- **Implemented `mapActivityDestroy`**: This method now correctly unregisters layers and nulls out activity-specific references (`layerController`, `nauticalHudContainer`, and HUD header views) when the `MapActivity` is destroyed. This prevents memory leaks.
- **Improved `getOrCreateNauticalHud`**: Added a check to ensure the HUD container is associated with the current activity. If the activity context has changed (e.g., due to a configuration change or recreation), the old container is removed and a new one is created for the fresh activity.

### Null-Safety Verification

#### [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Verified that the `lateinit var connection` is strictly guarded by `::connection.isInitialized` checks in all lifecycle methods (`mapActivityResume`, `mapActivityPause`, etc.) and background tasks.
- Confirmed that broadcast receiver unregistration for `screenStateReceiver` is safely wrapped in `try-catch` blocks.

## Verification Results

### Static Analysis
- Performed `analyze_file` on modified files. Only minor lint warnings (parentheses, line breaks, deprecations) were found, with no critical syntax errors or null-safety violations introduced.

### Code Inspection
- Confirmed all unregistering logic in `shutdownResources` and `disable` methods correctly handles nullability of engines, brokers, and listeners.
