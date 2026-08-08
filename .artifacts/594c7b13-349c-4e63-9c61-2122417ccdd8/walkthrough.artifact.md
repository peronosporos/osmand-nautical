# Walkthrough - Nautical Plugin UX & Bug Fixes

I have successfully addressed the UX flaws and bugs in the Nautical plugin. This update improves connection stability, UI layout arbitration, and safety-critical feedback.

## 1. Improved Connectivity & Setup

- **Connection Debounce**: Implemented a 10-second debounce for "Connected/Disconnected" Toast notifications in `NauticalPlugin.kt` to prevent UI obstruction during unstable connections.
- **SSL Flexibility**: Relaxed `NauticalTrustManager` to fully bypass certificate verification when "Trust All" is enabled, supporting legacy boat hardware with expired or self-signed certificates.
- **Wizard Enhancements**:
    - Functionalized the "Auto-Discover Server" button in the Setup Wizard.
    - Added a "Test Connection" button to validate server reachability and credentials before finishing onboarding.

## 2. HUD & Layout Arbitration

- **Spatial Arbitration**: Updated `NauticalHudManager.kt` to accurately detect standard map widgets (Next Turn, Street Name) and adjust the vertical offset of nautical headers, eliminating overlaps.
- **Compact HUD**: Implemented "Compact Mode" in the HUD. When more than one safety header (e.g., MOB + Navtex) is active, the headers automatically shrink to preserve map visibility.
- **Touch Guard Feedback**: Added a persistent lock icon (`ScreenTouchLockHudView`) that appears when the map is locked during heavy weather or maneuvers.

## 3. Better Technical Interaction

- **Intuitive Stats**: In the Technical Stats screen, users can now tap anywhere on a data cell (icon, label, or value) to trigger the edit dialog.
- **Material 3 Alignment**: Refactored the technical stats grid to use standard theme attributes and consistent text styles.

## 4. Safety & "Synergy" Features

- **Bi-Directional Anchor Sync**: Dropping or moving an anchor locally now enqueues a sync request to the Signal K server, keeping the entire crew updated.
- **Buddy Management**: Added a quick "Add/Remove Buddy" button directly inside the AIS Vessel Details dialog.
- **Urgent Staleness**: Changed the passive "n/a" display for safety-critical fields (Depth, XTE) to a bold "TIMEOUT" warning when data is stale for more than 10 seconds.
- **Maneuver Recovery**: Added visual banners and TTS announcements when an automated maneuver (like a Tack) enters "Recovery Mode" after an abort.

## 5. Stability & Debt

- **Safe Migration**: Fixed a potential data loss bug in `SignalKEngine.kt` where legacy buffer files were deleted even if the migration failed.
- **Command Debounce**: Added a 500ms debounce to Autopilot toggle taps in `NauticalPilotWidget.kt` to prevent enqueuing conflicting commands on high-latency networks.
- **Localization**: Extracted over 15 hardcoded English strings from various engines and layers into `strings.xml`.
- **Clutter Reduction**: Removed redundant "Raster Charts" and "Replay Controls" from the map context menu, as they are already available in the main settings and "Configure Map" menu.
