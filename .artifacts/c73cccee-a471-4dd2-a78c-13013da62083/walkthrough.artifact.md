# Walkthrough - Batch 2 Architectural Refactor: Native VHF & NAVTEX Ticker

This walkthrough covers the architectural improvements to Batch 2, aligning it with OsmAnd's native data handling and optimizing the HUD for map legibility.

## 1. Native VHF Data Integration
Moved away from a proprietary database to leverage OsmAnd's high-performance vector maps.

- **Direct Tag Querying**: [VhfPoiSearchLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/poi/ui/VhfPoiSearchLayer.kt) now queries `BinaryMapIndexReader` on-the-fly.
- **Support for Standard Tags**: It identifies VHF stations using `seamark:radio:channel`, `communication:vhf`, and `seamark:information` tags.
- **Enhanced Callouts**: Tapping a station now dynamically extracts and displays the channel info (e.g., "VHF Ch. 16") in the standard POI menu.

## 2. Optimized NAVTEX HUD
Replaced the cluttered vertical stack with a single-row auto-cycling ticker.

- **Single-Line Ticker**: [NavtexHudView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/ui/NavtexHudView.kt) now only ever occupies one row, maintaining maximum map real estate.
- **Priority Cycling**: Active warnings are sorted by urgency (SAR > Meteo > Navigational) and cycle every 5 seconds.
- **Position Badge**: A `[ 1 / N ]` badge indicates the current message's position in the priority queue.
- **Detail View**: Tapping the ticker opens the new full-screen [NavtexListFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/ui/NavtexListFragment.kt) for detailed inspection of all active warnings.

## 3. Clean-up & Stability
- Deleted the redundant `VhfPoiDatabase.kt`.
- Streamlined `NauticalPlugin.kt` to handle the new HUD interactions.
- Optimized spatial polygon intersection checks in `NavtexMapLayer.kt`.

> [!TIP]
> The VHF layer automatically activates at zoom level 13 and higher to avoid map clutter in overview modes.
