# Navtex and MSI Broadcast Subsystem Audit and Fixes

Auditing the Navtex/MSI subsystem for decoding precision, deduplication integrity, spatial extraction, and high-priority alerting.

## User Review Required

> [!IMPORTANT]
> The implementation of "BOUNDED BY" polygons requires changing the database schema and the `NavtexMessage` data model. I will use a simple semicolon-separated string for coordinates in the database to avoid complex migrations or new tables for now, unless a more robust solution is requested.

## Proposed Changes

### 1. Navtex Message Decoding & Deduplication

#### [MODIFY] [NavtexSentenceParser.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/engine/NavtexSentenceParser.kt)
- Add support for `$CZCX` NMEA sentences (similar to `$CRRXO`).
- Improve `messageId` parsing to handle variations and ensure robust extraction of station, subject, and sequence.
- Refine coordinate regex to handle dashes, degrees symbols, and multi-point "BOUNDED BY" patterns.

#### [MODIFY] [NavtexRepository.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/data/NavtexRepository.kt)
- Update `upsertMessage` to avoid updating the `timestamp` if the message already exists with the same ID, preventing "phantom updates" that keep messages alive past their expiry.
- Implement content-based deduplication if IDs match but station letters differ (optional but good for "overlapping schedules").

### 2. Spatial Coordinate Extraction & Polygon Mapping

#### [MODIFY] [NavtexMessage.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/engine/NavtexMessage.kt)
- Change `coordinates: LatLon?` to `points: List<LatLon>`.
- Add `isPolygon: Boolean` property.

#### [MODIFY] [NavtexDatabaseHelper.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/data/NavtexDatabaseHelper.kt)
- Replace `COL_LAT` and `COL_LON` with `COL_POINTS` (TEXT).
- Implement migration or update `TABLE_CREATE`.

#### [MODIFY] [NavtexMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/ui/NavtexMapLayer.kt)
- Implement polygon rendering for messages with multiple points.
- Ensure points are properly projected using `tileBox`.
- Add safety check for (0,0) coordinates.

### 3. Priority HUD Alerting & Category Filtering

#### [MODIFY] [NavtexViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/viewmodel/NavtexViewModel.kt)
- Update filtering logic in `uiState` to ensure Subject 'A' and 'D' bypass distance and category filters.
- Update `NavtexFilters` to support multiple excluded/included subjects if needed (at least ensure 'A' and 'D' are always shown).

#### [MODIFY] [NavtexHudView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/ui/NavtexHudView.kt)
- Implement sound and vibration triggers when a new urgent message appears.
- Use `Vibrator` and `ToneGenerator` or `RingtoneManager`.

## Verification Plan

### Automated Tests
- Run `NavtexSentenceParserTest.kt` with new test cases for `$CZCX` and "BOUNDED BY" text.
- Run `NavtexRepositoryTest.kt` to verify deduplication and expiry.

### Manual Verification
- Deploy to device/emulator.
- Inject raw Navtex sentences via mock NMEA stream.
- Verify HUD appearance with sound/vibration for 'A'/'D' messages.
- Verify polygon rendering on the map.
- Verify distance filter behavior.
