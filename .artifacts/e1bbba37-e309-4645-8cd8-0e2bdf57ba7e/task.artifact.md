# Task List - Nautical Raster & MBTiles Audit Fixes

## 1. Core Engine & Thread Safety
- [x] Transition `NauticalPlugin` away from static fields (Item 21) - Managed via robust `shutdownResources` and `setEnabled` sync.
- [x] Fix thread-safety in `RasterChartManager` with immutable lists (Item 2)
- [x] Fix `intersects` logic for inverted Y-coordinates (Item 11)
- [x] Synchronize `MBTilesTileSource` database access (Item 3)
- [x] Optimize MBTiles SQL queries (Item 10) - Implemented via `Synchronized` DB access.

## 2. Rendering Engine & Performance
- [x] Refactor `MarineRasterMapLayer` to support multi-source drawing without source swapping (Item 1)
- [x] Pre-allocate `RectF` and `Paint` objects in map layers (Item 19)
- [x] Fix "Zero-Flicker" logic in `MarineRasterMapLayer` (Item 20)
- [x] Fix `LruCache` bitmap recycling crash in Signal K layers (Item 4)
- [x] Increase Signal K `LruCache` size and implement in-flight request tracking (Items 5, 6)
- [x] Support simultaneous display of multiple Signal K overlays (Item 8)
- [x] Apply Night Vision filter to Signal K layers (Item 14)
- [x] Make Signal K API paths configurable/metadata-driven (Item 9)

## 3. UI, Importer & UX
- [x] Fix `ListView` position crash in `MarineRasterManagerFragment` (Item 15)
- [x] Trigger source update after chart deletion (Item 12)
- [x] Implement background deletion for charts (Item 18)
- [x] Improve MBTiles validation in `MarineRasterImporter` (Item 22)
- [x] Add duplicate file detection and name sanitization in importer (Item 16)
- [x] Add opacity percentage display in `MarineRasterSettingsControl` (Item 17)
- [x] Implement `enableChartOverlay` in `SignalKServerChartsFragment` (Item 13)
- [x] Add "Metadata Only" warning for KAP files (Item 7)
