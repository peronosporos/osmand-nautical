# Implementation Plan - Isochrone Weather Routing Calculation Engine

Implement the Isochrone Weather Routing calculation engine under `net.osmand.plus.plugins.nautical.routing.algorithm` and `model`.

## User Review Required

> [!IMPORTANT]
> All new user-visible routing strings will be added to the beginning of `OsmAnd/res/values/strings.xml` per project standards.

## Open Questions

- None.

## Proposed Changes

### Strings (`OsmAnd/res/values/strings.xml`)
- Add routing localized status strings at the beginning of `strings.xml`:
  - `routing_status_calculating`: "Calculating Weather Route..."
  - `routing_status_complete`: "Optimal Route Calculated"
  - `routing_status_failed`: "Routing Failed / Land Collision"

### Domain Models (`net.osmand.plus.plugins.nautical.routing.model`)

#### [NEW] [RoutingModels.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/routing/model/RoutingModels.kt)
- `Waypoint` (lat, lon)
- `RoutingRequest` (start, destination, departureTime, polarProfile)
- `IsochroneNode` (lat, lon, cumulativeTime, heading, parent, speedThroughWater, speedOverGround)
- `OptimalRouteResult` (nodes: List<Waypoint>, totalTimeHours: Long, totalDistanceNm: Double)

### Routing Algorithm (`net.osmand.plus.plugins.nautical.routing.algorithm`)

#### [NEW] [IsochroneRoutingEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/routing/algorithm/IsochroneRoutingEngine.kt)
- Injects `PolarProfile` and `GribInterpolationEngine`.
- Iteratively projects test headings over time steps.
- Vector addition: `Vector(SOG) = Vector(BSP) + Vector(Current)`.
- Angular sector binning (36 sectors of 10°) for node pruning.
- Land polygon collision checks.
- Backtracking parent pointer traversal to compile `OptimalRouteResult`.

### ViewModel (`net.osmand.plus.plugins.nautical.viewmodel`)

#### [NEW] [RoutingViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/viewmodel/RoutingViewModel.kt)
- Exposes `StateFlow<OptimalRouteResult?>` and routing status.

## Verification Plan

### Automated Tests
- Build and compilation verification.

### Manual Verification
- Verify routing calculation and result exposure.
