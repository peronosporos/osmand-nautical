# OsmAnd Nautical Plugin - Concurrency & Battery Audit Report

## 1. Unmanaged WakeLocks & Battery Drain

| Component | Issue | Impact |
| :--- | :--- | :--- |
| `NauticalPlugin` | Stays in high-power background mode (NavigationService) if "Receive in Background" is enabled, even with no active alarms or routes. | Constant battery drain in background. |
| `AisTrackerPlugin` | CPA collision timer (`cpaTimer`) runs at a fixed 10s interval regardless of vessel speed or collision risk. | Unnecessary CPU wakeups when stationary or in clear waters. |
| `MaritimeOperationsService` | Ghost references in `AnchorWatchMapLayer` and `AnchorCalculatorViewModel` to a deleted service. | Compilation instability and potential resource leaks. |

**Exact Fix (`NauticalPlugin.kt`):**
Downgrade Signal K connection polling and background service usage when no "Anchor", "Route", or "MOB" alarms are active.

---

## 2. UI Redrawing & Off-Screen Thrashing

| Component | Issue | Impact |
| :--- | :--- | :--- |
| `NauticalMapLayer` | Executes `SafetyCorridorChecker.checkCorridor` (S-57 spatial check) inside `onDraw` for every frame. | Severe UI jank and high CPU/GPU usage during map panning. |
| `NauticalPlugin` | Audio alarm loops calling `refreshMap()` for state changes that don't affect visual rendering. | Unnecessary map invalidations. |

**Exact Fix (`NauticalMapLayer.kt`):**
Cache the corridor check result and only invalidate when the route changes or the vessel moves > 50m.

---

## 3. Race Conditions in Data Aggregation

| Component | Issue | Impact |
| :--- | :--- | :--- |
| `SailingDataAggregator` | Read-modify-write on `_aggregatedData.value` in `handleDelta` is non-atomic. | Rapid updates from multiple sources (e.g. NMEA + SignalK) can overwrite each other. |
| `DirectNmeaMultiplexer` | `activeClients` and `collectionJobs` maps are modified without synchronization across coroutines. | `ConcurrentModificationException` and leaked network jobs. |

**Exact Fix (`SailingDataAggregator.kt`):**
```kotlin
_aggregatedData.update { current ->
    current.copy(speedThroughWater = num, ...)
}
```

**Exact Fix (`DirectNmeaMultiplexer.kt`):**
Wrap map/list modifications in `synchronized(activeClients)` or use a `Mutex`.
