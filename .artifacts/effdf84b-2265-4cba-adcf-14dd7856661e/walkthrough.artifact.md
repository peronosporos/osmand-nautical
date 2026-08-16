# Walkthrough - Fix Unused Properties and Core Initialization in NauticalPlugin

I have fixed the warnings regarding unused properties in `NauticalPlugin.kt` by correctly instantiating the core subsystems and registering all required listeners. This change transforms the plugin from a collection of declarations into a functional state.

## Changes Made

### Nautical Plugin Core
- **[SignalKEngine & Autopilot](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)**: Changed companion object property visibility to `internal set` to allow the plugin instance to initialize them.
- **Initialization Logic**: `initPlugin()` now orchestrates the startup of:
    - `SignalKEngine` (Telemetry engine)
    - `AutopilotController` (Autopilot backend)
    - `ElectricalController` (Switch/Dimmer control)
    - `AutomatedLogbookEngine` (Event-based logging)
    - `AlarmPriorityManager` (AIS collision safety)
    - `ManeuverTtsHelper` (Voice instructions)
    - `SignalKDiscovery` (mDNS server discovery)
    - `AutopilotRouteListener` (Routing integration)

### Listener Management
- **Registration**: Added `registerListeners()` to connect all `StateChangedListener` properties to their respective `OsmandSettings`. This ensures the plugin reacts immediately to user setting changes (e.g., enabling AIS, toggling laylines).
- **Cleanup**: Added `unregisterListeners()` called in `disable()` to properly detach all listeners and receivers, preventing memory leaks and background processing after the plugin is turned off.
- **System Integration**: Registered `screenStateReceiver` to manage engine state during screen transitions.

### Warning & Redundancy Fixes
- **Removed Unused Proxies**: Deleted 9 properties (e.g., `skTideLayer`, `oceanographicGribMapLayer`) that were redundant delegates to `layerManager` and never used.
- **Code Quality**: Fixed unresolved references, corrected constructor parameters, and removed redundant qualifiers as identified by the IDE inspections.

## Verification Results

### Automated Analysis
- Ran `analyze_file` on `NauticalPlugin.kt`. All previous "unused property" warnings for listeners and core managers are resolved as they are now properly registered.
- Resolved all compilation errors introduced during the migration.
