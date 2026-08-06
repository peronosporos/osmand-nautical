# Walkthrough - Nautical Plugin UI & Configuration Enhancement

I have successfully completed the UI audit and enhancement for the Nautical plugin. All identified gaps between backend functionality and frontend configuration have been closed.

## Changes Made

### 1. Modernized Display & Heavy Weather
- Replaced legacy "Night Vision" and "Sunlight Mode" toggles with a single, unified **Display Mode** (Normal, Dark/Red, Sunlight).
- Added a high-level **Heavy Weather Mode** toggle to the main settings, allowing for quick optimization of the UI for rough conditions.

### 2. Functional Server Discovery
- Implemented a fully reactive **mDNS Discovery Dialog**. Users can now scan for local Signal K servers and select them from a list, which automatically populates connection parameters.

### 3. Autopilot Tuning & Performance
- Added a new **Autopilot Tuning** section to the settings, exposing critical parameters previously only available in the backend:
    - Rudder Gain
    - Counter Rudder
    - Auto Trim
    - Filter Sensitivity
    - Rudder Limit
- These settings now trigger immediate synchronization with the Signal K hardware via `pushAllSettings()`.

### 4. Advanced Anchor Watch
- Introduced an **Advanced Anchor Configuration** section:
    - Fixed Depth at Anchor
    - Expected Tide Rise
    - Bow Roller Offset
    - Target Scope Ratio
- These parameters ensure much higher accuracy for the swing circle and rode length recommendations.

### 5. Enhanced Safety Boundaries
- Added configuration for **Safe Corridor Width** and **Safety Buffer**, allowing users to define their vessel's safety zone for XTE alarms.

### 6. Settings Consolidation
- Moved Map Indicators (Heading, COG, Current) and Overlays (Laylines, Tides, Trajectory) into the main Nautical Settings screen for better discoverability.

## Technical Cleanup
- Resolved duplicate field definitions in `OsmandSettings.java` (`NAUTICAL_VHF_BACKEND_URL`, `NAUTICAL_VHF_AUTO_REPLAY`).
- Updated `NauticalPlugin.kt`'s preference change listener to handle the expanded set of watched keys, ensuring map redraws and cache invalidation.

## Verification Results

### Manual Verification
- **Settings Screen**: Verified all new categories and items appear and persist correctly.
- **mDNS Dialog**: Verified scan starts correctly and presents found servers.
- **Display Mode**: Verified that switching to "Dark (Red)" correctly applies the system-wide scotopic filter.
- **Autopilot Sync**: Confirmed that changing tuning parameters triggers the `autopilot?.pushAllSettings()` call.
