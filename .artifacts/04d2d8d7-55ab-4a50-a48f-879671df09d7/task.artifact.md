# Task List - Tactical Navigation Engine Audit & Fixes

- [x] **Phase 1: Engine & Math Core (Radians Standardization)**
    - [x] Update documentation in `MarineState.kt`
    - [x] Fix `calculateSetAndDrift` in `SignalKEngine.kt`
    - [x] Refactor `LaylineMathEngine.kt` to use Radians and fix vector math
    - [x] Update `PolarDiagram.kt` for Radian TWA and robust interpolation
    - [x] Update `TacticalProcessor.kt` to use Radians consistently
- [x] **Phase 2: UI & Rendering Consolidation**
    - [x] Deprecate/Remove redundant drawing in `NauticalMapLayer.kt`
    - [x] Optimize and enhance `SailingLaylinesMapLayer.kt` (Add Wind Shifts)
- [x] **Phase 3: Verification**
    - [x] Verify math with unit tests (PolarDiagramTest updated and reviewed)
    - [x] Final UI check on map layer stability
