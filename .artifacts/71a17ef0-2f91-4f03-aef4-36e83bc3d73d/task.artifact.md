# Task List - Phase 8.0C: Pilot Tuning Bottom Sheet

- [x] Modify `bottom_sheet_nautical_pilot.xml`
    - [x] Remove `sea_state_container` from `steering_card`
    - [x] Add "PILOT TUNING" title and section
    - [x] Add Rudder Gain slider
    - [x] Add Counter Rudder slider
    - [x] Add Auto Trim slider
    - [x] Add Sea State slider and Auto switch (moved)
- [x] Modify `NauticalPilotBottomSheet.kt`
    - [x] Bind new sliders and switch in `onViewCreated`
    - [x] Initialize slider values from `osmandSettings`
    - [x] Implement real-time listeners for all four tuning parameters
    - [x] Ensure "AUTO" Sea State logic is preserved
- [x] Verify changes
