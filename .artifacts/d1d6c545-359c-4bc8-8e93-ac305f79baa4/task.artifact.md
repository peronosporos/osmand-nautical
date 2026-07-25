# PyPilot Integration Safety Audit - Task List

- [x] **Phase 1: Mode Switch Safety & Validation**
    - [x] Fix `isWindSafeForManeuver` null handling in `AutopilotController`
    - [x] Add mode validation to `AutopilotController.setAutopilotMode`
    - [x] Update `NauticalPilotBottomSheet` buttons state based on data availability
- [x] **Phase 2: Automated Maneuver Interlocks**
    - [x] Implement global maneuver timeout in `ManeuverEngine`
    - [x] Fix safety check routing in `ManeuverEngine`
    - [x] Add abort-safe timer handling to `GybingManeuver`
- [x] **Phase 3: Emergency Disengagement & Watchdog**
    - [x] Implement high-priority STANDBY in `AutopilotController`
    - [x] Clear pending state immediately on STANDBY
    - [x] Enhance visual/audible connection loss alerts in `NauticalPlugin` and `NauticalMapLayer`
- [x] **Phase 4: Verification**
    - [x] Verify build
    - [x] Manual test scenarios
