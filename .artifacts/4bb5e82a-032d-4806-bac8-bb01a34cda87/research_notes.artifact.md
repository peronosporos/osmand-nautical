# Autopilot Implementation Audit Notes

I have performed a thorough review of the Autopilot and Signal K engine implementation. Here are the findings categorized by severity.

## Failures & Critical Inconsistencies

### 1. Steering Command Flood (Network/Performance)
- **Issue**: `NauticalPilotBottomSheet` sends a `PUT` request to the server on every single degree change during a dial drag (`onHeadingChanged`).
- **Impact**: Sliding the dial 90° sends 90 HTTP requests in ~1 second. This will likely overwhelm a Signal K server, cause significant lag, and potentially crash the autopilot interface on the boat.
- **Fix**: Implement a debounce (e.g., 150-250ms) for steering commands.

### 2. Standard State Mismatch
- **Issue**: The app uses `route` as a state string for the autopilot.
- **Impact**: Standard Signal K and NMEA 2000 autopilots expect `track` for waypoint navigation. Sending `route` may result in no action on the server.
- **Fix**: Map `ROUTE` UI state to `track` Signal K state.

### 3. Route Sync Gap
- **Issue**: `processRouteStep()` is only called when a waypoint is reached.
- **Impact**: If the connection drops or the autopilot is manually disengaged and then re-engaged, the active waypoint is not re-sent unless the user manually triggers it.
- **Fix**: Re-send active waypoint upon mode change to `ROUTE`/`TRACK`.

## Improvements & UX Polish

### 1. Off-Course Visual Alert
- **Issue**: `checkOffCourseAlert` in `NauticalPlugin` only logs a warning.
- **Improvement**: Add a flashing or high-contrast warning in the `NauticalPilotBottomSheet` when the vessel deviates beyond the configured XTE threshold.

### 2. Connection Health Feedback
- **Issue**: The UI doesn't clearly distinguish between "Live", "Stale" (delayed), and "Disconnected" data in the main control cluster.
- **Improvement**: Dim or color-code the dials when `connectionStatus` is `STALE` or `DISCONNECTED`.

### 3. "Shunt" UI Context
- **Issue**: Shunting is specific to Proas.
- **Improvement**: Replace "Tack" buttons with "Shunt" if `VesselType` is `PROA`.

## Code Health

### 1. Monolithic Parser
- `SignalKEngine.handleIncomingMessage` is very large and handles every path manually. It should ideally be broken into "parsers" for different categories (Environment, Steering, etc.).
- I will not perform a full refactor now, but I will clean up the steering section.
