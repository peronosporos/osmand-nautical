# Task: Fix Heave-To Maneuver and MOB Integration

- [x] Infrastructure: Update Signal K paths and Autopilot capabilities
    - [x] Update `SignalKPaths.kt` to use `notifications.security.mob`
    - [x] Implement `setRudderAngle` in `AutopilotController.kt`
- [x] Backend Logic: Refactor `ManOverboardManeuver.kt`
    - [x] Replace `setRudderLimit` with `setRudderAngle`
    - [x] Implement dynamic tack detection (AWA sign flip)
    - [x] Integrate stabilization check from `HeavingToManeuver.kt`
    - [x] Add error feedback for autopilot disconnection
- [x] UI/ViewModel: Refine MOB interface
    - [x] Align `MobViewModel.kt` with `PropulsionContextManager`
    - [x] Relax "Heave To" button constraints and refine `isUpwind` threshold
    - [x] Update `MobEmergencyHeaderView.kt` update logic
- [x] Verification
    - [x] Verify Signal K notification path consistency
    - [x] Verify autopilot command sequence via logs/mocks
    - [x] Manual UI verification
