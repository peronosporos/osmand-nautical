# Walkthrough - Med-Mooring Maneuver Refinement

I have completed the refinement of the Med-Mooring maneuver, addressing all 18 identified issues. The system is now significantly more robust, safety-aware, and user-friendly.

## Key Changes

### 1. Robust State Machine & Stern-way Detection
- **Reversing Detection**: The `MedMooringManeuver` now explicitly checks COG vs Heading to detect when the vessel is actually reversing (`isReversing`), preventing phase transitions based on forward motion.
- **Improved Payout Logic**: The transition from `ANCHOR_DROP` to `PAYOUT_RODE` is no longer a blind timer; it now waits for confirmed stern-way.
- **Dynamic Progress**: The approach progress bar is now correctly scaled from 0-100%.

### 2. Safety & Collision Awareness
- **Depth Safety**: Added a mandatory check against `NAUTICAL_VESSEL_DRAFT` before executing.
- **Over-speed Protection**: The maneuver now automatically aborts with a high-priority alarm if speed exceeds 1.5 knots within one vessel length of the quay.
- **Helm Override**: Integrated with the `SignalKDataBroker` to automatically abort the maneuver if the skipper takes manual control of the helm.
- **Multi-instance Engine Support**: The pre-flight check now iterates through all engines to ensure at least one is running for power maneuvers.

### 3. Precision Autopilot Integration
- **Perpendicular Approach**: During the `STERN_APPROACH` phase, the autopilot now calculates and maintains a heading perpendicular to the quay target, rather than just locking the current heading.
- **Safe Restoration**: Autopilot now defaults to `STANDBY` after maneuver completion or abortion, avoiding dangerous automatic returns to "track" mode in close quarters.

### 4. UI & Visualization Improvements
- **Anchor Rendering**: The dropped anchor position is now rendered as an icon on the map during the maneuver.
- **Backing Vector**: The backing vector length is now dynamically scaled based on the distance to the quay target.
- **Localization**: All engine instructions and maneuver names in the HUD are now fully localized via `strings.xml`.
- **Parameter Sync**: Adjusting vessel length or scope in the maneuvers bottom sheet now immediately updates the active maneuver instance and re-announces the updated targets.

## Verification Results

### Automated Tests
- Verified code compilation and lack of unresolved references (all initially reported errors fixed).

### Manual Logic Verification
- Checked geodesic bearing and distance calculations for correctness.
- Verified that all new strings are correctly mapped in `strings.xml`.
- Confirmed that `internal` vs `public` access modifiers allow cross-package communication between the engine, UI, and map layers.

> [!IMPORTANT]
> Skippers should still maintain visual contact with the quay and be prepared to take manual control if Signal K telemetry becomes stale or inaccurate.
