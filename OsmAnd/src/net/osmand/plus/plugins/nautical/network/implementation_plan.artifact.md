# Implementation Plan - Sailing Performance Network & Repository Layer

Build the foundational network and repository layer for sailing performance data under `net.osmand.plus.plugins.nautical.network` and `net.osmand.plus.plugins.nautical.repository`.

## User Review Required

> [!IMPORTANT]
> Added Retrofit (`2.9.0`) and Gson converter (`2.9.0`) dependencies to `OsmAnd/build-common.gradle` to support `SignalKRestService` using Retrofit as requested.

## Open Questions

- None.

## Proposed Changes

### Build Configuration

#### [MODIFY] [build-common.gradle](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/build-common.gradle)
- Add Retrofit and Gson converter dependencies.

### Network Component (`net.osmand.plus.plugins.nautical.network`)

#### [NEW] [SignalKModels.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/network/SignalKModels.kt)
- Data classes for Signal K Resources API polar resource profile (`PolarProfile`, `PolarTable`, etc.) and incoming WebSocket delta streams (`DeltaMessage`, `Update`, `Value`) with paths:
  - `navigation.speedThroughWater`
  - `environment.wind.speedTrue`
  - `environment.wind.angleTrueWater`
  - `performance.polarSpeed`
  - `performance.targetAngle`
  - `performance.polarSpeedRatio`

#### [NEW] [SignalKRestService.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/network/SignalKRestService.kt)
- Retrofit interface for:
  - Fetching polars (`GET /signalk/v1/api/resources/polars`)
  - Uploading polar profiles (`POST /signalk/v1/api/resources/polars` or `PUT`)
  - Getting server self-identity (`GET /signalk/v1/api/self` or root `/signalk/v1/api/`)

#### [NEW] [SignalKWebSocketClient.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/network/SignalKWebSocketClient.kt)
- OkHttp WebSocket client connecting to `ws://[server-ip]:[port]/signalk/v1/stream?subscribe=none`.
- Sends dynamic subscription filters for the required paths.
- Exposes incoming delta messages as a `SharedFlow<DeltaMessage>` (or `SignalKUpdate`).

### Repository Component (`net.osmand.plus.plugins.nautical.repository`)

#### [NEW] [SailingPerformanceRepository.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/repository/SailingPerformanceRepository.kt)
- Single Source of Truth exposing:
  - `StateFlow<PolarProfile?>` (active polar profile)
  - `StateFlow<LivePerformanceData>` (real-time performance metrics)
- Method `switchActivePolar(polarId: String)`.
- Integrates `SignalKRestService` and `SignalKWebSocketClient`.

## Verification Plan

### Automated Tests
- Unit tests for JSON parsing of Delta messages and Polar profiles.
- Repository state flow testing.

### Manual Verification
- Verify successful compilation with Retrofit and Coroutines flow integration.
