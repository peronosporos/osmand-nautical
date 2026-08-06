# Implementation Plan - Phase 5: Predictive Navigation & Mission Continuity

This phase focuses on enterprise-grade maritime safety, predictive collision avoidance, and ensuring data integrity during long-range missions.

## User Review Required

> [!IMPORTANT]
> **Predictive AIS Math**: The curved AIS projection (ROT-based) significantly increases computational complexity for the map layer. We will implement a threshold-based activation (e.g., only for ROT > 5°/min) to maintain UI performance.
> **Persistence Storage**: We will use a dedicated binary log file for "Mission Continuity" to avoid overhead on the main OsmAnd settings database.

## Proposed Changes

---

### Predictive AIS Collision Vectors (ROT-based)

Enhancing AIS tracking from linear extrapolation to curved "Hazard Zones" based on the target vessel's Rate of Turn (ROT).

#### [MODIFY] [AisLocation.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd-shared/src/commonMain/kotlin/net/osmand/shared/aistracker/AisLocation.kt)
- Add `rot: Float?` property to the `AisLocation` data class.

#### [MODIFY] [AisObject.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd-shared/src/commonMain/kotlin/net/osmand/shared/aistracker/AisObject.kt)
- Update `getAisLocation()` and `getExtrapolatedLocation()` to pass the `rot` value.

#### [MODIFY] [AisTrackerMath.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd-shared/src/commonMain/kotlin/net/osmand/shared/aistracker/AisTrackerMath.kt)
- Implement `getCurvedPosition(loc: AisLocation, timeInHours: Double): AisLatLon`.
- This uses the formula: `Heading(t) = Heading_0 + ROT * t`, then integrates the position.

---

### Mission Continuity (Offline Logging & Sync)

Ensuring zero data gaps in the logbook during Signal K disconnects.

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Implement a `PersistentDataBuffer` that mirrors in-memory buffers to a local binary file.
- Detect "Connection Loss" and switch to logging from internal Android sensors (GPS/IMU) if configured.

#### [MODIFY] [OkHttpSignalKConnection.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/OkHttpSignalKConnection.kt)
- Add latency (RTT) tracking by measuring the time between a ping and pong (or periodic Hello).
- Expose `lastLatencyMs` to the engine.

---

### Tidal Correction Feedback

Correlating real-time Set and Drift measurements with GRIB current data.

#### [MODIFY] [IsochroneRoutingEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/routing/algorithm/IsochroneRoutingEngine.kt)
- Add `confidenceFactor` calculation.
- If `MarineState.drift` differs from `gribEngine.getCurrentVector` by > 30%, reduce the routing confidence.

---

### Hardware Health Dashboard

A specialized HUD for monitoring system integrity.

#### [NEW] [HardwareHealthHudHeader.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/HardwareHealthHudHeader.kt)
- A new UI component showing:
    - Signal K Latency (ms)
    - Data freshness (Last update age)
    - Packet Integrity (Success vs. Failures)

## Verification Plan

### Automated Tests
- `AisTrackerMathTest.kt`: Verify curved projection accuracy against known circular tracks.
- `SignalKEngineTest.kt`: Verify persistent buffer recovery after a simulated app crash.

### Manual Verification
- Connect to a Signal K simulator and verify the "Hardware Health" HUD displays live latency.
- Enable AIS simulation with ROT and observe curved target vectors on the map.
