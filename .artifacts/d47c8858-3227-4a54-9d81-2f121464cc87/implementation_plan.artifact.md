# Targeted Null-Safety and Lifecycle Guard Check Report

This plan summarizes the findings of the targeted check in `plugins/nautical` and proposes fixes for identified lifecycle and memory leak issues.

## Findings Summary

### 1. `lateinit` Properties Analysis
- [x] **`NauticalPlugin.kt`**: Only one `lateinit var connection: OkHttpSignalKConnection`. It is properly guarded with `::connection.isInitialized` in all lifecycle callbacks (`mapActivityResume`, `mapActivityPause`, `setEnabled`, `startEngine`, `shutdownResources`) and observers.
- [x] **`SailingIntegrationPlugin.kt`**: No `lateinit` properties found. All members are nullable or initialized at construction.
- [x] **Other Files**: `lateinit` properties in fragments and dialogs (e.g., `MarineRasterManagerFragment.kt`) are standard UI component initializations in `onCreateView` and are accessed safely within the fragment lifecycle.

### 2. Lifecycle & Memory Leak Issues
- [!] **`SailingIntegrationPlugin.kt` Activity Leak**: The plugin holds strong references to HUD views (`mobHeaderView`, `drHeaderView`, `navtexHudView`, `nauticalHudContainer`) and a `layerController` which contains a reference to `MapActivity`. These are not nulled out when the activity is destroyed, causing a memory leak when the map is opened multiple times.
- [!] **HUD Container Reuse Bug**: `getOrCreateNauticalHud` in `SailingIntegrationPlugin.kt` incorrectly reuses `nauticalHudContainer` even if it belongs to a previous (and potentially destroyed) activity.

### 3. Receiver & Listener Safety
- [x] **`NauticalPlugin.kt`**: `screenStateReceiver` unregistration is safely wrapped in `try-catch` blocks.
- [x] **Listeners**: Most listeners are nulled out or removed in `shutdownResources` or `onDisable` logic.

## Proposed Changes

### [Component] `SailingIntegrationPlugin`

#### [MODIFY] [SailingIntegrationPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/plugin/SailingIntegrationPlugin.kt)
- Implement `mapActivityDestroy(activity: MapActivity)` to null out activity-specific resources: `layerController`, `nauticalHudContainer`, and all HUD view headers.
- Update `getOrCreateNauticalHud` to verify if the existing `nauticalHudContainer` belongs to the current activity context before reuse.
- Implement `mapActivityPause(activity: MapActivity)` for completeness if needed (though not strictly required for current logic).

### [Component] `NauticalPlugin`

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Add a null check for `app.osmandMap` in `updatePowerManagement` to be extra safe, although it's currently handled by a local `val` check.

## Verification Plan

### Automated Tests
- I will check if the project compiles after these changes.
- Since I cannot run the full app, I will perform manual code inspection to ensure no new `lateinit` access is introduced without guards.

### Manual Verification
- Verify that `SailingIntegrationPlugin` correctly clears activity references on `mapActivityDestroy`.
- Verify that `NauticalPlugin` remains crash-free regarding `lateinit connection`.
