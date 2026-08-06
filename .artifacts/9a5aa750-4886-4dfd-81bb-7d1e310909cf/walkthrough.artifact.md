# Walkthrough - Comprehensive Nautical Plugin Optimization

I have completed the full optimization of the Nautical plugin, addressing Signal K v2 integration, HUD arbitration, telemetry expansion, and advanced configuration.

## Key Changes

### Signal K v2 Modernization
- **Course API**: Updated `SignalKRestService` and `SignalKEngine` to support the v2 Course API (`/navigation/course`), enabling robust server-side route and waypoint reconciliation.
- **Data Models**: Added `SignalKCourse` and related data classes for seamless API interaction.
- **REST State Sync**: Improved background state refresh to include Course object reconciliation.

### Telemetry & Visualization
- **New Widgets**: Registered and implemented logic for:
    - **AC System Summary**: Real-time inverter/charger status.
    - **Rigging Loads**: Aggregate view of vessel tension data.
    - **Notifications**: Live count of active Signal K alerts.
- **Reliability Fallback**: Enhanced the STW (Speed Through Water) widget to automatically fall back to COG (Course Over Ground) if the paddlewheel is detected as fouled/unreliable.
- **Integrity States**: Refined the high-visibility ALARM state (flashing red/white) and STALE state (yellow tint) in `MarineTextWidget`.

### Safety & HUD Management
- **Spatial Arbitration**: Re-implemented `NauticalHudManager.updateLayout` using robust screen-coordinate detection. The nautical HUD now dynamically shifts to avoid overlapping standard OsmAnd widgets like "Next Turn" and the "Top Bar".
- **Safety Arbitration**: Introduced `SafetyStateArbitrator` to manage cross-component safety logic, ensuring high-priority emergencies (MOB) preempt lower-severity warnings in both audio and HUD space.

### Technical Configuration
- **Advanced UI**: Overhauled `NauticalAdvancedSettingsFragment` with categorized sections for:
    - **Telemetry Tuning**: PID gains and filter sensitivities.
    - **EMA Smoothing**: Individual control over Alpha values for Heading, Wind, and Depth.
    - **Reliability**: Configurable thresholds for STW vs. SOG drift detection.
    - **Connectivity**: Network timeouts and watchdog intervals.

### Technical Integrity & Reactive Wiring
- **Full Reactive Chain**: Wired all remaining technical fields (`Magnetic Variation`, `Yaw`, `CPA`, `TCPA`) to specific StateFlows in `SignalKDataBroker`. This ensures that individual widgets update as soon as their specific data arrives, without waiting for a full vessel state refresh.
- **Course Object Handling**: Completed the `processCourseObject` and optimized WebSocket `navigation.course` implementation in `SignalKEngine` to handle server-defined `arrivalRadius` and `activeRoute` parameters.
- **Zero-Warning Implementation**: Resolved all compiler warnings related to unused properties, legacy overrides, and expression clarity, ensuring a high-integrity codebase.

## Verification Results

### Automated Tests
- **SignalKEngine Reconciliation**: Verified that Course objects are correctly parsed and update the `serverNextPoint` in `MarineState`.
- **Data Broker Staleness**: Confirmed that the staleness monitoring job correctly flags paths when updates cease beyond the watchdog threshold.

### Manual Verification
- **HUD Displacement**: Confirmed that activating standard widgets correctly pushes the Nautical HUD container down without obscuring standard map controls.
- **Fallback Logic**: Simulated fouled STW (STW < 0.1, SOG > 1.0) and verified the STW widget correctly displays "(COG)" fallback data.
