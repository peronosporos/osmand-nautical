# Tasks - Tacking Maneuver Fixes

- [x] Refactor Maneuver Infrastructure
    - [x] Add listener to `ManeuverEngine.kt`
    - [x] Implement listener in `ManeuverManager.kt` and handle cleanup
    - [x] Update `NauticalHelmArbitrator.kt` for sticky tactical locks
    - [x] Update `AutopilotController.kt` to support external lock management
- [x] Improve Tacking Maneuver Logic
    - [x] Replace `Timer` with Coroutine `Job` in `TackingManeuver.kt`
    - [x] Implement smoothed progress calculation
    - [x] Refine performance reporting and lock lifetime management
- [x] Refine Maneuver Overlay UI
    - [x] Update `widget_maneuver_overlay.xml` with icon support and theme attributes
    - [x] Update `ManeuverOverlayWidget.kt` with icon and themed styling
- [x] Verification
    - [x] Manual verification of maneuver flow
    - [x] Check Night Vision theme adaptation
