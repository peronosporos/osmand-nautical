# Walkthrough - Nautical Pilot Functionality Overhaul

This task addressed 20 bugs, architectural issues, and UI/UX improvements in the Nautical plugin's autopilot and pilot head.

## Changes Made

### 1. Backend Consolidation
- **Merged Autopilot Logic**: Consolidated `AutopilotManager` into `AutopilotController`. Removed the redundant `AutopilotManager.kt`.
- **Secure Commands**: Enforced `HTTPS` and Bearer token authentication for all state-mutating commands (Autopilot mode, heading nudges, etc.).
- **Retry Mechanism**: Implemented an automatic retry mechanism (up to 3 attempts) for high-priority commands (e.g., EMERGENCY STOP) on network failure or server 5xx errors.

### 2. Synchronization & Reliability
- **Helm Lock Fix**: Fixed a race condition where the `NauticalHelmArbitrator` lock would remain active if a command was successfully reconciled before the timeout job finished.
- **Improved Reconciliation**: Increased the command timeout to 5000ms to reduce visual "flapping" during slow network updates.
- **Server arrival Trigger**: Fixed `SignalKEngine` to trigger route step listeners when the server advances waypoints in `hasCourseAutoAdvance` mode.
- **Dead Reckoning Stability**: Ensured Dead Reckoning is disabled when internal GPS fallback is active to prevent vessel position "jumping".
- **Shadow Drive Debounce**: Increased the manual override threshold (rudder delta) to 8 degrees to prevent false positives in rough seas.

### 3. UI/UX Enhancements
- **Banner Queueing**: Implemented a thread-safe banner queue in `NauticalHudManager` to prevent overlapping notifications from being lost.
- **Voice Debouncing**: Optimized voice announcement of heading changes with an 800ms debounce window for rapid adjustments.
- **Optimistic Mode Selection**: Improved the mode toggle group to stay checked during "pending" states, providing immediate visual feedback.
- **Tack/Gybe Hysteresis**: Added 20-degree hysteresis to the Tack/Gybe button label logic to prevent flickering during slow maneuvers.
- **Stale Visibility**: Enhanced the Pilot HUD widget to show a yellow indicator and blinking animation when telemetry data is stale.
- **Pattern Abort Confirmation**: Added a confirmation dialog when aborting a search pattern and removed automatic sheet dismissal.

### 4. Architectural Cleanup
- **Package Relocation**: Moved Pilot UI components from the main `:OsmAnd` module to a dedicated package: `net.osmand.plus.plugins.nautical.ui.widgets`.
- **Standardized Base Classes**: Created `BaseNauticalBottomSheet` to handle common Nautical setup (like night vision filters) across all plugin sheets.
- **Configurable Rudder Limit**: Synchronized `RudderView` and `NauticalPilotWidget` with the `NAUTICAL_RUDDER_LIMIT` setting, removing hardcoded 35-degree constraints.

## Verification Results

### Logic & Backend
- Verified `AutopilotController` handles state reconciliation correctly using `try-finally` for helm lock release.
- Confirmed insecure `http://` commands are rejected in `executePut`.

### UI/UX
- Verified that rapid tapping of `+1`/`-1` heading buttons results in a single, clear voice announcement after the tapping stops.
- Confirmed that the "Tack" button doesn't flicker when the vessel is near 90 degrees relative wind.
- Verified that banners queue up and display sequentially.

render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotController.kt)
render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKDataBroker.kt)
render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalHudManager.kt)
