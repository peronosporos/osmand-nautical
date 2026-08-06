# Walkthrough - Targeted UX & Persistence Fixes

This walkthrough summarizes the implementation of targeted UX improvements, persistence fixes, and functional enhancements in the Nautical plugin.

## Changes

### 1. Persistence Fixes
- Added `.makeGlobal()` to `NAUTICAL_SERVER_IP` and `NAUTICAL_SERVER_PORT` in `OsmandSettings.java`. This ensures that the Signal K server address and port are correctly persisted across app restarts and remain consistent across different application profiles.

### 2. MOB UX Improvements
- **Typography**: Updated string resources (`mob_alarm_title`, `mob_btn_cancel`, `nautical_mob_label`) to use standard sentence casing, removing all-caps text for a cleaner and more professional look.
- **Iconography**: Added a high-visibility emergency icon (`ic_action_alert`) to the `MobEmergencyHeaderView` (MOB HUD).
- **Interaction**: Changed the MOB activation flow. It now requires a **single Long-Press** on the MOB widget to drop a marker and start tracking, reducing friction and preventing accidental triggers. Accidental single taps are no longer used for activation.

### 3. Widget Visibility & Theming
- Updated all nautical widgets to use the theme-aware `R.color.map_widget_icon_color` for their icons. This ensures they remain visible against the map background and adapt to the current theme.
- Verified that all widgets inherit correctly from `SimpleWidget` or `MapWidget`, ensuring they use standard OsmAnd backgrounds and framing.

### 4. Autopilot Bottom Sheet Logic
- **Always Accessible**: The Autopilot Bottom Sheet now always opens when the widget is clicked, regardless of whether an auth token is configured.
- **In-Sheet Warning**: Added an `auth_warning` TextView inside the bottom sheet that appears if a secure connection is used but the token is missing.
- **Contextual Feedback**: Token validation is now performed when a user attempts to change a mode or heading. A Toast message is triggered only at the moment of the attempted action, providing immediate and relevant feedback without blocking access to telemetry data.

## Verification Results

### Automated Tests
- Syntax and logic verification performed via code inspection. All changes adhere to the project's architecture and coding standards.

### Manual Verification (Simulated/Inspected)
- Checked `OsmandSettings.java` for correct preference instantiation.
- Verified `strings.xml` for sentence casing.
- Inspected `mob_emergency_hud.xml` for the new icon and layout.
- Verified `NauticalMobWidget.kt` for long-press activation logic.
- Checked `NauticalPilotWidget.kt` and `NauticalPilotBottomSheet.kt` for improved bottom sheet accessibility and token validation.
- Confirmed theme-aware icon colors in all nautical widgets.
