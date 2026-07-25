# MOB & Close-Quarters Autopilot Integration Walkthrough

We have integrated the `AutopilotManager` into the Man Overboard (MOB) and Close-Quarters (Docking/Mooring) maneuvers to ensure maximum safety during critical vessel operations.

## Key Safety Enhancements

### 1. Man Overboard (MOB) Override
The `ManOverboardManeuver` now acts as a high-priority safety interlock:
- **Instant Disengage**: The moment a MOB event is triggered, the app sends a `disengage()` command to the autopilot. This drops the pilot to `standby` immediately, preventing the boat from sailing away from the person in the water.
- **Urgent Announcement**: A clear TTS alert announces "Man Overboard. Autopilot disengaged." to inform the skipper that they must take manual control of the helm for the rescue.

### 2. Close-Quarters Safety (Docking & Mooring)
Approaching a dock or mooring with the autopilot active can be dangerous. We've added two layers of protection:
- **ARMED Layer**: When you select and arm a docking or mooring maneuver, the system checks if the autopilot is engaged. If it is, you'll receive a tactical warning: "Autopilot active. Disengage before approach."
- **EXECUTING Layer**: If you proceed to "EXECUTE" the maneuver while the autopilot is still active, the app will **automatically command it to standby** and announce "Autopilot disengaged for approach." This ensures you have direct, manual steering for the final high-precision approach.

## Technical Implementation

### Maneuver Lifecycle
We extended the `ManeuverStateMachine` and `ManeuverEngine` to support an explicit `transitionToArmed()` phase. This allows for pre-execution safety checks and warnings before the skipper commits to the maneuver.

### Logic Integration
- **ManOverboardManeuver**: Updated the `activate()` method to trigger the autopilot disengagement sequence before starting the tactical guidance loop.
- **Docking/MooringManeuver**: Implemented `transitionToArmed()` and updated `transitionToExecuting()` to handle the autopilot interlock logic.

## Verification Results

### Safety Protocol Check
- Verified that MOB activation instantly triggers the `disengage()` call on the `AutopilotManager`.
- Verified that Docking/Mooring execution handles the transition from any engaged pilot state back to `standby` reliably.

> [!CAUTION]
> The automated disengage feature is designed to aid safety, but the skipper must always be ready to take manual control of the helm instantly.

> [!TIP]
> The ARMED warning for docking is a helpful reminder to check your steering system before you enter the confined space of a marina.
