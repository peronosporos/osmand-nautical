# Nautical Plugin Audit & Optimization Plan

This plan outlines the findings from an extensive audit of the `:plugins:Osmand-Nautical` (and its implementation in `:OsmAnd`) for code redundancy and resource optimization. The primary goals are to leverage existing OsmAnd core functionality and offload heavy processing to the Signal K "boat server" where possible.

## User Review Required

> [!IMPORTANT]
> Some optimizations involve offloading logic to the Signal K server. This assumes the server has the necessary plugins installed (e.g., for VMG, Leeway, or Routing). Fallback local logic should be maintained for servers that do not provide these derived values.

> [!WARNING]
> Migrating to core OsmAnd notification and unit formatting systems may slightly change the visual presentation of some nautical data to match the rest of the application.

## Open Questions
1. **Routing Integration:** Since `signalk-routeiq` primarily focuses on physical/depth constraints and tides rather than wind-based weather isochrones, should we integrate it as a "Safety Routing" provider? We would still use the local engine for sailing-specific weather routing but could offload land/depth collision checks to the server.
2. **Universal Fallbacks:** Since hardware is universal, I will implement a "Probe" mechanism that detects Signal K server plugins and automatically switches to server-side calculation only if the required API is present and responding.

## Proposed Changes

### [Capability Discovery & Mapping]

Implement a "Probe" mechanism to identify server-side capabilities and avoid redundant local processing.

#### [NEW] [CapabilityManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/CapabilityManager.kt)
- Fetches `/signalk/v1/api/vessels/self` to identify existing data paths.
- Queries `/signalk/v1/api/plugins` to detect installed routing or performance plugins.
- Maintains a `ServerCapabilityMap` (e.g., `hasServerVmg`, `hasServerLeeway`, `hasWeatherRoutingPlugin`).

---

### [Resource Optimization & Offloading]

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- **Lazy Calculation:** Update `calculateSetAndDrift` and `calculateLeeway` to check `CapabilityManager` first. If the server provides these paths, skip local calculation.
- **Throttling:** Aggressively throttle background telemetry to 0.5Hz when the app is backgrounded.
- **Buffer Management:** Reduce `telemetryBuffers` size if the server indicates it has historical data capability (e.g., `signalk-history`).

#### [MODIFY] [IsochroneRoutingEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/routing/algorithm/IsochroneRoutingEngine.kt)
- **Routing Delegation:**
    - If `winga-weather-routing` is detected: Use its API for weather-optimal paths.
    - If `signalk-routeiq` is detected: Use it for depth/safety constrained "shortest path" routing.
    - Fallback: Use the local engine if no server plugins are found or the device is offline.

---

### [Redundancy Reduction]

## Verification Plan

### Automated Tests
- Unit tests in `OsmAnd/test/java/net/osmand/plus/plugins/nautical` to ensure derived values from Signal K are correctly prioritized over local calculations.
- Benchmarking JSON parsing and telemetry processing on a low-end device/emulator.

### Manual Verification
- Connect to a Signal K server with derived value plugins enabled and verify that OsmAnd uses the server-provided values.
- Verify that weather routing still works (either locally or via server) when a destination is set.
- Check CPU/RAM usage in Android Studio Profiler during active navigation with high-frequency Signal K updates.
