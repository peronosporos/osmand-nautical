# Implementation Plan - Concurrency & Battery Audit Fixes

Audit and resolve defects in the OsmAnd Nautical Plugin related to battery drain, thread-safety, and UI performance.

## User Review Required

> [!IMPORTANT]
> - `MaritimeOperationsService` was found to be missing but still referenced in the codebase (likely a partial deletion from a previous task). I will finalize its removal by migrating all remaining references to `AnchorDriftWatchdog` managed by `NauticalPlugin`.
> - I will implement a "Power Save" mode for background NMEA/AIS processing that reduces polling when no active alarms (Anchor, Route, MOB) are armed.

## Proposed Changes

### [Component] Concurrency & Thread-Safety

#### [MODIFY] [SailingDataAggregator.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/service/SailingDataAggregator.kt)
- Replace non-atomic `_aggregatedData.value = current.copy(...)` with `_aggregatedData.update { ... }` in both `handleDelta` and `startWatchdog`.
- Ensure all property updates within `handleDelta` are performed on the latest state.

#### [MODIFY] [DirectNmeaMultiplexer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/multiplexer/DirectNmeaMultiplexer.kt)
- Protect `activeClients`, `collectionJobs`, and `statusJobs` with a `Mutex` or use thread-safe collections (e.g., `ConcurrentHashMap`, `CopyOnWriteArrayList`).
- Synchronize `start()` and `stop()` methods to prevent leaked jobs.

---

### [Component] Battery & Performance Optimization

#### [MODIFY] [AisTrackerPlugin.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/aistracker/AisTrackerPlugin.java)
- Downgrade `cpaTimer` frequency from 10s to 30s when the app is in background AND no collision alert is active.
- Implement a check to skip CPA calculations for static or distant targets to save CPU cycles.

#### [MODIFY] [NauticalMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalMapLayer.kt)
- Cache the result of `SafetyCorridorChecker.checkCorridor`. Only re-calculate when the route or vessel position changes significantly, rather than every frame in `onDraw`.

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Add a "Power Save" state to the Signal K engine/connection that increases `delay()` or reduces ping frequency when backgrounded with no active alarms.
- Remove references to `MaritimeOperationsService` and route them to `anchorWatchdog`.

---

### [Component] Technical Debt Cleanup

#### [MODIFY] [AnchorWatchMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/anchor/AnchorWatchMapLayer.kt)
- Remove `MaritimeOperationsService` import.
- Change watchdog access to `NauticalPlugin.getInstance()?.anchorWatchdog`.

#### [MODIFY] [AnchorCalculatorViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/viewmodel/AnchorCalculatorViewModel.kt)
- Remove `plugin?.startMaritimeOperationsService()` (redundant as `NauticalPlugin` already manages the watchdog).

#### [MODIFY] [AndroidManifest.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/AndroidManifest.xml)
- Remove the stale `<service>` entry for `MaritimeOperationsService`.

## Verification Plan

### Automated Tests
- Run `SailingDataAggregatorTest.kt` to ensure logic remains intact after concurrency fixes.
- Add a new test case for `DirectNmeaMultiplexer` to verify rapid start/stop doesn't crash.

### Manual Verification
- Deploy to device/emulator.
- Enable Anchor Watch and verify the "Snail Trail" still draws correctly.
- Verify that background NMEA reception still works but triggers fewer CPU wakeups (via log inspection).
- Check that `NauticalMapLayer` rendering is smoother (less CPU time in `onDraw`).
