# Walkthrough - Phase 8.0V: Isolated Dispatchers, I/O Queuing & Paging

Implemented a safety-critical dispatcher and a centralized I/O queue to resolve thread starvation and SQLite gridlock. Bounded data paging was also added to the Marine Logbook.

## Changes

### 1. Safety-Critical Coroutine Dispatcher
- **[NauticalDispatchers.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalDispatchers.kt)**: Introduced `SafetyDispatcher`, a dedicated high-priority single-threaded dispatcher.
- **[AnchorDriftWatchdog.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/AnchorDriftWatchdog.kt)**: Refactored to use `SafetyDispatcher` for polling and drift logic, ensuring real-time alerts are never delayed by heavy background tasks.

### 2. Centralized Disk I/O Queue
- **[NauticalIOQueue.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalIOQueue.kt)**: Created a singleton with a shared `Mutex` for disk operations.
- **[MarineLogbookRepository.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/logbook/data/MarineLogbookRepository.kt)**, **[NavtexRepository.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/data/NavtexRepository.kt)**, and **[GribRepository.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/grib/repository/GribRepository.kt)**: All disk-intensive operations now synchronize via `NauticalIOQueue.writeMutex` to prevent `SQLiteDatabaseLockedException`.

### 3. Bounded Data Caching & Trajectory Paging
- **[MarineLogbookRepository.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/logbook/data/MarineLogbookRepository.kt)**: Implemented trajectory paging by limiting the in-memory logbook state to the last 500 entries, maintaining a fixed RAM footprint regardless of voyage duration.

## Verification Results

### Automated Tests
- Verified `NauticalIOQueue` serializes concurrent tasks effectively.
- Confirmed `AnchorDriftWatchdog` runs on the isolated safety thread.

### Manual Verification
- Simulated concurrent GRIB loading and Logbook writes; no `SQLiteDatabaseLockedException` occurred.
- Confirmed that Anchor Drift logic remains responsive even when `Dispatchers.IO` is saturated with parsing tasks.

> [!NOTE]
> AIS Tracker Plugin files were left untouched as per instructions. AIS-related performance optimizations from the original request are deferred.
