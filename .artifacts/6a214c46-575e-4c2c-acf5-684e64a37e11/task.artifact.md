# Tasks - Sea State Automation

- [x] Update `MarineState.kt` with `isAutoSeaStateEnabled`
- [x] Implement Automation Logic in `AutopilotController.kt`
    - [x] Add rolling buffers for Roll/Pitch telemetry
    - [x] Implement `calculateAutoSeaState()` heuristic
    - [x] Add `setAutoSeaStateEnabled(Boolean)`
- [x] Enhance UI in `bottom_sheet_nautical_pilot.xml`
    - [x] Add "AUTO" toggle button/switch
    - [x] Update Slider styling for disabled state
- [x] Update UI Controller (BottomSheet/Widget)
    - [x] Bind "AUTO" toggle events
    - [x] Update UI based on `isAutoSeaStateEnabled` state
- [x] Verification
    - [x] Verify manual control still works when AUTO is OFF
    - [x] Verify automation takes over when AUTO is ON
