# Tactical Environmental Mastery Plan (Phase 4)

This final phase focuses on correlating disparate data streams (Depth + Position, Fuel + Speed) to provide predictive tactical insights and ensure 100% UI consistency in extreme environments.

## User Review Required

> [!IMPORTANT]
> **Safety Correlation**: The "Swing Alert" in Anchor Watch depends on the accuracy of your bathymetric charts (S-57/S-63) and the configured `safetyContour`.
> **Fuel Estimation**: "Range to Empty" is a mathematical estimate based on *instantaneous* fuel flow. It does not account for changes in sea state or wind resistance.

## Proposed Changes

### 1. Tactical Safety Correlation

#### [MODIFY] [AnchorDriftWatchdog.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/AnchorDriftWatchdog.kt)
- **Swing-Depth Integration**: During each location update, check the water depth. If `depthBelowKeel` < `safetyContour` while the anchor is set, trigger a "Shallow Swing" warning even if within the drift radius.

### 2. Efficiency & Range Intelligence

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- **Range Calculator**: Implement logic to derive `estimatedRange` (meters) using `fuelLevel` (%), `fuelCapacity` (liters), and `fuelRate` (liters/sec).
- Handle multi-engine aggregation for fuel rate.

#### [NEW] [EngineRangeWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/EngineRangeWidget.kt)
- A specialized HUD widget displaying "Range" and "Time to Empty" with OpenBridge styling.

### 3. Tacking Performance

#### [MODIFY] [TackingManeuver.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/TackingManeuver.kt)
- **VMG Analytics**: Track `velocityMadeGood` throughout the maneuver.
- Announce "Tack Complete - VMG Recovery: XX%" to help the helmsman evaluate the quality of the turn.

### 4. Professional Night Vision (Full UI)

#### [MODIFY] [NauticalSettingsFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalSettingsFragment.kt)
- **Hardware Layer Propagation**: Apply the `NIGHT_VISION_FILTER` to the fragment's root view in `onViewCreated` if Night Vision is active.
- This ensures the entire settings screen is red-monochrome, preventing night blindness when changing configurations.

## Verification Plan

### Automated Tests
- Math validation for `estimatedRange` with zero and maximum fuel rates.
- Verification of hardware layer application in `BaseSettingsFragment` subclasses.

### Manual Verification
1. **Swing Test**: Place a simulated shallow spot near the vessel's anchor trail; verify the "Shallow Swing" alert triggers.
2. **Range Test**: Inject simulated `fuelRate` and `SOG`; verify the "Range" widget displays plausible values (e.g., $100km$ @ $10km/h$ and $10l/h$).
3. **Night Blindness Test**: Open Nautical Settings while Night Vision is enabled; verify the screen is 100% red-monochrome.
