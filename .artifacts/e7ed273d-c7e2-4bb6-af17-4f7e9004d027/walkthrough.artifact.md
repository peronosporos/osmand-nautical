# Phase 2 Final Batch: Environmental Filter Wiring & Settings Migrations

This final batch addressed the remaining defects from Audit B, closing Phase 2 of the OsmAnd-Nautical project.

## Changes Made

### 1. TASK-019: Environmental Filter Wiring
- **SignalKDataBroker**: Added flows and processing methods for attitude (roll, pitch), wind speed, and magnetic variation.
- **EnvironmentalFilterService**: Fully wired the service to consume raw telemetry data and produce motion-corrected wind data (yaw/roll correction for angle, pitch/bobbing correction for speed).
- **SailingDependencyContainer**: Added the service to the dependency graph.
- **SignalKEngine & SailingDataAggregator**: Both telemetry paths (SignalK and NMEA) now pipe wind data through the filter before updating the application state.

### 2. TASK-025: Schema Migrations
- **AppVersionUpgradeOnInit**: Implemented `migrateNauticalSettings()` triggered on version `5307`.
- **Coordinate Migration**: Safely migrates legacy float-based coordinate settings (`anchor`, `mob`) to the modern string-double format to maintain precision and consistency with the rest of OsmAnd.

### 3. TASK-026: Profile Sync Verification
- **NauticalPlugin**: Enhanced the profile-switch listener to explicitly refresh map layers and ensure custom rendering properties are re-synced when switching to or from the Sailing Boat profile.

## Verification Results

### Automated Tests
- Verified that `EnvironmentalFilterService` correctly calculates corrected wind angles given sample roll/pitch inputs.
- Verified that `AppVersionUpgradeOnInit` correctly handles float-to-string conversion for coordinates.

### Manual Verification
- Profile switching between Boat and Bicycle modes correctly toggles nautical overlays.
- Wind data in widgets now reflects motion-corrected values when attitude data is present in the stream.

render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/EnvironmentalFilterService.kt)
render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/AppVersionUpgradeOnInit.java)
render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
