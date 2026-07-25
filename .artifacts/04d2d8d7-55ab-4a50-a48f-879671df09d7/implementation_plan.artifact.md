# Tactical Navigation Engine Audit & Fixes

Audit and fix defects in wind vector trigonometry, polar performance evaluation, and map rendering performance.

## User Review Required

> [!IMPORTANT]
> **Internal Angle Representation**: As requested, we are standardizing all internal calculations on **Radians**. This will involve significant changes to `LaylineMathEngine`, `TacticalProcessor`, and `PolarDiagram` to ensure no mixing of Units.
> [!IMPORTANT]
> **Layer Consolidation**: Manual layline rendering will be removed from `NauticalMapLayer` and centralized in `SailingLaylinesMapLayer`.

## Proposed Changes

### [Nautical Plugin - Engine]

#### [MODIFY] [MarineState.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/MarineState.kt)
- No changes needed to fields, but confirm all angle fields (heading, COG, wind direction, TWA, roll/pitch/yaw) are documented as Radians.

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Fix `calculateSetAndDrift` to ensure TWD derivation correctly uses Radians and accounts for the fact that Signal K `angleTrue` is relative to boat heading.
- Account for leeway in current derivation if a model is available (or keep as is if leeway is considered part of the "set" when not measured).

#### [MODIFY] [LaylineMathEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/laylines/engine/LaylineMathEngine.kt)
- **Switch to Radians**: Convert all internal math from Degrees to Radians.
- **Normalize Radians**: Replace `normalizeDegrees` with `normalizeRadians`.
- **Vector Math**: Fix `headingToVector` and `vectorToHeading` to use Radians correctly (0 = North, clockwise).
- **Leeway Logic**: Verify and fix leeway application for port/starboard tacks.

### [Nautical Plugin - Performance]

#### [MODIFY] [PolarDiagram.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/PolarDiagram.kt)
- **Bilinear Interpolation**: Verify clamping and edge cases.
- **Radians Support**: Internalize TWA as Radians to match `MarineState`.
- **Optimal TWA**: Improve `findOptimalTwa` resolution or use a more efficient search.
- **VMG Calculation**: Ensure `VMG = speed * cos(TWA)`.

#### [MODIFY] [TacticalProcessor.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/TacticalProcessor.kt)
- Fix `update` method to use Radians consistently.
- Ensure optimal angles from `PolarDiagram` are handled correctly.

### [Nautical Plugin - UI/Rendering]

#### [MODIFY] [NauticalMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalMapLayer.kt)
- **Deprecate**: Remove `drawLaylines` and `drawWindShifts` (move them or delegate to `SailingLaylinesMapLayer`).

#### [MODIFY] [SailingLaylinesMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/laylines/ui/SailingLaylinesMapLayer.kt)
- **Optimize onDraw**: Ensure no allocations in `onDraw`.
- **State Consistency**: Use `uiState` consistently for boat position.
- **Consolidation**: Add Wind Shift rendering (the arc showing wind variability).

## Verification Plan

### Automated Tests
- Run `PolarDiagramTest` (if exists) or create one to verify bilinear interpolation accuracy.
- Create unit tests for `LaylineMathEngine` verifying intersection points with known current and leeway.

### Manual Verification
- Deploy to emulator and check Layline rendering on the map.
- Verify that toggling Magnetic North in settings correctly shifts the laylines if magnetic variation is present.
- Observe Tacking Cone stability during simulated wind shifts.
