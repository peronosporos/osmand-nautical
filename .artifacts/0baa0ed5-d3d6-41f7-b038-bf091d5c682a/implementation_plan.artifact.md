# GRIB Functionality Optimization and Bug Fixes

This plan addresses all 20 identified issues in the Nautical plugin's GRIB implementation, ranging from critical race conditions and memory leaks to UI performance bottlenecks and missing feature support (GRIB1, advanced packing).

## User Review Required

> [!IMPORTANT]
> **Memory Optimization**: Changing grid storage from `Array<DoubleArray>` to `FloatArray` will significantly reduce memory footprint but may slightly reduce precision (Double to Float). This is standard for weather data in mobile apps.
> [!WARNING]
> **GRIB1 & Packing Support**: I will implement GRIB1 and "Complex Packing" support. However, proprietary formats like JPEG2000 or PNG packing (GRIB2) require external native libraries (like OpenJPEG) which are not currently in the project. I will implement a fallback error message for these specific templates.

## Proposed Changes

### [Backend] Data Structures & Repository
- Refactor `TimeStepGrid` to use flat `FloatArray` for 2D grids to minimize object overhead and GC pressure.
- Centralize `nautical/grib` directory path management.
- Replace `String` based caching in `GribRepository` with a spatial-temporal hashing mechanism using `Long` keys.
- Ensure thread-safe atomic access to interpolation cache using `java.util.concurrent` primitives.

#### [MODIFY] [GribGridData.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/grib/parser/GribGridData.kt)
#### [MODIFY] [GribRepository.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/grib/repository/GribRepository.kt)

---

### [Backend] Parser & Engine
- **Fix Critical Bug**: Resolve the `InputStream` closure race condition in `GribManagerBottomSheet` / `GribRepository`.
- **Add GRIB1 Support**: Implement `Grib1Parser` to handle legacy marine weather files.
- **Enhanced GRIB2 Support**:
    - Implement "Complex Packing" (Template 5.2/5.3) for GRIB2.
    - Support Scan Mode bit 4 (zigzag rows).
    - Add missing time units (months, years, decades).
- **Interpolation Engine**: Update to support flat `FloatArray` indexing.

#### [MODIFY] [GribParser.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/grib/parser/GribParser.kt)
#### [MODIFY] [GribInterpolationEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/grib/parser/GribInterpolationEngine.kt)
#### [NEW] [Grib1Parser.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/grib/parser/Grib1Parser.kt)

---

### [Frontend] Map Layer & Rendering
- **Eliminate Allocations**: Move all `Paint` and `Rect` allocations out of `onDraw` and high-frequency loops.
- **Vector Batching**: Batch wave vector rendering into a single `drawLines` call for significant GPU performance gains.
- **UI Polish**:
    - Adjust "EXPIRED FORECAST" banner position to avoid overlapping with top-level OsmAnd widgets.
    - Improve Marching Squares implementation to handle ambiguous cases.
    - Make zoom constraints more flexible.

#### [MODIFY] [OceanographicGribMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/map/layers/OceanographicGribMapLayer.kt)

---

### [Frontend] UI & Lifecycle
- **Fix Recycling Bug**: Ensure `GribAdapter` properly cancels background metadata loading when ViewHolders are recycled.
- **Unify Units**: Standardize unit conversion logic using `OsmandSettings` and existing `MetricsConstants` helpers.

#### [MODIFY] [GribManagerBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/grib/ui/GribManagerBottomSheet.kt)

## Verification Plan

### Automated Tests
- Unit tests for `Grib1Parser` and `Grib2Parser` (complex packing) using mock byte arrays.
- Unit tests for `GribInterpolationEngine` with `FloatArray` backend.

### Manual Verification
- Deploy to device and verify:
    - Smooth panning with GRIB layers enabled (no "jank" from GC).
    - Correct wave vector directions (switching "To/From").
    - "EXPIRED FORECAST" banner visibility and placement.
    - Importing both GRIB1 and GRIB2 files from local storage.
