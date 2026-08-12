# Implementation Plan - Navtex System Comprehensive Bug Fix

This plan addresses all 20 bugs and issues identified in the Navtex functionality audit, ranging from backend parsing logic to frontend UI/UX inconsistencies.

## User Review Required

> [!IMPORTANT]
> **Maritime Standard Synchronization**: Several changes involve switching from local time to UTC (GMT) for consistency with international maritime standards. This may affect how users interpret message timestamps compared to other app features.

> [!WARNING]
> **Checksum Enforcement**: I will transition from "optional" checksums to "mandatory" checksum validation for safety-critical MSI data. This might block legacy receivers that do not provide NMEA checksums.

## Proposed Changes

### Backend: Engine & Parsing Logic
Fixes for parsing inaccuracies, urgency inconsistencies, and coordinate extraction errors.

#### [MODIFY] [NavtexSentenceParser.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/engine/NavtexSentenceParser.kt)
- **Mandatory Checksums**: Update `validateChecksum` to return `false` if the checksum is empty, ensuring only verified data is processed.
- **Urgency Alignment**: Synchronize `isSubjectUrgent` with the standard set used in the Decoder (adding `METEOROLOGICAL_WARNING`).
- **Low-Degree Fix**: Refactor `parseNmeaDegrees` to handle single-digit degrees (e.g., "5") by padding or using a more robust substring logic.
- **Regex Robustness**: Update coordinate regex to handle variations in spacing and degree symbols more reliably.

#### [MODIFY] [NavtexMessageDecoder.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/engine/NavtexMessageDecoder.kt)
- **Flexible Sequence Parsing**: Allow for single-digit or leading-space sequence numbers in the header part.

#### [MODIFY] [NavtexViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/viewmodel/NavtexViewModel.kt)
- **FQN Fix**: Correct the `OsmAndLocationListener` reference to use the proper internal package.
- **Spatial Filtering**: Update filtering logic to use polygon bounds (or centroid) rather than just `points[0]` for distance checks.
- **Safe Subject Filter**: Add null/empty checks for the `NAVTEX_SUBJECT_FILTER` setting.

---

### Backend: Data & Persistence
Fixes for performance bottlenecks and database mapping fragility.

#### [MODIFY] [NavtexRepository.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/data/NavtexRepository.kt)
- **Deferred Cleanup**: Move `cleanupExpired` to a periodic task or background trigger rather than running it on every single write.
- **Column Name Mapping**: Replace hardcoded cursor indices with `getColumnIndexOrThrow` to prevent crashes after schema updates.
- **Zero-Expiry Guard**: Add a minimum floor (e.g., 1 hour) to the expiry duration to prevent immediate deletion of all messages.

---

### Frontend: Map Rendering & Interaction
Fixes for anti-meridian wrapping, clipping, and touch accuracy.

#### [MODIFY] [NavtexMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/ui/NavtexMapLayer.kt)
- **Anti-Meridian Logic**: Implement `normalizeLongitude` in `isPointInPolygon` to handle polygons crossing the 180°/-180° line.
- **Marker Clipping Fix**: Adjust `isMessageVisible` to include a pixel-based buffer around markers.
- **Touch Buffer for Polygons**: Add a configurable touch radius for polygon boundary detection in `collectObjectsFromPoint`.
- **Localization**: Replace hardcoded strings in `getObjectName` with localized resource references.

---

### Frontend: UI/UX & HUD
Fixes for lifecycle leaks, concurrency, and maritime standards.

#### [MODIFY] [NavtexHudView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/ui/NavtexHudView.kt)
- **MOB Isolation**: Remove the `MOB EMERGENCY ACTIVE` leak from the Navtex display logic.
- **Ticker Safety**: Add bounds checks in the `cycleJob` to prevent `IndexOutOfBoundsException` if messages are cleared mid-cycle.

#### [MODIFY] [NavtexDetailsBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/ui/NavtexDetailsBottomSheet.kt)
- **State Restoration**: Move the `NavtexMessage` into `arguments` via Parcelable (or ID-based lookup) to ensure it survives activity recreation.
- **Key Event Filtering**: Refine `onKeyListener` to only consume back-press and navigational keys, allowing system keys to pass through.

#### [MODIFY] [NavtexListFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/ui/NavtexListFragment.kt)
- **UTC Time Formatting**: Update `SimpleDateFormat` to use UTC for all message timestamps.
- **Filter Dialog Implementation**: Replace the stub logic in `showFilterDialog` with a proper multi-subject selection UI.

## Verification Plan

### Automated Tests
- Run `NavtexSentenceParserTest.kt` with new test cases for:
    - Single-digit degrees coordinate parsing.
    - Checksum enforcement (valid vs invalid vs missing).
    - Urgency for Meteorological Warnings.
- Add `NavtexRepositoryTest` cases for:
    - Concurrent writes and cleanup.
    - Expiry duration edge cases (0, very large).

### Manual Verification
- **Anti-Meridian**: Scroll to 180° longitude and verify Navtex polygons render correctly across the line.
- **State Loss**: Open a Navtex detail view and rotate the screen.
- **MOB Isolation**: Activate a MOB alert and verify the Navtex ticker does not show MOB-specific text.
- **UTC Check**: Verify that timestamps in the list match GMT/UTC time.
