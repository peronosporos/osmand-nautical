# Implementation Plan - Fix Unused Properties in NauticalPlugin

This plan addresses warnings about unused properties in `NauticalPlugin.kt`. Many of these properties are listeners that should be registered to ensure the plugin functions correctly (e.g., receiving Signal K updates, responding to setting changes). Additionally, several core components like `SignalKEngine` and `AutopilotController` appear to be declared but never instantiated or connected.

## User Review Required

> [!IMPORTANT]
> The current state of `NauticalPlugin.kt` seems to be missing core initialization logic for `engine`, `autopilot`, and several other managers. This plan will add the necessary instantiation and registration logic in `initPlugin()` and `registerListeners()`.

## Proposed Changes

### Nautical Plugin

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)

- **Instantiate Core Components**: In `initPlugin()`, instantiate `engine`, `autopilot`, `electrical`, `logbookEngine`, `alarmPriorityManager`, `ttsHelper`, `skDiscovery`, etc.
- **Register Listeners**: Create a `registerListeners()` method to:
    - Add `StateChangedListener`s to their respective preferences in `OsmandSettings`.
    - Register `marineStateListener` and `routeStepListener` with `engine`.
    - Add `locationListener` to `app.locationProvider`.
    - Register `prefChangeListener` with `SharedPreferences`.
    - Register `screenStateReceiver` with the system.
    - Initialize `autopilotListener` and register it with `app.routingHelper`.
- **Unregister Listeners**: Update `disable()` and lifecycle methods to properly unregister all listeners and receivers to prevent leaks.
- **Connect Missing Parts**: Ensure `maneuverManager`, `tacticalProcessor`, etc., are correctly initialized and linked.
- **Cleanup**: Remove redundant qualifiers as suggested by the warnings.

## Verification Plan

### Automated Tests
- Since I cannot run Gradle locally, I will rely on code analysis and ensure no new warnings are introduced.
- I will verify that all properties marked as "unused" are now either used or removed if truly redundant.

### Manual Verification
- Visual inspection of the code to ensure all listeners are registered and core components are instantiated.
- Verify that `engine` and `autopilot` are no longer null after `initPlugin()` is called.
