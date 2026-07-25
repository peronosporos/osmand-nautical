# Tasks - Autopilot Safety and Robustness Improvements

- [x] **Connection Safety Improvements**
    - [x] Update `SignalKEngine.kt`: watchdog behavior and `onConnectionRestored`
    - [x] Update `NauticalPlugin.kt`: persistent visual warning and state cleanup
- [x] **Robust Steering & Maneuver Safety**
    - [x] Update `AutopilotController.kt`: add `isWindSafeForManeuver`
    - [x] Update `NauticalPilotBottomSheet.kt`: add confirmation dialogs for critical actions
    - [x] Update `NauticalPilotWidget.kt`: add confirmation dialogs for mode switches
- [x] **Route Following Improvements**
    - [x] Update `AutopilotRouteListener.kt`: sync full OsmAnd route to engine
    - [x] Update `SignalKEngine.kt`: refine `updateFollowingState` for multi-point routes
- [ ] **Verification**
    - [ ] Create `AutopilotSafetyTest.kt` for unit testing
    - [ ] Manual verification with mocked Signal K stream
