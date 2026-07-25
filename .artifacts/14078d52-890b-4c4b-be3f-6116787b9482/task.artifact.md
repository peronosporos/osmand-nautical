# Task List - Dead Reckoning (DR) Fallback System

- [x] Create domain models in `DeadReckoningState.kt`
- [x] Implement `DrProjectionEngine.kt` with vector projection logic
- [x] Implement Unit Tests to verify projection accuracy
- [x] Implement ViewModel and Watchdog
    - [x] Update `LivePerformanceData` with position and heading
    - [x] Update `SailingPerformanceRepository` to parse new fields
    - [x] Add DR strings to `strings.xml`
    - [x] Implement `DeadReckoningViewModel.kt`
- [x] Implement Unit Tests for `DeadReckoningViewModel`
- [x] Implement Map Canvas Overlay and UI Warning Banner
    - [x] Update `DeadReckoningViewModel.kt` for UI requirements
    - [x] Create `dr_warning_banner.xml` layout
    - [x] Implement `DeadReckoningMapLayer.kt`
    - [x] Implement `DrWarningHeaderView.kt`
    - [x] Integrate with `SailingIntegrationPlugin` and `SailingMapLayerController`
- [x] Finalize and provide walkthrough
