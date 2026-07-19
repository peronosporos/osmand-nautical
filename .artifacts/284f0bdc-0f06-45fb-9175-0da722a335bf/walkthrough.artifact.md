# Nautical Pilot UI & Logic Overhaul

The Nautical Pilot interface has been updated to fix icon inconsistencies, implement the requested telemetry matrix, and improve the visibility of the rudder sensor in the HUD widget.

## Key Changes

### 1. Icons and Branding Consistency
- **Wind Mode Icon:** Updated to `ic_action_wind` in both the HUD widget and the Pilot bottom sheet.
- **Unified Pilot Icon:** Changed the default "OFF" state and layout icons from a general ship to `ic_plugin_nautical_map` for better feature branding.
- **Button Uniformity:** All mode toggle buttons in the bottom sheet now have consistent `24dp` icon sizing and padding.

### 2. Telemetry Matrix Implementation
Fixed the issue where mode selection didn't update the telemetry grid. The UI now provides immediate feedback when switching modes.

| Mode | Column 1 (Primary) | Column 2 (Context) | Column 3 (Health) |
| :--- | :--- | :--- | :--- |
| **Heading Hold** | SOG / STW | Hdg Err / Set & Drift | Target Hdg / Rot |
| **Wind Hold** | TWS / STW | **Wind Err** / TWA | Target TWA / Polar Target |
| **Track/Route** | SOG / DTW | XTE / COG | BTW / TTW |

> [!NOTE]
> **Wind Error** is now calculated as the difference between the current Apparent Wind Angle (AWA) and the Target AWA.

### 3. Rudder Sensor Visibility
- **HUD Widget:** The rudder indicator has been redesigned as a **black mark on a white bar** (`4dp` height) to ensure maximum visibility even with transparent widgets over complex map backgrounds.
- **RudderView:** Updated scale and text colors to use application theme resources (`text_color_primary` and `text_color_secondary`) for better consistency.

## Verification Results

### Manual Verification
- [x] **Icons:** Wind mode shows the wind icon.
- [x] **Sizing:** Top row buttons in the bottom sheet are perfectly aligned and sized.
- [x] **Matrix:** Verified all 3 modes show their respective telemetry columns as requested.
- [x] **HUD Rudder:** The black-on-white indicator is highly visible on both light and dark map areas.
