# Walkthrough: Nautical Pilot Refactoring

I have optimized the Nautical Pilot Bottom Sheet to improve domain alignment, safety, and UI clarity.

## Changes Made

### 1. Reactive Authentication Logic
Moved from pre-emptive warnings to reactive ones. The app will now only display an "Authentication Required" alert if the Signal K server actually returns a `401 Unauthorized` status. This prevents confusing users with red alerts when connecting to unauthenticated LAN servers.
- Added `UNAUTHORIZED` state to `ConnectionStatus`.
- Updated `SignalKConnection` and its OkHttp implementation to detect and propagate 401 errors.
- Updated `NauticalPilotBottomSheet` to show the warning only when this status is active.

### 2. Telemetry UI Fixes
Fixed the text alignment in the telemetry grid.
- Added `android:gravity="center"` to all telemetry value TextViews in `nautical_pilot_bottom_sheet.xml`.
- Values are now perfectly centered under their respective icons and labels.

### 3. Helm Control Safety (Control Reorganization)
Following commercial MFD standards (Raymarine, B&G), I have removed PID tuning sliders and Sea State controls from the primary helm sheet to prevent accidental adjustments in heavy seas.
- Moved **Rudder Gain**, **Counter Rudder**, **Auto Trim**, and **Sea State** controls to the **Advanced Settings** sheet.
- These controls are now protected by the `Safety Lock` toggle in the Advanced sheet.
- Updated `NauticalAdvancedSettingsBottomSheet.kt` to include full bindings and logic for Sea State and Auto Sea State.

### 4. Domain Focus Cleanup
Removed the **Digital Switching** section from the Autopilot sheet. Helm controls are now focused purely on vessel steering, reducing the sheet height and visual clutter.

## Verification Results

### UI Alignment
- [x] Verified `txt_value_1_1` through `txt_value_2_3` have `gravity="center"`.

### Advanced Settings
- [x] Verified `bottom_sheet_nautical_advanced.xml` contains the new Sea State section.
- [x] Verified `NauticalAdvancedSettingsBottomSheet` logic correctly initializes and saves Sea State preferences.
- [x] Verified Sea State controls respect the recursive `setEnabled` lock.

### Auth Flow
- [x] Verified `ConnectionStatus.UNAUTHORIZED` handling in `NauticalPlugin` and `NauticalPilotBottomSheet`.
