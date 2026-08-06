# Walkthrough - Signal K & Nautical UX Optimization

I have implemented the Signal K frontend, HUD overlay, and safety engagement optimizations identified in the UX audit.

## Key Changes

### 📡 Signal K Telemetry Mapping
Expanded the Nautical plugin's telemetry capabilities by mapping previously unmapped Signal K streams to displayable widgets.
- **New Widgets**: Magnetic Variation, Vessel Yaw, CPA (Closest Point of Approach), TCPA (Time to CPA), Rudder Angle (Text), and Polar Target Speed.
- **Improved Formatting**: Specialized rendering for TCPA (mm:ss) and automated conversion for new metrics.

### 📐 HUD Collision & Vertical Layout Arbitration
Resolved visual overlaps between the Nautical plugin's emergency headers and standard OsmAnd map elements.
- **Dynamic Positioning**: The Nautical HUD container now automatically adjusts its top margin to sit below the Street Name bar or standard top widgets.
- **Compact Mode**: Implemented the `INauticalHudHeader` interface. When multiple headers (e.g., MOB and NAVTEX) are active, they switch to a compact layout to maximize map visibility.

### 🛡️ State Machine & Safety Locking
Improved the reliability of safety-critical systems.
- **Autopilot Throttling**: Added a command debounce in `AutopilotController` to prevent hardware oscillation on high-latency networks.
- **Audio Arbitration**: Updated `AnchorDriftWatchdog` to suppress anchor drift alarms if a higher-priority Man Overboard (MOB) emergency is active, avoiding confusing overlapping alarms.

### ⚠️ Stale Data Indication
Reduced ambiguity when sensor data is lost.
- **Per-Field Watchdog**: `SignalKEngine` now tracks staleness on a per-path basis.
- **Safety Indicators**: Critical fields like Depth and XTE now explicitly display "TIMEOUT" and use warning colors when data has not been updated for >5 seconds, rather than silently showing "n/a".

## Verification Results

### Manual Verification Path
1. **HUD Arbitration**: Verified that `nauticalHudContainer` correctly calculates `topOffset` based on `widget_top_bar` and `top_widgets_panel`.
2. **Compact Mode**: Confirmed that `visibleCount > 1` triggers `setCompactMode(true)` on all nautical headers.
3. **Stale Data**: Verified that `MarineTextWidget` uses `isWidgetDataStale` to set `alpha = 0.5f` and show "TIMEOUT" for Depth/XTE.
4. **Command Locking**: Confirmed `COMMAND_LOCK_MS` prevents rapid duplicate `PUT` requests to the autopilot server.
5. **Safety Priority**: Verified `triggerAlarm` in `AnchorDriftWatchdog` returns early if `isMobActive` is true.

---
> [!TIP]
> Users can now add the "CPA" and "TCPA" widgets via the "Configure Screen" menu under the "Nautical" category to enhance collision awareness.
