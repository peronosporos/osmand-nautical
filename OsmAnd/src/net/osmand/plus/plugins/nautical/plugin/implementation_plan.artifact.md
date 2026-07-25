# Implementation Plan - Master Plugin Initializer & Dependency Assembly

Implement the master plugin initializer (`SailingIntegrationPlugin`) and dependency assembly module (`SailingDependencyContainer`) to coordinate all advanced sailing performance features.

## User Review Required

> [!IMPORTANT]
> `SailingIntegrationPlugin` will serve as the primary lifecycle coordinator for advanced nautical features (Polars, GRIB, Weather Routing), extending the base `OsmandPlugin`.

## Open Questions

- None.

## Proposed Changes

### Strings (`OsmAnd/res/values/strings.xml`)
- Add registration strings at the beginning of `strings.xml`:
  - `sailing_performance_plugin_name`: "Advanced Sailing Performance"
  - `sailing_performance_plugin_desc`: "Advanced polar analysis, weather routing, and GRIB integration for nautical navigation."

### Dependency Injection Component (`net.osmand.plus.plugins.nautical.di`)

#### [NEW] [SailingDependencyContainer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/di/SailingDependencyContainer.kt)
- Singleton object providing single-instance access to:
  - `OkHttpClient`
  - `Retrofit` (with Gson converter)
  - `SignalKRestService`
  - `SignalKWebSocketClient`
  - `SailingPerformanceRepository`
  - `GribRepository`

### Plugin Component (`net.osmand.plus.plugins.nautical.plugin`)

#### [NEW] [SailingIntegrationPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/plugin/SailingIntegrationPlugin.kt)
- Extends `net.osmand.plus.plugins.OsmandPlugin`.
- Oversees:
  - `init()`: Initialize DI container and repository.
  - `disable()`: Cleanup resources and disconnect WebSocket.
  - `registerLayers()`: Register `SailingLaylinesMapLayer` and `WeatherRoutingMapLayer`.
  - `createWidgets()`: Register `PolarSpeedRatioWidget` and `TargetVmgWidget`.
  - `getSettingsScreenType()`: Return custom settings screen for sailing performance.

## Verification Plan

### Automated Tests
- Build and compilation verification.

### Manual Verification
- Verify plugin appearance in OsmAnd's plugin manager.
- Verify initialization of repositories and network clients upon plugin activation.
