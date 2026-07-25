# Implementation Plan - Architectural Correction for S57SpatialIndex

This plan addresses a critical architectural requirement: all spatial queries must delegate directly to the persistent SQLite database to prevent memory thrashing caused by in-memory JTS feature loading.

## Proposed Changes

### Nautical Plugin - SQLite Persistence
#### [MODIFY] [S57SqliteHelper.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/S57SqliteHelper.kt)
- Update `queryFeatures` to accept an optional `acronyms: Collection<String>?` parameter.
- Modify the SQL query generation to dynamically include an `AND acronym IN (...)` clause when acronyms are provided.
- This ensures that filtering by feature type happens at the database level, significantly reducing the number of objects loaded into JVM memory.

### Nautical Plugin - Spatial Indexing
#### [MODIFY] [S57SpatialIndex.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/S57SpatialIndex.kt)
- Ensure `S57SpatialIndex` acts strictly as a wrapper/delegate for `S57SqliteHelper`.
- Update `queryFeatures(queryGeometry: Geometry)`:
    - Extract the envelope from the JTS geometry.
    - Delegate directly to `sqliteHelper.queryFeatures(minLat, maxLat, minLon, maxLon)`.
    - **CRITICAL**: Remove the in-memory JTS intersection loop (`feature.geometries.any { ... }`) to comply with memory safety requirements.
- Update `queryByAcronym(queryGeometry: Geometry, acronyms: Set<String>)`:
    - Extract the envelope from the JTS geometry.
    - Delegate directly to `sqliteHelper.queryFeatures(..., acronyms)`.
    - This pushes both spatial (bounding box) and attribute (acronym) filtering into SQL.

## Verification Plan

### Automated Verification
- Use `analyze_file` to ensure `S57SqliteHelper` correctly handles the dynamic SQL generation and `S57SpatialIndex` correctly delegates the calls.

### Manual Verification
- Review the code to confirm that no `S57Geometry.toJtsGeometry` calls remain within the hot paths of `S57SpatialIndex`.
- Confirm that `IsochroneRoutingEngine` and `SafetyCorridorChecker` still function correctly with the candidates returned from the bounding-box-only queries (they may perform their own precise checks if needed).
