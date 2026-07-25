# Task List - Isochrone Weather Routing Calculation Engine

- [x] Add routing status string resources to the beginning of `OsmAnd/res/values/strings.xml`
- [x] Create domain models (`Waypoint`, `RoutingRequest`, `IsochroneNode`, `OptimalRouteResult`) in `net.osmand.plus.plugins.nautical.routing.model`
- [x] Create `IsochroneRoutingEngine.kt` with vector SOG/BSP/Current math, 36 sector binning, land collision checks, and backtracking in `net.osmand.plus.plugins.nautical.routing.algorithm`
- [x] Create `RoutingViewModel.kt` exposing StateFlow results in `net.osmand.plus.plugins.nautical.viewmodel`
- [x] Verify build and integration
