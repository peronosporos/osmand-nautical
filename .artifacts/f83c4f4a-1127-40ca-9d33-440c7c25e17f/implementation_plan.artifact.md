# Implementation Plan - Nautical Plugin S-57/S-63 Comprehensive Fixes

This plan addresses the exhaustive list of bugs and performance issues identified in the Nautical plugin's S-57 and S-63 functionality, spanning backend parsing, storage, security, and map rendering.

## User Review Required

> [!IMPORTANT]
> **Database Schema Migration**: This update involves significant changes to `S57SqliteHelper` (moving to R-Tree and efficient geometry storage). All existing S-57 indices will be cleared and rebuilt on the first launch after the update.
> **HWID and User Permits**: I will add a migration path for User Permits to use a more stable identifier than `ANDROID_ID` where possible, but it may require users to re-input their manufacturer keys if the primary identifier changes.

## Proposed Changes

### [Backend] S-57 Parsing & Core Logic

#### [MODIFY] [S57FileReader.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/S57FileReader.kt)
- **Attribute Mapping**: Fix the discrepancy between numeric tag codes and acronyms. Use a proper Data Dictionary mapping for S-57 attributes.
- **ISO 8211 Robustness**: Update directory entry parsing to respect the leader-defined lengths (Tag length, length of field length, length of field position).
- **Encoding Support**: Parse the DSSI record to determine the lexical level/character encoding (ISO-8859-1 vs UTF-8).
- **Memory Optimization**: Refactor spatial record (VRID) handling to use a disk-backed cache or more efficient memory structure for very large cells.
- **Update Merging**: Implement logic to apply .031 and other update files to base .000 cells by matching feature IDs and applying incremental changes.

#### [MODIFY] [S57SqliteHelper.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/S57SqliteHelper.kt)
- **R-Tree Index**: Implement SQLite R-Tree for spatial queries instead of the 4-column B-Tree index.
- **Geometry Storage**: Optimize geometry storage. Instead of raw JSON, use a more compact binary format (WKB or a custom lightweight binary) to reduce I/O and parsing overhead during map draw.
- **Batch Processing**: Improve `addFeaturesStreaming` with larger transaction chunks and prepared statements.

#### [MODIFY] [S57GeometryOptimizer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/style/S57GeometryOptimizer.kt)
- **Recursive Safety**: Rewrite Douglas-Peucker to be iterative or add a strict recursion depth limit to prevent `StackOverflowError`.

---

### [Backend] S-63 Security & Cryptography

#### [MODIFY] [S63Decryptor.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s63/crypto/S63Decryptor.kt)
- **Update Support**: Extend ZIP extraction to handle `.001`, `.002`, etc., instead of just `.000`.

#### [MODIFY] [S63PermitGenerator.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s63/crypto/S63PermitGenerator.kt)
- **Permit Validation**: Add checksum validation for User Permits and Cell Keys.
- **Expiry Warnings**: Implement logic to check expiry dates and trigger notifications via `NauticalNotificationManager`.

#### [MODIFY] [S63CredentialStore.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s63/ui/S63CredentialStore.kt)
- **HWID Stability**: Research and implement a more stable identifier (e.g., combining serial number and other stable hardware IDs) to avoid permit loss on factory reset.

#### [MODIFY] [S63BridgeStream.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s63/bridge/S63BridgeStream.kt)
- **Performance**: Increase the `PipedInputStream` buffer size to reduce context switching during decryption.

---

### [Frontend] Map Rendering & UI Improvements

#### [MODIFY] [S57MapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/ui/S57MapLayer.kt)
- **Async Rendering**: Offload *all* SQLite queries (including critical hazards) to background threads. Use a placeholder/loading state for hazards during initial view load.
- **Path Caching**: Modify `Path` caching to store paths in a local relative coordinate system or use `Matrix` transformations to handle map rotation and small shifts without full re-computation.
- **Touch Precision**: Replace bounding box selection with precise geometric intersection checks (Point-in-Polygon, Point-to-Line distance).

#### [MODIFY] [S52SymbolManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/style/S52SymbolManager.kt)
- **Expanded Library**: Implement additional S-52 symbols for common buoys, beacons, and sea marks.

#### [MODIFY] [S57ChartManagerFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/ui/S57ChartManagerFragment.kt)
- **Coverage Calculation**: Fix the area calculation to account for chart overlaps using a grid-based or union-based approach.
- **Reactive UI**: Observe `S57SpatialIndex.indexingStatus` to provide real-time feedback on the indexing process.

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- **Basemap Settings**: Ensure that basemap suppression settings are reverted cleanly when the plugin or layer is disabled.

## Verification Plan

### Automated Tests
- **S57ParserTest**: Unit tests for ISO 8211 directory parsing, attribute acronym mapping, and update merging.
- **S63CryptoTest**: Verify decryption of base cells and update fragments. Validate permit checksum logic.
- **SpatialIndexTest**: Benchmark R-Tree query performance vs. the old B-Tree implementation.

### Manual Verification
- Deploy to a device, import a set of S-63 charts with updates.
- Verify map rendering smoothness (jank detection) during panning and zooming.
- Test touch interaction precision on small features.
- Check "Chart Manager" for real-time indexing status updates.
