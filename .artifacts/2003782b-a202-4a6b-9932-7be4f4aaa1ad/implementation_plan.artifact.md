# Implementation Plan - Phase 8.0: S-57 Cartography & Basemap Synchronization

Fix duplicate symbol clutter, density distortion, and night-mode transparency leaks in the S-57 chart renderer.

## User Review Required

> [!IMPORTANT]
> Basemap suppression will be implemented by setting custom rendering properties (`hide_sea_marks`, `hide_coastline`, `no_osm_nautical`). This assumes the active rendering style supports these properties. If the user is using a custom style that doesn't respect these, some clutter might remain.

## Proposed Changes

### Nautical Plugin & S-57 Rendering

#### [MODIFY] [S52SymbolManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/style/S52SymbolManager.kt)
- Add `scale` parameter to `drawSymbol` and all private drawing methods.
- Refactor all hardcoded dimensions (offsets, radii, stroke widths) to be multiplied by `scale`.
- Ensure consistent line widths and fill colors for all symbols.

#### [MODIFY] [S57MapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/ui/S57MapLayer.kt)
- **Basemap Suppression**:
    - Implement `suppressBasemap(active: Boolean)` using `OsmandSettings.getCustomRenderProperty`.
    - Toggle suppression in `initLayer` and `destroyLayer`.
- **Scaling**:
    - Calculate dynamic `scale` based on `tileBox.density` and `tileBox.zoom`.
    - Apply `scale` to `textPaint.textSize`, `strokePaint.strokeWidth`, and point markers.
- **Night Mode Fills**:
    - Force `fillPaint.alpha = 255` and `strokePaint.alpha = 255` in `onDraw`.
    - Ensure background paths (`DEPARE`, `LNDARE`) are drawn first and cover the viewport.
    - Set `textPaint` color to strictly IHO S-52 Night Palette (Red/Amber) during night mode.

#### [MODIFY] [S57FeatureStylizer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s57/style/S57FeatureStylizer.kt)
- Refine priorities to ensure `DEPARE` and `LNDARE` have the lowest priorities (0-10) but are opaque to block the basemap.
- Ensure all S-57 features have higher priority than standard OSM features (handled via z-order in `SailingMapLayerController`).

## Verification Plan

### Automated Tests
- N/A (UI rendering changes are best verified visually).

### Manual Verification
1.  **Basemap Suppression**: Enable S-57 overlay in Boat mode. Verify that OSM shorelines and buoys disappear. Disable overlay, verify they reappear.
2.  **Density Scaling**: Zoom in and out. Verify that symbols and text scale proportionally with the map and do not appear distorted or tiny at high zoom levels.
3.  **Night Mode**: Switch to Night Mode. Verify that the S-57 chart is fully opaque (no bright OSM map bleeding through) and use red/amber tones.
