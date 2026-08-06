# Implementation Plan - Phase 8.0N: State Persistence, Cache & Schema Migration

Fix persistence traps, enum index brittleness, and cache poisoning vulnerabilities.

## User Review Required

> [!IMPORTANT]
> - Refactoring `OSMAND_THEME` from `IntPreference` to `EnumStringPreference` is a core change. Existing integer values in `SharedPreferences` will be migrated to strings automatically by `EnumStringPreference` if handled correctly, but I will implement a robust migration path.
> - A new `clearMarineData` routine will be added to `NauticalPlugin` to recursively delete temporary GRIB fragments and decrypted S-63 tiles.

## Proposed Changes

### 1. Resilient Schema Migration

#### [MODIFY] [PolarDiagram.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/PolarDiagram.kt)
- Update `loadFromSignalKJson` to use `optJSONArray` and `optDouble` with safe defaults.
- Add structural validation for parsed arrays.

#### [MODIFY] [MarineLogbookRepository.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/logbook/data/MarineLogbookRepository.kt)
- Update `readEntry` to use `cursor.getColumnIndex` for field resolution, ensuring resilience against DB schema changes.

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Update `processBlackBoxCrash` to use resilient JSON parsing (`optLong`, `optDouble`, etc.).
- Implement `clearMarineData` to purge Signal K buffers, GRIB cache, and S-63 temp files.

---

### 2. String-Based Enums

#### [NEW] [NmeaSource.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/settings/enums/NmeaSource.kt)
- Define `NmeaSource` enum: `SIGNALK`, `BLUETOOTH`, `USB`, `TCP`.

#### [NEW] [OsmandTheme.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/settings/enums/OsmandTheme.kt)
- Define `OsmandTheme` enum: `DARK`, `LIGHT`, `SYSTEM_DEFAULT`.

#### [MODIFY] [OsmandSettings.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/settings/backend/OsmandSettings.java)
- Refactor `NAUTICAL_NMEA_SOURCE` to `EnumStringPreference<NmeaSource>`.
- Refactor `OSMAND_THEME` to `EnumStringPreference<OsmandTheme>`.
- Audit and ensure `NAUTICAL_HEADING_REFERENCE`, `NAUTICAL_TTW_MODE`, and `NAUTICAL_VESSEL_TYPE` use string-based registration (already confirmed).

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Update `updateNmeaSource()` to handle the new `NmeaSource` enum values.

---

### 3. Cache Poisoning Cleanup

#### [MODIFY] [GribRepository.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/grib/repository/GribRepository.kt)
- Add `cleanup()` method to reset interpolation engine and grid data.

#### [MODIFY] [S63Decryptor.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s63/crypto/S63Decryptor.kt)
- Add `cleanup(context: Context)` to delete any temporary decrypted files (though currently in-memory, this adds future-proofing).

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Add `clearBuffers(context: Context)` to delete `nautical_history.bin` and `.json`.

## Verification Plan

### Automated Tests
- Run `PolarDiagramTest` (if exists) with malformed JSON.
- Run `MarineLogbookRepositoryTest` with altered DB schema (simulated).

### Manual Verification
1. Change NMEA Source and UI Theme, verify they persist as strings in `prefs.xml`.
2. Trigger "Clear Marine Data" (will add a button in Nautical Settings or use a secret action) and verify files in `context.filesDir` and `nautical/` are deleted.
3. Simulate a crash, then update app/schema, and verify `processBlackBoxCrash` still works.
