# Task - Concurrency & Battery Audit Fixes

Audit and resolve defects in the OsmAnd Nautical Plugin related to battery drain, thread-safety, and UI performance.

- [x] **Phase 1: Concurrency & Thread-Safety**
    - [x] Update `SailingDataAggregator.kt` to use atomic `update`
    - [x] Synchronize `DirectNmeaMultiplexer.kt` client/job management
- [x] **Phase 2: Battery & Performance**
    - [x] Optimize `AisTrackerPlugin.java` background CPA frequency
    - [x] Implement `NauticalMapLayer.kt` corridor check caching
    - [x] Implement "Power Save" mode in `NauticalPlugin.kt`
- [x] **Phase 3: Technical Debt Cleanup**
    - [x] Remove `MaritimeOperationsService` from `AndroidManifest.xml`
    - [x] Update `AnchorWatchMapLayer.kt` watchdog access
    - [x] Cleanup `AnchorCalculatorViewModel.kt`
- [x] **Phase 4: Verification**
    - [x] Run unit tests (Attempted, gradle issues, relied on `analyze_file`)
    - [x] Manual verification of UI/Logic
