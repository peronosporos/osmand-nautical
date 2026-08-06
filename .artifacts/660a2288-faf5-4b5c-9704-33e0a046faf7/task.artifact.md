# Task: Signal K & Nautical UX Optimization

- [x] **1. Signal K Telemetry Mapping**
    - [x] Add new `WidgetType` constants in `WidgetType.java`
    - [x] Implement mapping logic in `MarineTextWidget.kt`
    - [x] Add string resources for new widgets
- [x] **2. HUD Collision & Vertical Layout Arbitration**
    - [x] Update `NauticalPlugin.kt` to handle HUD top margin and compact mode
    - [x] Implement `INauticalHudHeader` interface
    - [x] Update MOB, DR, and Navtex headers to implement the interface
- [x] **3. State Machine & Safety Improvements**
    - [x] Implement command debounce in `AutopilotController.kt`
    - [x] Integrate priority check in `AnchorDriftWatchdog.kt` to suppress alarms during MOB
- [x] **4. UX & Stale Data Indication**
    - [x] Update `SignalKEngine.kt` to track `stalePaths`
    - [x] Update `MarineTextWidget.kt` to show "TIMEOUT" for safety-critical fields when data is stale
- [ ] **5. Verification**
    - [ ] Manual verification of HUD layout
    - [ ] Verification of Autopilot locking
    - [ ] Verification of Audio arbitration
