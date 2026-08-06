# Task List - Phase 8.0V: Isolated Dispatchers, I/O Queuing & Paging

- [x] Create `NauticalDispatchers.kt` with `SafetyDispatcher`
- [x] Update `AnchorDriftWatchdog.kt` to use `SafetyDispatcher`
- [x] Create `NauticalIOQueue.kt` for centralized I/O
- [x] Refactor `MarineLogbookRepository.kt`:
    - [x] Integrate `NauticalIOQueue`
    - [x] Implement trajectory paging (bounded memory)
- [x] Refactor `NavtexRepository.kt` to use `NauticalIOQueue`
- [x] Refactor `GribRepository.kt` to use `NauticalIOQueue`
- [x] Verification and Testing
