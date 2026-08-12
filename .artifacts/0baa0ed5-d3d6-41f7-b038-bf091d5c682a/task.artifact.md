# GRIB Implementation TODO List

## Backend: Data & Core
- [x] Refactor `TimeStepGrid` to use `FloatArray` instead of `Array<DoubleArray>` [GribGridData.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/grib/parser/GribGridData.kt)
- [x] Fix race condition in GRIB loading [GribManagerBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/grib/ui/GribManagerBottomSheet.kt) and [GribRepository.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/grib/repository/GribRepository.kt)
- [x] Update `GribParser.kt`:
    - [x] Support `FloatArray` output
    - [x] Implement Scan Mode bit 4 (zigzag)
    - [x] Add missing time units (months, years, etc.)
- [x] Implement `Grib1Parser.kt` [NEW]
- [x] Update `GribInterpolationEngine.kt` for `FloatArray` backend
- [x] Optimize `GribRepository.kt` caching (Spatial hashing + Atomic access)

## Frontend: Rendering & UI
- [/] Optimize `OceanographicGribMapLayer.kt`:
    - [x] Move allocations out of `onDraw`
    - [x] Implement wave vector batching using `drawLines`
    - [x] Fix "EXPIRED FORECAST" banner position
    - [x] Handle ambiguous Marching Squares cases
- [x] Polish `GribManagerBottomSheet.kt`:
    - [x] Fix ViewHolder recycling bug (cancel jobs)
    - [ ] Unify unit conversion logic (Implicitly handled via app settings in Layer, but could be cleaner)

## Final Cleanup & Polish
- [x] Centralize GRIB directory path [GribRepository.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/grib/repository/GribRepository.kt)
- [x] Implement robust GRIB edition detection [GribRepository.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/grib/repository/GribRepository.kt)
- [x] Cleanup `Grib1Parser.kt` (remove unused variables)
- [x] Flatten viewport grid in `prepareIsobars` [OceanographicGribMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/map/layers/OceanographicGribMapLayer.kt)
- [x] Fine-tune wave label positioning

## Verification
- [ ] Unit tests for GRIB1 and GRIB2 (Complex Packing)
- [ ] Performance check for map rendering
- [ ] Verification of UI unit consistency
