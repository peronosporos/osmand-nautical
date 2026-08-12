# Walkthrough - Nautical Plugin Marine Widgets & SignalKEngine Overhaul

Completed a comprehensive overhaul of the nautical telemetry widgets and the underlying Signal K engine to fix critical calculation errors, improve multi-instance support, and clean up redundant code.

## Key Changes

### 1. Backend & Logic Fixes
- **SignalKEngine**:
    - Standardized `revolutions` to Hz and `engineHours` to seconds.
    - Restored primary instance (`"0"`) field updates to maintain compatibility with safety services (docking safety, logbook).
    - **Generic History Access**: Introduced `getHistory(path: String)` and removed ~40 redundant hardcoded history methods.
- **SignalKUnitConverter**:
    - Fixed temperature offsets (`KELVIN_OFFSET_CELSIUS` = -273.15).
    - Centralized conversion coefficients for RPM, Knots, and Pressure.
    - Integrated `OsmAndFormatter` for native-feeling time-based data (TTW, ETA).

### 2. Telemetry Widget Improvements
- **MarineTextWidget**:
    - Refactored to a granular, flow-based update model for improved performance.
    - **Multi-Instance Support**: Added `customId` parsing to support specific engine/battery/tank instances (e.g., Starboard vs Port engine).
    - **Styling**: Fixed background reset conflict during integrity state changes.
    - **Accessibility**: Added localized descriptions for all nautical widget types.
- **NauticalGraphWidget & Bottom Sheets**:
    - Implemented instance-aware history graphing.
    - Added a 500ms update throttle to graphs to prevent main-thread saturation.
    - Fixed Celsius scaling and unit labels.

## Verification Results

### Automated Tests
- Updated `SignalKUnitConverterTest.kt` with new test cases for RPM and Temperature conversions.

### Build & Logic Stability
- **Path Synchronization**: All migrated calls were verified against `SignalKPaths` constants.
- **Regression Check**: Verified that safety-critical services (Autopilot, Docking Safety) and tactical displays (Laylines) remain fully functional.
- **UI Consistency**: Verified that stale data correctly shows strike-through and pulse RED styling without erasing custom widget backgrounds.
