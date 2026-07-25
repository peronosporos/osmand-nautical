# Implementation Plan - GRIB Binary Reader & Spatial-Temporal Interpolation Engine

Implement the GRIB binary reader and spatial-temporal interpolation engine under `net.osmand.plus.plugins.nautical.grib.parser` and `repository`.

## User Review Required

> [!IMPORTANT]
> All new user-visible strings will be added to the beginning of `OsmAnd/res/values/strings.xml` per project standards.

## Open Questions

- None.

## Proposed Changes

### Strings (`OsmAnd/res/values/strings.xml`)
- Add GRIB localized strings at the beginning of `strings.xml`:
  - `grib_status_loading`: "Loading GRIB Weather Data..."
  - `grib_status_ready`: "GRIB Data Ready"
  - `grib_parse_error`: "Failed to parse GRIB file"

### GRIB Parser Component (`net.osmand.plus.plugins.nautical.grib.parser`)

#### [NEW] [GribGridData.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/grib/parser/GribGridData.kt)
- Data structures for GRIB grid bounds, lat/lon grid steps, U/V wind component matrices, and forecast timestamp steps.

#### [NEW] [GribParser.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/grib/parser/GribParser.kt)
- Binary reader extracting GRIB headers, grid geometry, and forecast time steps from input streams.

#### [NEW] [GribInterpolationEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/grib/parser/GribInterpolationEngine.kt)
- Implements Bilinear Spatial Interpolation (across 4 surrounding grid points) and Linear Temporal Interpolation (between forecast time steps).
- Exposes `getWindVector(lat, lon, timestamp)` and current vectors.

### Repository Component (`net.osmand.plus.plugins.nautical.grib.repository`)

#### [NEW] [GribRepository.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/grib/repository/GribRepository.kt)
- Manages local GRIB file storage and exposes `StateFlow<GribStatus>` (`LOADING`, `READY`, `ERROR`).

## Verification Plan

### Automated Tests
- Build and compilation verification.

### Manual Verification
- Verify GRIB parser and interpolation engine behavior with sample inputs.
