# Tasks - Nautical Racing Functionality Fixes

- [x] **Phase 1: Backend & Signal K Logic**
    - [x] Subscribe to `PERF_RACING_TIMER` in `SignalKEngine.kt` (Item 1)
    - [x] Refine VMG Great-Circle calculation in `SignalKEngine.kt` (Item 10)
    - [x] Fix Bilinear Interpolation epsilon in `PolarDiagram.kt` (Item 2)
    - [x] Improve CSV/JSON Polar parsing and validation in `PolarDiagram.kt` (Items 6, 7, 9)
- [x] **Phase 2: Tactical Manager & Wizard**
    - [x] Spherical Start Line distance in `TacticalStartManager.kt` (Item 3)
    - [x] Rewrite Time to Burn (TTB) logic in `TacticalStartManager.kt` (Items 4, 5)
    - [x] Improve Polar Wizard precision and recording safety in `PolarConfigViewModel.kt` (Items 8, 17)
- [x] **Phase 3: UI & HUD Improvements**
    - [x] Themed colors and negative timer in `StartLineHudHeader.kt` (Items 11, 14)
    - [x] "Target VMG" and Unit formatting in `TargetVmgWidget.kt` (Items 12, 13)
    - [x] Negative timer support in `MarineTextWidget.kt` (Item 14)
    - [x] High-frequency countdown updates in `NauticalPlugin.kt` (Item 16)
    - [x] Implement `TacticsHudHeader.kt` for Optimal Tack (Item 15)
- [x] **Phase 4: Verification**
    - [x] Run Unit Tests for Polars and Start Line math
    - [x] Manual verification of HUD changes
