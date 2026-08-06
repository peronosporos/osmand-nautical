# Task Tracking: Resource Management & Adaptive UI/UX

- `[ ]` **Server Offloading (Resource Management)**
    - `[ ]` Disable local AIS CPA timer when server offloading is available (`NauticalAisManager`)
    - `[ ]` Finalize offloading for VMG, Leeway, and Set/Drift in `SignalKEngine`
    - `[ ]` Lifecycle audit: Ensure strict job cancellation in `SignalKEngine.stop()`
- `[ ]` **Adaptive UI/UX**
    - `[ ]` Link `WidgetType.isAllowed()` to `CapabilityManager` flags
    - `[ ]` Implement dynamic category visibility in `NauticalSettingsFragment`
    - `[ ]` Clean up `MarineTextWidget` logic to use `dataBroker` states preferentially
- `[ ]` **Code Cleanliness & De-duplication**
    - `[ ]` Remove redundant path parsing and hardcoded strings
    - `[ ]` Final verification using `analyze_file`
