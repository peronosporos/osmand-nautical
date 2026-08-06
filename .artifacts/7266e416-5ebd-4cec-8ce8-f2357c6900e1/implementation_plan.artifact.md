# Implementation Plan - Phase 8.0H: Concurrency, Thread Safety & Database Integrity

This plan addresses critical concurrency issues, thread safety, and database integrity in the OsmAnd Nautical project, specifically targeting memory corruption (torn reads), SQLite lock contention, and NMEA stream interleaving.

## User Review Required

> [!IMPORTANT]
> The refactoring of `MarineState` and `SignalKDataBroker` moves the single source of truth for the vessel's state into a `MutableStateFlow<MarineState>` within `SignalKDataBroker`. This change affects how all consumers receive state updates.

> [!WARNING]
> Enabling SQLite Write-Ahead Logging (WAL) is generally safe but changes the way database files are managed (extra `-wal` and `-shm` files).

## Proposed Changes

### 1. Thread-Safe MarineState & Data Pipelines

#### [MODIFY] [MarineState.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/MarineState.kt)
- Convert `AisTarget` to a fully immutable data class (change `var` to `val`).
- Ensure all nested classes in `MarineState.kt` are immutable.

#### [MODIFY] [SignalKDataBroker.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKDataBroker.kt)
- Remove individual `MutableStateFlow`s for fields (heading, wind, etc.).
- Introduce `private val _marineState = MutableStateFlow(MarineState())` and its public `asStateFlow()` counterpart.
- Implement state updates using `_marineState.update { it.copy(...) }` to ensure atomicity.
- Ensure all `process*Update` methods use this atomic update pattern.

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Refactor to use `dataBroker.marineState` as the primary state flow.
- Remove internal `_currentState` and `_marineStateFlow` if redundant, or synchronize them with `dataBroker`.
- Use the `update` pattern for any direct state modifications.

---

### 2. SQLite Write-Ahead Logging & Connection Pooling

#### [MODIFY] [LogbookDbHelper.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/logbook/data/LogbookDbHelper.kt)
- Call `db.enableWriteAheadLogging()` during database initialization in `openConnection`.

#### [MODIFY] [S57SqliteHelper.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/S57SqliteHelper.kt)
- Override `onConfigure(db: SQLiteDatabase)` to call `db.enableWriteAheadLogging()`.

#### [MODIFY] [MarineLogbookRepository.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/logbook/data/MarineLogbookRepository.kt)
- Implement a `Mutex` to synchronize write operations across different repository instances if they share the same database file.
- Ensure `refreshEntries` (reads) can happen concurrently with `insertEntry` (writes) using WAL.

---

### 3. Synchronized NMEA Stream Multiplexing

#### [MODIFY] [DirectNmeaMultiplexer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/multiplexer/DirectNmeaMultiplexer.kt)
- Introduce a `Channel<String>` for all incoming NMEA sentences.
- Launch a dedicated "Multiplexer Worker" coroutine to consume sentences from the channel sequentially.
- Protect the `navtexBuffer` and parser state by processing them only within the worker coroutine.
- Ensure complete sentences are written to parsers atomically.

---

### 4. Anchor Watch vs. Location Provider Synchronization

#### [MODIFY] [AnchorDriftWatchdog.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/AnchorDriftWatchdog.kt)
- Refactor to consume coordinates from `SignalKEngine.marineStateFlow` (or the unified `StateFlow` from `SignalKDataBroker`).
- Add logic to subscribe to the state flow when the watchdog is active.
- Remove or deprecate `onLocationChanged(location: Location)`.

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Remove the call to `anchorWatchdog?.onLocationChanged(location)` in `locationListener`.
- Ensure `AnchorDriftWatchdog` is properly started/stopped to manage its state flow subscription.

## Verification Plan

### Automated Tests
- Run existing unit tests for `SignalKEngine` and `NMEA` parsing.
- Add/Update tests for `SignalKDataBroker` to verify thread-safe state updates.
- Verify NMEA multiplexing with concurrent inputs using a mock transport.

### Manual Verification
- Deploy to an Android device/emulator.
- Connect multiple NMEA sources (if possible, or simulate them).
- Verify that MarineState updates are smooth and no "torn reads" occur (e.g., mismatched coordinates).
- Test Anchor Watch with simulated GPS drift and verify it uses the filtered Nautical location.
- Verify that Logbook entries can be inserted while S-57 charts are being rendered (read from DB).
