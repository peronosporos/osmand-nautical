# Tasks - Sunlight Vision & Display Mode Fixes

- [x] `NauticalPlugin.kt` Implementation
    - [x] Update `applyDisplayMode` for brightness and UI filters
    - [x] Update `getMapTheme` for Day mode override
    - [x] Integrate Signal K `sunlightMode` automation in `marineStateListener`
- [x] `NauticalMapLayer.kt` Implementation
    - [x] Migrate hardcoded colors to `NauticalColorResolver`
    - [x] Add polarized lens adaptation logic
- [x] `NauticalColorResolver.kt` Implementation
    - [x] Fix priority logic for Sunlight mode
- [x] `NauticalDisplayModeWidget.kt` Implementation
    - [x] Add visual feedback for active Sunlight mode
- [ ] Verification
    - [ ] Verify brightness override
    - [ ] Verify theme coupling
    - [ ] Verify Signal K automation
