# Walkthrough - Signal K Tides Integration

Successfully integrated the `signalk-tides` API into the OsmAnd Nautical plugin, providing native tide station visualization and forecasting that matches the feature set of Freeboard-SK.

## Changes

### [Engine & Data Models]

#### [CapabilityManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/CapabilityManager.kt)
- Added `hasSignalKTides` to `ServerCapabilityMap` to detect server-side tide support.

#### [MarineState.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/MarineState.kt)
- Introduced `TideState` data class to track real-time tide metrics like `heightNow`, `state` (rising/falling), and next extremes.
- Integrated `tide` into the main `MarineState`.

#### [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Added parsing logic for `environment.tide.*` Signal K paths, ensuring real-time tide data is reflected in the UI.

#### [SignalKTideManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKTideManager.kt)
- Created a manager to handle tide station discovery, prediction fetching, and caching from the Signal K API.

---

### [Network Layer]

#### [SignalKModels.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/network/SignalKModels.kt)
- Added data classes for `SignalKTideStation`, `SignalKTideExtreme`, and `SignalKTidePrediction`.

#### [SignalKRestService.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/network/SignalKRestService.kt)
- Defined Retrofit endpoints for fetching tide stations, extremes, and timelines from Signal K.

---

### [UI & Map Visualization]

#### [SignalKTideLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/view/SignalKTideLayer.kt)
- Implemented a custom map layer that renders tide stations with:
    - **Dynamic Icons**: Gauge-style indicators.
    - **Trend Arrows**: Visual cues for rising/falling tides.
    - **Smart Labels**: Combined height and station name labels at high zoom levels.

#### [TideViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/tide/ui/TideViewModel.kt)
- Updated to support selecting Signal K stations and fetching their timelines for the graph view.

#### [TideStationBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/tide/ui/TideStationBottomSheet.kt)
- Integrated with the updated `TideViewModel` to handle Signal K station interactions.

## Verification Results

### Automated Tests
- Verified that `SignalKEngine` correctly maps `environment.tide.*` paths to `MarineState.tide`.
- Verified `SignalKTideManager` correctly handles empty or failed API responses.

## Tide & Current Interoperability Refinements

In addition to the core Signal K integration, I have refined the existing tide functionality to ensure a seamless experience between local and server data:

### [Enhanced Station Discovery]
- **TideViewModel**: Now searches both local harmonic files and the Signal K server for nearby stations, preferring Signal K when available to provide real-time accuracy.
- **Unified Map Interaction**: Tapping the map near any tide or current station (local or SK) now correctly opens the detailed forecast sheet with the appropriate data source.

### [Integrated Current Support]
- **SignalKEngine**: Now parses `environment.current.setTrue` and `environment.current.drift` for real-time tidal stream monitoring.
- **TidalCurrentsMapLayer**: Updated to display Signal K current stations with accurate direction vectors, supplementing the local height-derived approximations.

### [Advanced Visualization]
- **TideGraphView**: Re-engineered to support both water height (meters) and current speed (knots) graphs. It automatically adjusts axes and labels based on the station type and source.
- **Source Transparency**: The `TideStationBottomSheet` now clearly indicates whether data is coming from the Signal K server or a local harmonic dataset.
