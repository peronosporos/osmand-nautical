# Walkthrough - Sailing Performance Network & Repository Layer

Implemented the foundational network and repository layer for sailing performance data under `net.osmand.plus.plugins.nautical.network` and `net.osmand.plus.plugins.nautical.repository`.

## Changes

### Build Configuration

#### [MODIFY] [build-common.gradle](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/build-common.gradle)
- Added Retrofit (`2.9.0`) and Gson converter (`2.9.0`) dependencies.

### Network Component (`net.osmand.plus.plugins.nautical.network`)

#### [NEW] [SignalKModels.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/network/SignalKModels.kt)
- Data classes for Signal K Resources API polar profiles (`PolarProfile`, `PolarTable`, etc.) and WebSocket delta streams (`DeltaMessage`, `Update`, `Value`, `LivePerformanceData`) listening to required paths (`navigation.speedThroughWater`, `environment.wind.speedTrue`, `environment.wind.angleTrueWater`, `performance.polarSpeed`, `performance.targetAngle`, `performance.polarSpeedRatio`).

#### [NEW] [SignalKRestService.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/network/SignalKRestService.kt)
- Retrofit interface for fetching polar resources (`/signalk/v1/api/resources/polars`), uploading profiles, and getting server self-identity.

#### [NEW] [SignalKWebSocketClient.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/network/SignalKWebSocketClient.kt)
- OkHttp WebSocket client connecting to `ws://[server-ip]:[port]/signalk/v1/stream?subscribe=none`, sending dynamic subscription filters for all required performance paths, and exposing incoming messages as a `SharedFlow<DeltaMessage>`.

### Repository Component (`net.osmand.plus.plugins.nautical.repository`)

#### [NEW] [SailingPerformanceRepository.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/repository/SailingPerformanceRepository.kt)
- Single Source of Truth exposing:
  - `StateFlow<PolarProfile?>` (`activePolarProfile`)
  - `StateFlow<LivePerformanceData>` (`livePerformanceData`)
- Provides `switchActivePolar(polarId: String)` to switch active polar profiles via REST and local cache.
- Coordinates WebSocket deltas and REST polar fetching.

## Verification Results

### Build & Compilation
- Dependencies synced successfully. All components compiled without errors.
