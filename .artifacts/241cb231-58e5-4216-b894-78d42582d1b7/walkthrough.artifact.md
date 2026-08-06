# Walkthrough - Full Android Smartwatch Support

I have refined the smartwatch detection and layout logic to ensure compatibility with "Full Android" maritime watches (e.g., Kospet, Lemfo) and improved safe rendering across both round and square displays.

## Changes Made

### 1. Robust Smartwatch Detection
- **Heuristic Fallback**: Updated `WearOsNauticalManager` to automatically detect devices with a `smallestScreenWidthDp` less than 300dp. This catches rugged watches that identify as phones in their firmware.
- **Manual Override**: Added a new setting `NAUTICAL_FORCE_WATCH_LAYOUT` allowing users to manually force the simplified 3-metric "Heartbeat HUD" regardless of hardware flags.

### 2. UI Safety & Geometry Optimization
- **Corner Safety**: Refactored `nautical_heartbeat_hud.xml` with increased horizontal padding (24dp) and centered layout to prevent data from being cut off on round screens.
- **Adaptive Padding**: Enhanced `HeartbeatHudView` to programmatically apply additional vertical padding (16dp) when a round screen is detected, ensuring content stays within the "safe" middle band of the display.

### 3. Settings & Orchestration
- **Global Settings**: Registered `NAUTICAL_FORCE_WATCH_LAYOUT` in `OsmandSettings.java`.
- **User Control**: Integrated a toggle in `NauticalSettingsFragment.kt` and `nautical_settings.xml` under the Hardware category.
- **Live Refresh**: Added a listener in `NauticalPlugin.kt` to immediately re-initialize the HUD when the watch layout mode is toggled, providing instant feedback without app restart.

## Verification Results

### Detection Logic
- **Hardware Watch**: Verified `UI_MODE_TYPE_WATCH` still works for standard WearOS devices.
- **Small Device Heuristic**: Verified that a simulated device with 280dp smallest width correctly triggers the simplified HUD.
- **Manual Override**: Confirmed that toggling "Force Smartwatch Layout" in settings immediately swaps between the complex MFD and the Heartbeat HUD.

### Visual Integrity
- **Round Displays**: Confirmed that metrics (Heading, Depth, XTE) remain fully visible and centered on round watch faces.
- **Square Displays**: Confirmed that the centered layout and safe-zone padding provide a clean look on small square screens.
