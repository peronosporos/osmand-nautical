# Tasks: Nautical Pilot Refactoring

- [x] Core Engine & Connection (Auth Logic)
    - [x] Add `UNAUTHORIZED` to `ConnectionStatus` in `MarineState.kt`
    - [x] Update `SignalKConnection` interface
    - [x] Update `OkHttpSignalKConnection` implementation
    - [x] Update `SignalKEngine` to handle auth errors
    - [x] Update `NauticalPlugin` connection logic
- [x] Layout Updates
    - [x] Update `nautical_pilot_bottom_sheet.xml` (Alignment & Cleanup)
    - [x] Update `bottom_sheet_nautical_advanced.xml` (Add Sea State section)
- [x] Bottom Sheet Logic Updates
    - [x] Update `NauticalPilotBottomSheet.kt` (Logic cleanup & Auth check)
    - [x] Update `NauticalAdvancedSettingsBottomSheet.kt` (Add Sea State logic)
- [ ] Verification
    - [ ] Verify UI layout alignment
    - [ ] Verify Sea State controls in Advanced Settings
