# Walkthrough - Surgical Repair of Coroutines, Hysteresis, and Lock Leaks

Repaired technical defects in the mutex arbitration, propulsion monitoring, and MOB maneuver systems to ensure high stability and correct state transitions.

## Changes

### Engine & Arbitration Refinement

#### [NauticalHelmArbitrator.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/NauticalHelmArbitrator.kt)
- **Lock Stack**: Replaced the simple priority override with a `Stack<Pair<Int, String>>`. Acquiring a higher-priority lock now preserves the previous state.
- **State Restoration**: Releasing a high-priority lock (e.g., Level 1 MOB) now correctly restores the previous lower-priority lock (e.g., Level 3 Anchoring) if it was active, rather than resetting to Standby.
- **Safety Timer Lifecycle**: Fixed timer management to ensure safety release correctly propagates through the priority stack.

#### [PropulsionContextManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/PropulsionContextManager.kt)
- **Active Hysteresis**: Replaced passive timestamp checks with a `Handler` based `hysteresisRunnable`.
- **UI Responsiveness**: When the engine stops, a 5-second timer is started. If no "started" signal arrives, the state flips on the main thread, ensuring laylines reappear exactly after 5 seconds without requiring an NMEA update trigger.

### Maneuver Stability

#### [ManOverboardManeuver.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/ManOverboardManeuver.kt)
- **Coroutine Leak Fix**: Removed `scope.cancel()` from the abort logic. Instead, implemented granular job tracking (`networkJob`, `heaveToJob`) to ensure child jobs are culled while the parent `scope` remains healthy for subsequent triggers.
- **Lifecycle Awareness**: Added `isFinishing` and `isDestroyed` checks before showing the propulsion confirmation modal to prevent "WindowLeaked" or "ActivityNotFound" crashes.
- **Main Thread Dispatch**: Guaranteed that all dialog interactions occur on `Dispatchers.Main`.

### Code Quality & Cleanup

#### [AutopilotController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotController.kt) & [LaylineViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/laylines/viewmodel/LaylineViewModel.kt)
- Fixed multiple "unused variable" and "unused parameter" warnings.
- Cleaned up unused coroutine imports and suppressed necessary parameters in override methods.

## Verification Results

### Integration & Reliability
- **Lock Restoration**: Verified that a MOB trigger on top of an active anchoring maneuver correctly returns to the anchoring lock state after MOB is dismissed.
- **Hysteresis Test**: Confirmed that laylines remain suppressed during simulated 3-second NMEA dropouts and reappear exactly 5 seconds after a sustained engine stop.
- **Crash Resilience**: Verified that aborting and re-triggering MOB multiple times does not result in job accumulation or coroutine scope failure.
