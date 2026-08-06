# Phase 3 Navigation Intelligence Walkthrough

Successfully implemented Tactical Steering & Dynamic Corrections as part of the Phase 3 Navigation Intelligence upgrades. This phase focuses on high-precision nautical math, dynamic environmental modeling, and advanced autopilot capabilities.

## Changes Made

### 1. Dynamic Leeway Modeling (TASK-032)
Replaced the static leeway assumption with a real-time mathematical model that adapts to vessel heel and speed.
- **[NEW] [LeewayCalculator.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/utils/LeewayCalculator.kt)**: Implements `Leeway_angle = K * (Heel_Angle / STW^2)` with smooth decay below 0.5 knots to prevent instability.
- **[SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)**: Now calculates dynamic leeway and incorporates it into the Tidal Set/Drift vector subtraction (COG/SOG - CTW/STW).
- **[SailingDataAggregator.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/service/SailingDataAggregator.kt)**: Pipes dynamic leeway into the `LivePerformanceData` stream for UI widgets.
- **[LaylineMathEngine](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/laylines/engine/LaylineMathEngine.kt)**: Now receives real-time leeway corrections, shifting layline vectors dynamically as the vessel heels.

### 2. True Wind Angle (TWA) Autopilot Support (TASK-033)
Enhanced autopilot control with dedicated TWA steering mode.
- **[AutopilotController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotController.kt)**: Added support for `steering.autopilot.target.windAngleTrue`.
- **Dynamic Fallback**: Implemented TWA calculation from AWA, STW, and Leeway vectors if the server doesn't provide native `angleTrueWater` data.
- **Toggling**: Added logic to toggle between Compass, AWA, and TWA modes with proper target adjustments.

### 3. High-Frequency Tidal Processing (TASK-035)
Improved the responsiveness and stability of tidal current calculations.
- **Frequency Boost**: Increased processing from 0.2Hz to 1.0Hz in `SignalKEngine.kt`.
- **EMA Smoothing**: Implemented Exponential Moving Average (EMA) over a 5-second window for both Drift (speed) and Set (direction) to filter out wave-induced jitter.

### 4. Polar VMG Optimization (TASK-031)
Upgraded optimal wind angle resolution for better performance targets.
- **[PolarDiagram.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/PolarDiagram.kt)**: Replaced 0.5-degree brute-force search with a **Ternary Search** algorithm, achieving 0.1-degree precision for upwind/downwind VMG optima.

## Verification Results

### Automated Tests
- Verified `LeewayCalculator` with various STW and Heel inputs, confirming the inverse-square relationship and low-speed decay.
- Verified `PolarDiagram` optimization precision against known polar curves.

### Manual Verification
- Simulated Signal K data shows Tidal Set/Drift updating every second with smooth transitions.
- Autopilot commands correctly target `windAngleTrue` when in TWA mode.
- Laylines visible on the map now shift noticeably when simulating a gust increasing vessel heel.

> [!IMPORTANT]
> The dynamic leeway model depends on the `NAUTICAL_LEEWAY_COEFFICIENT` setting. Ensure this is calibrated for your specific hull type (typically 3.0 - 5.0 for modern fin-keel sailboats).

> [!TIP]
> The new TWA Autopilot mode is significantly more stable in rolling seas compared to AWA mode, as it eliminates "apparent wind shear" induced by mast oscillation.
