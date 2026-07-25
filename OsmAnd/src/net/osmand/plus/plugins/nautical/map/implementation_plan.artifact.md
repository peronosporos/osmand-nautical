# Implementation Plan - Custom OsmAnd Map Layers for Dynamic Visualizations

Implement custom OsmAnd map layers for dynamic visualizations under `net.osmand.plus.plugins.nautical.map.layers` and `controller`.

## User Review Required

> [!IMPORTANT]
> All new user-visible layer strings will be added to the beginning of `OsmAnd/res/values/strings.xml` per project standards.

## Open Questions

- None.

## Proposed Changes

### Strings (`OsmAnd/res/values/strings.xml`)
- Add layer localized strings at the beginning of `strings.xml`:
  - `layer_sailing_laylines`: "Advanced Sailing Laylines"
  - `layer_weather_routing`: "Weather Routing & Isochrones"

### Map Layers Component (`net.osmand.plus.plugins.nautical.map.layers`)

#### [NEW] [SailingLaylinesMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/map/layers/SailingLaylinesMapLayer.kt)
- Extends `OsmandMapLayer`.
- Renders real-time port/starboard tacking and gybing laylines projected forward from the boat position.
- Color-coded dynamically by performance efficiency (Green when ratio >= 1.0, Red when < 1.0).

#### [NEW] [WeatherRoutingMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/map/layers/WeatherRoutingMapLayer.kt)
- Extends `OsmandMapLayer`.
- Renders historical isochrone expansion rings and the finalized optimal weather route polyline.
- Strict point-of-sail color-coding:
  - `TWA < 70°`: Red (Beating)
  - `70° <= TWA <= 130°`: Green (Reaching)
  - `TWA > 130°`: Blue (Running)

### Controller Component (`net.osmand.plus.plugins.nautical.map.controller`)

#### [NEW] [SailingMapLayerController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/map/controller/SailingMapLayerController.kt)
- Registers and unregisters map layers into OsmAnd's core map layer stack with menu toggles.

## Verification Plan

### Automated Tests
- Build and compilation verification.

### Manual Verification
- Verify layer rendering on MapActivity.
