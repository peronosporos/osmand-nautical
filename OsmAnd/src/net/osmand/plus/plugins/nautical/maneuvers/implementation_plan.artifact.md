# Implementation Plan - Production-Grade PolarDiagram Engine

Implement a production-grade `PolarDiagram` engine in `net.osmand.plus.plugins.nautical.maneuvers.PolarDiagram` supporting bilinear interpolation, CSV and Signal K JSON ingestion parsers, VMG upwind and downwind optimizers, and thread-safe immutable storage with graceful fallbacks.

## User Review Required

> [!IMPORTANT]
> The updated `PolarDiagram` class will replace the existing simple lookup implementation while fully maintaining API compatibility with `TacticalProcessor` and any other nautical classes (`loadFromCsv`, `getTargetSpeed`, `getOptimalUpwindTwa`).

## Open Questions

- None. Signal K JSON schema expected follows standard Signal K paths (`/signalk/v1/api/resources/polars` or similar resource objects containing `tws`, `twa`, and `speeds` arrays or tabular values). We will support standard Signal K resource structure as well as generic JSON object representations.

## Proposed Changes

### Nautical Maneuvers Component

#### [MODIFY] [PolarDiagram.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/PolarDiagram.kt)

- **Thread-Safety & Immutability**:
  - Encapsulate axes (`twsValues`, `twaValues`) and speed grid (`speedTable`) with thread-safe access (e.g., using `ReentrantReadWriteLock` or atomic/synchronized state snapshot upon loading).
- **Bilinear Interpolation (`getTargetSpeed(tws, twa)`)**:
  - Implement precise 2D bilinear interpolation across TWS (True Wind Speed) and TWA (True Wind Angle) axes to eliminate step-function jumps during live navigation.
  - Handle out-of-bounds clamping or graceful fallback defaults if TWS/TWA are outside table bounds.
- **Dual Ingestion Parsers**:
  - `loadFromCsv(inputStream: InputStream): Boolean`: Enhanced flat CSV polar parser with robust header validation, comment skipping, and whitespace trimming.
  - `loadFromSignalKJson(jsonString: String): Boolean` (or `loadFromSignalKJson(inputStream: InputStream)`): Parses Signal K Resources API JSON schemas (`/signalk/v1/api/resources/polars`).
- **VMG Optimizers**:
  - `getOptimalUpwindTwa(tws: Double)`: Evaluates VMG ($speed \cdot \cos(twa)$) specifically within the 20° to 85° range.
  - `getOptimalDownwindTwa(tws: Double)`: Evaluates VMG specifically within the 100° to 175° range.
- **Safe Fallback Defaults**:
  - Fallback target speed calculation when uninitialized or out of bounds.

## Verification Plan

### Automated Tests
- Create unit tests for `PolarDiagram` verifying:
  1. CSV parsing and JSON parsing correctness.
  2. Bilinear interpolation smoothness (testing values between grid points).
  3. Upwind VMG optimization in 20°–85° range.
  4. Downwind VMG optimization in 100°–175° range.
  5. Thread safety under concurrent reads/writes.

### Manual Verification
- Verify successful compilation and integration with `TacticalProcessor`.
