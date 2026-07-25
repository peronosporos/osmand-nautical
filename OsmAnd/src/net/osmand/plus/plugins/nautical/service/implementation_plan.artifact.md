# Implementation Plan - Live Performance Data Aggregator, Foreground Service, & Map Widgets

Implement the Live Performance Data Aggregator, Foreground Service, and Map Widgets under `net.osmand.plus.plugins.nautical.service` and `widgets`.

## User Review Required

> [!IMPORTANT]
> The service and aggregator integrate with `SailingPerformanceRepository` and `SignalKWebSocketClient` to maintain background WebSocket telemetry and watchdog staleness checking.

## Open Questions

- None.

## Proposed Changes

### Service & Aggregator Component (`net.osmand.plus.plugins.nautical.service`)

#### [NEW] [SailingDataAggregator.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/service/SailingDataAggregator.kt)
- Listens to `SharedFlow<DeltaMessage>`, flattens incoming deltas into `LivePerformanceData`.
- Runs a 5-second watchdog timer (via coroutine / Timer) to mark data as stale (`--` or clearing values) if updates drop.

#### [NEW] [SailingDataService.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/service/SailingDataService.kt)
- Android Foreground Service using a `PARTIAL_WAKE_LOCK` and persistent notification to keep WebSocket telemetry alive in the background.

### Widgets Component (`net.osmand.plus.views.mapwidgets.widgets`)

#### [NEW] [PolarSpeedRatioWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/PolarSpeedRatioWidget.kt)
- Custom OsmAnd dashboard widget displaying Polar Speed Ratio (`performance.polarSpeedRatio`).
- Dynamic green/red color tint based on efficiency (e.g. green if ratio >= 1.0 or 100%, red if below).

#### [NEW] [TargetVmgWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/TargetVmgWidget.kt)
- Custom OsmAnd dashboard widget displaying Target VMG / Target Angle.

## Verification Plan

### Automated Tests
- Build and compilation verification.

### Manual Verification
- Verify service start/stop and widget rendering.
