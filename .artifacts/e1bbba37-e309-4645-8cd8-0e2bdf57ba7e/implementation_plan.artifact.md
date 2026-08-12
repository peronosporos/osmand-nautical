# Implementation Plan - Nautical Raster & MBTiles Audit Fixes

This plan addresses all 22 items identified during the nautical plugin audit, ranging from critical thread-safety bugs to UI/UX improvements.

## User Review Required

> [!IMPORTANT]
> **Item 7 (BSB/KAP Support)**: Full native rendering of KAP files is a massive undertaking (requires GDAL or a custom RLE decoder). I will improve the parser to properly warn the user that these are currently "Metadata Only" and won't render, rather than fixing the rendering itself, unless explicitly requested to implement the full decoder.

> [!WARNING]
> **Item 21 (Static State)**: Refactoring `NauticalPlugin.engine` away from static access is a significant architectural change that impacts many parts of the plugin. I will implement a safer lifecycle management while maintaining the necessary global access for map layers.

## Proposed Changes

### 1. Core Engine & Thread Safety (Items 1, 2, 3, 11, 21)

#### [MODIFY] [RasterChartManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/raster/RasterChartManager.kt)
- Replace `MutableList` with a `volatile` immutable list to prevent `ConcurrentModificationException`.
- Fix `intersects` logic to handle inverted coordinate systems.
- Add thread-safe access to source indexing.

#### [MODIFY] [MBTilesHelper.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/raster/MBTilesHelper.kt)
- Implement a connection pool or `synchronized` block for `SQLiteConnection` access in `MBTilesTileSource`.

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Transition from static fields to managed instances with proper cleanup in `shutdownResources`.

### 2. Rendering Engine & Performance (Items 4, 5, 6, 8, 9, 14, 19, 20)

#### [MODIFY] [MarineRasterMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/raster/MarineRasterMapLayer.kt)
- Refactor `onDraw` to avoid swapping `this.map`. Implement a multi-source drawing delegate.
- Pre-allocate `RectF` and `Paint` objects to eliminate GC pressure in `onDraw`.
- Improve "Zero-Flicker" logic to avoid blank screens during initial indexing.

#### [MODIFY] [SignalKRasterLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/raster/SignalKRasterLayer.kt) & [SignalKPmtilesLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/raster/SignalKPmtilesLayer.kt)
- **Task-006 Revision**: Fix `LruCache` bitmap recycling crash by removing explicit `recycle()` calls (let the OS/GC handle it or use a safer pooling strategy).
- Increase `LruCache` size from 50 to 256.
- Implement `inFlightRequests` Map to prevent duplicate network calls for the same tile.
- Support simultaneous display of multiple Signal K overlays (Radar + Weather).
- Apply `NIGHT_VISION_FILTER` to Signal K bitmaps.
- Move Signal K API paths to a configurable registry or metadata.

### 3. UI, Importer & UX (Items 10, 12, 13, 15, 16, 17, 18, 22)

#### [MODIFY] [MarineRasterManagerFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/raster/MarineRasterManagerFragment.kt)
- Fix `ListView` position mapping to account for the footer view.
- Trigger `updateSources()` and map refresh after chart deletion.
- Wrap deletion logic in a background coroutine with a progress indicator.

#### [MODIFY] [MarineRasterImporter.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/raster/MarineRasterImporter.kt)
- Implement duplicate file detection and name sanitization.
- Add deeper validation of MBTiles (checking if `tiles` table is readable).

#### [MODIFY] [MarineRasterSettingsControl.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/raster/MarineRasterSettingsControl.kt)
- Add a `TextView` to display the current opacity percentage next to the `SeekBar`.

#### [MODIFY] [SignalKServerChartsFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/SignalKServerChartsFragment.kt)
- Implement `enableChartOverlay` to actually activate server-side charts by updating the active source ID in `NauticalSettings`.

## Verification Plan

### Automated Tests
- Run existing `MBTilesHelperTest` (if any).
- Create a new unit test for `RasterChartManager` to verify thread-safe source updates.
- Verify memory allocation in `onDraw` using Android Studio Profiler (looking for low GC activity).

### Manual Verification
1.  **Thread Safety**: Repeatedly import and delete charts while panning the map rapidly.
2.  **Night Vision**: Enable "Night Mode" and verify Signal K radar/weather overlays turn red.
3.  **UI Fixes**: Verify `ListView` long-press works correctly near the footer.
4.  **Network Efficiency**: Monitor Logcat for duplicate Signal K tile requests (should be zero for identical keys).
5.  **Multi-Overlay**: Enable both Radar and Windy and verify they both render.
