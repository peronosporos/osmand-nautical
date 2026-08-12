# Walkthrough - Nautical Racing Functionality Enhancements

I have completed a comprehensive overhaul of the nautical plugin's racing features, addressing 17 bugs and improvements across the backend, tactical engine, and UI.

## Changes Made

### Backend & Logic
- **Signal K Racing Timer**: [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt) now correctly parses `performance.racing.timer` from Signal K updates.
- **Great-Circle VMG**: Optimized VMG calculation to account for great-circle bearings, ensuring better accuracy for long-distance tactical routing.
- **Polar Engine Robustness**:
    - Fixed division-by-zero vulnerabilities in Bilinear Interpolation.
    - Added unit detection for Polar CSV files (m/s vs Knots).
    - Improved Signal K JSON matrix order detection.
    - Added strict dimension validation for polar profiles.

### Tactical Start Line
- **Spherical Geometry**: Replaced planar Start Line distance calculations with spherical projection in [TacticalStartManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/TacticalStartManager.kt), fixing errors at high latitudes.
- **Time to Burn (TTB)**: Completely rewrote the TTB logic to account for the race countdown and the velocity component perpendicular to the line.

### Polar Configuration Wizard
- **High-Precision Logging**: Changed `PolarCell` sample counts to `Double` to prevent precision loss during weighted bilinear recording.
- **Safety Filters**: The Wizard now automatically pauses recording if the engine is running or if sensors report low calibration confidence, preventing data pollution.

### UI & HUD Improvements
- **Themed Racing HUD**: [StartLineHudHeader.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/StartLineHudHeader.kt) now uses semantic theme colors instead of hardcoded hex values.
- **Target VMG Widget**: [TargetVmgWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/TargetVmgWidget.kt) now displays tactical *Target VMG* derived from the polar diagram when available, and respects user unit settings.
- **Negative Timer Support**: Both the HUD and widgets now correctly format negative racing timer values (e.g., `-00:05` for late starts).
- **High-Frequency Updates**: [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt) now overrides power-saving throttles to provide 10Hz UI updates during the final race countdown.
- **Optimal Tack Indicator**: Introduced [TacticsHudHeader.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/TacticsHudHeader.kt), which provides a visual prompt when the boat reaches a layline.

## Verification Results

### Logic & Math
- Verified `PolarDiagram` bilinear math with epsilon protection.
- Verified `TacticalStartManager` TTB formula correctly subtracts TTL from the countdown.

### UI & UX
- Verified theme consistency in `StartLineHudHeader`.
- Verified `TargetVmgWidget` correctly formats knots/metric units.
- Verified new `nautical_tactics_hud.xml` layout and associated strings.

> [!TIP]
> To test the new **Optimal Tack** feature, set a waypoint and sail towards a layline. A prompt will appear in the top HUD when you are within 50m of the layline intersection.

> [!WARNING]
> Ensure your Signal K server is publishing `performance.racing.timer` to see the live countdown in the HUD.
