# Navtex & MSI Subsystem Audit Walkthrough

Successfully audited and refactored the Navtex processing pipeline to ensure safety compliance and high-priority alerting.

## Changes Made

### 1. Navtex Message Decoding & Deduplication
- **Added `$CZCX` support**: `NavtexSentenceParser` now handles both `$CRRXO` and `$CZCX` sentences.
- **Smart Deduplication**: `NavtexRepository` now preserves the original reception timestamp if the message body is identical upon re-reception, preventing expiry extensions for repetitive broadcasts.
- **Robust ID Parsing**: Fixed assumptions about fixed-length message IDs.

### 2. Spatial Coordinate Extraction & Polygon Mapping
- **Multi-point Support**: Refactored `NavtexMessage` and `NavtexDatabaseHelper` to store and process a list of `LatLon` points instead of a single coordinate.
- **Regex Enhancements**: Added `COORD_PATTERN_3` for simple degree-only formats (e.g., `38N 020E`) and updated `extractCoordinates` to return all geographic points found in the message body.
- **Polygon Rendering**: `NavtexMapLayer` now renders shaded polygons for messages containing 3 or more points (e.g., "BOUNDED BY" zones) while maintaining point markers for single-point warnings.
- **Null Island Protection**: Added checks to ignore (0,0) coordinates resulting from failed parsing.

### 3. Priority HUD Alerting & Category Filtering
- **Urgent Filter Bypass**: Modified `NavtexViewModel` to ensure Subject Type 'A' (Navigational Warning) and 'D' (Search and Rescue) always bypass distance and category filters, ensuring they are always visible on the map and HUD.
- **High-Priority HUD**: Updated `NavtexHudView` to trigger system notification sounds and vibration feedback when a new urgent message is received.

## Verification Results

### Automated Tests
- Updated `NavtexSentenceParserTest` with cases for `$CZCX`, "BOUNDED BY" polygons, and various coordinate formats.
- Updated `NavtexRepositoryTest` to reflect database schema changes and deduplication logic.

### Manual Verification
- Verified HUD alerts (sound/vibration) trigger correctly.
- Confirmed polygons render with appropriate transparency and color-coding (Red for urgent, Amber for others).
- Confirmed SAR messages bypass the 100km distance filter.
