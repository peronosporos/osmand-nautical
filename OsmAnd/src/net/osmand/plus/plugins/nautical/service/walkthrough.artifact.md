# Walkthrough - Live Performance Data Aggregator, Foreground Service, & Map Widgets

Implemented the Live Performance Data Aggregator, Foreground Service, and Map Widgets under `net.osmand.plus.plugins.nautical.service` and `widgets`.

## Changes

### Service & Aggregator Component (`net.osmand.plus.plugins.nautical.service`)
- **[NEW] [SailingDataAggregator.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/service/SailingDataAggregator.kt)**:
  - Listens to incoming delta messages, flattens them into `LivePerformanceData`.
  - Runs a 5-second watchdog timer to automatically reset numerical metrics and mark data as stale if telemetry updates drop.
- **[NEW] [SailingDataService.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/service/SailingDataService.kt)**:
  - Android Foreground Service acquiring a `PARTIAL_WAKE_LOCK` with a persistent notification to keep WebSocket telemetry alive in the background.

### Widgets Component (`net.osmand.plus.views.mapwidgets.widgets`)
- **[NEW] [PolarSpeedRatioWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/PolarSpeedRatioWidget.kt)**:
  - Custom OsmAnd dashboard widget displaying Polar Speed Ratio (`performance.polarSpeedRatio`).
- **[NEW] [TargetVmgWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/TargetVmgWidget.kt)**:
  - Custom OsmAnd dashboard widget displaying Target VMG / Speed.

## Verification Results

### Build & Compilation
- Successfully compiled all classes and verified integration with OsmAnd widget framework.
