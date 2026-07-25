# Walkthrough - Isochrone Weather Routing Calculation Engine

Implemented the Isochrone Weather Routing calculation engine under `net.osmand.plus.plugins.nautical.routing.algorithm` and `model`.

## Changes

### String Resources (`OsmAnd/res/values/strings.xml`)
- Added localized routing status strings at the beginning of `strings.xml`:
  - `routing_status_calculating`: "Calculating Weather Route..."
  - `routing_status_complete`: "Optimal Route Calculated"
  - `routing_status_failed`: "Routing Failed / Land Collision"

### Domain Models (`net.osmand.plus.plugins.nautical.routing.model`)
- **[NEW] [RoutingModels.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/routing/model/RoutingModels.kt)**:
  - `Waypoint`: latitude and longitude.
  - `RoutingRequest`: start, destination, departure time, and polar profile.
  - `IsochroneNode`: node state with coordinates, cumulative time, heading, parent pointer, BSP, and SOG.
  - `OptimalRouteResult`: list of path waypoints, total time hours, and total distance in NM.

### Routing Algorithm Component (`net.osmand.plus.plugins.nautical.routing.algorithm`)
- **[NEW] [IsochroneRoutingEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/routing/algorithm/IsochroneRoutingEngine.kt)**:
  - Iteratively projects test headings over time steps.
  - Implements vector addition: `Vector(SOG) = Vector(BSP) + Vector(Current)`.
  - Angular sector binning (36 sectors of 10°) for efficient node pruning.
  - Land collision checks and backtracking parent pointer traversal to compile `OptimalRouteResult`.

### ViewModel Component (`net.osmand.plus.plugins.nautical.viewmodel`)
- **[NEW] [RoutingViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/viewmodel/RoutingViewModel.kt)**:
  - Exposes `StateFlow<OptimalRouteResult?>` (`optimalRoute`) and `StateFlow<String>` (`routingStatus`).

## Verification Results

### Build & Compilation
- Successfully implemented and compiled all routing components.
