# Walkthrough - MOB Functionality Improvements

Comprehensive fixes for the Man Overboard (MOB) functionality, addressing safety, accuracy, and user experience.

## Changes

### Backend & Logic
- **Drift-Aware Vectors**: Updated `MobVectorEngine` to estimate casualty position based on Signal K drift data (`navigation.drift` and `navigation.setTrue`). The return vector now points to where the person is likely to be, not just where they fell.
- **IAMSAR Search Fix**: Corrected the sector search pattern generation in `PatternSteeringEngine`. It now correctly offsets subsequent sectors by 30°, following international search and rescue standards.
- **Signal K Path Alignment**: Unified the MOB notification path to `notifications.security.mob` across the plugin, ensuring interoperability with standard Signal K servers.

### Autopilot & Safety
- **Helm Lock Lifecycle**: Fixed a critical bug where the high-priority helm lock was never released after a MOB maneuver. It is now correctly released when the maneuver completes or is aborted.
- **Downwind Safety Guard**: Added a mandatory confirmation dialog when triggering a "Heave-To" maneuver while sailing downwind, preventing accidental dangerous jibes.
- **Accurate Navigation Math**: Replaced approximate Pythagorean distance calculations with high-precision `KMapUtils.getDistance` (haversine/rhumb) for guidance announcements.
- **Safer Disengage**: Refined the initial autopilot disengage logic to avoid dropping to standby if another emergency maneuver is already active.

### UI & UX
- **Interactive Feedback**: Added a progress bar to the `Cancel MOB` button to provide visual feedback during the required 2-second long-press.
- **Banner Prioritization**: Implemented a `PriorityBlockingQueue` in `NauticalHudManager`. Emergency and warning banners (like MOB) now jump to the front of the queue, ensuring they aren't delayed by non-critical status updates.
- **Standardized Units**: The MOB emergency header now uses the app's global unit settings for distance and bearing formatting.
- **High-Visibility Markers**: Map markers for MOB are now scaled based on screen density (DP), ensuring they are legible on high-resolution displays.
- **Pattern Visualization**: The active search pattern (e.g., Expanding Square, Sector Search) is now drawn on the map, allowing the skipper to visualize the recovery path.
- **Safe Screen Policy**: Refined `FLAG_KEEP_SCREEN_ON` logic to respect the user's global screen timeout settings when an emergency ends.

### Final Polish & Edge Cases
- **Dynamic Turn Threshold**: Replaced the fixed `0.3 Nm` threshold with a speed-aware calculation (`max(0.2, SOG * 60 / 1852)`). This ensures the autopilot chooses the correct turn type (Williamson vs. Scharnow) based on the vessel's actual physics.
- **Signal K Synchronization**:
  - Added remote MOB synchronization in `MobViewModel`. If a physical MOB button or external sensor triggers Signal K, the phone app automatically enters the tactical emergency state.
  - Improved delta parsing in `SignalKEngine` to extract coordinates directly from notification messages if provided.

## Verification Results

### Automated Tests
- Verified `MobVectorEngine` return vector accuracy with simulated 2-knot current over 5 minutes.
- Verified `PatternSteeringEngine` waypoint generation for Sector Search.

### Manual Verification
- [x] Triggered MOB via Volume Up shortcut and verified high-priority alarm.
- [x] Verified `Cancel MOB` long-press progress bar.
- [x] Confirmed Helm Lock is released after clearing MOB (autopilot returned to standby/manual control).
- [x] Verified search pattern line appears on map when SAR pattern is active.
- [x] Confirmed downwind warning appears for Heave-To.
