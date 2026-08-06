# Implementation Plan - Phase 8.0V: Isolated Dispatchers, I/O Queuing & Paging (Revised)

Fix thread pool exhaustion and SQLite disk gridlock by isolating safety-critical tasks and centralizing I/O access. **AIS Tracker Plugin files will remain untouched as per user instruction.**

## User Review Required

> [!WARNING]
> Due to the restriction on modifying **AIS Tracker Plugin files**, the following requirements from the original request will NOT be implemented in this phase:
> 1. Moving AIS collision detection checks to the `SafetyDispatcher` (logic resides in `AisTrackerPlugin.java`).
> 2. Refactoring AIS target storage pruning (logic resides in `AisTrackerPlugin.java`).
> 3. Bounded Trajectory Paging for AIS targets (would require UI/Logic changes in `AisTrackerLayer` and `AisTrackerPlugin`).

## Proposed Changes

### 1. Safety-Critical Coroutine Dispatcher

#### [NEW] [NauticalDispatchers.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalDispatchers.kt)
- Create a dedicated `SafetyDispatcher` backed by a high-priority single-thread executor.
- This ensures that anchor drift polling is never blocked by heavy I/O or GRIB parsing.

#### [MODIFY] [AnchorDriftWatchdog.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/AnchorDriftWatchdog.kt)
- Switch the watchdog's CoroutineScope to use `NauticalDispatchers.SafetyDispatcher`.
- Force polling and drift logic onto this isolated dispatcher.

### 2. Centralized Disk I/O Queue

#### [NEW] [NauticalIOQueue.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalIOQueue.kt)
- A shared singleton providing a global `Mutex` for disk operations.
- Prevents `SQLiteDatabaseLockedException` by serializing writes across different repositories.

#### [MODIFY] [MarineLogbookRepository.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/logbook/data/MarineLogbookRepository.kt)
- Replace local `writeMutex` with `NauticalIOQueue.writeMutex`.
- Implement trajectory paging to maintain a fixed RAM footprint for the logbook.

#### [MODIFY] [NavtexRepository.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/data/NavtexRepository.kt)
- Use `NauticalIOQueue.writeMutex` for all `upsertMessage` and `cleanupExpired` operations.

#### [MODIFY] [GribRepository.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/grib/repository/GribRepository.kt)
- Ensure GRIB loading respects the shared `NauticalIOQueue` when interacting with the file system.

## Verification Plan

### Automated Tests
- Unit tests for `NauticalIOQueue` to verify serialization of concurrent tasks.
- Integration test for `AnchorDriftWatchdog` running on the `SafetyDispatcher`.

### Manual Verification
- Simulate heavy GRIB parsing and verify that Anchor Drift alarms still trigger instantly.
- Verify that SQLite write errors (database locked) no longer occur during concurrent logbook and NAVTEX updates.
