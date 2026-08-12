# Implementation Plan - Nautical Trajectory Improvements

This plan addresses 12 identified issues (bugs, performance, and features) related to the Nautical plugin's trajectory (breadcrumb) functionality.

## Proposed Changes

### [Component] SignalK Engine & Data Management
Goal: Improve data capacity, persistence, and resolution.

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Define a `TrajectoryPoint` data class: `data class TrajectoryPoint(val lat: Double, val lon: Double, val time: Long)`.
- Change `trajectoryBuffer` to `CircularBuffer<TrajectoryPoint>` and increase capacity to 10,000.
- Update `addTrajectoryPoint(lat, lon)` to record current timestamp and use `TrajectoryPoint`.
- Adjust `addTrajectoryPoint` logic:
    - Minimum interval: 5s.
    - Record if: displacement > 10m (was 50m) OR time gap > 60s.
- Add an `autoSaveJob` to periodically call `saveBuffersToDisk(app)` (e.g., every 5 minutes).
- Add `clearTrajectory()` method.

### [Component] GPX Export
Goal: Include temporal data and improve user workflow.

#### [MODIFY] [GpxStreamer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/GpxStreamer.kt)
- Update `exportTrajectory` to accept `List<TrajectoryPoint>` and set `WptPt.time` for each point.

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Update `exportCurrentTrajectory()` to use the Android Share Sheet via `SharedUtil.shareFile` after export.
- Add `clearTrajectory()` action to the map context menu.

### [Component] Rendering & UI
Goal: Fix redraw issues, dateline handling, and performance.

#### [MODIFY] [OsmandSettings.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/settings/backend/OsmandSettings.java)
- Add `NAUTICAL_TRAJECTORY_COLOR` (default MAGENTA).
- Add `NAUTICAL_TRAJECTORY_THICKNESS` (default 10f).

#### [MODIFY] [NauticalMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalMapLayer.kt)
- **Item 1: Real-time UI Redraw Failure**: In `invalidateTrajectory()`, add `view.refreshMap()`.
- **Item 2: Broken Dateline Handling**: Implement wrap-aware longitude checking in `onDraw`.
- **Item 6: Hardcoded Styling**: Read color and thickness from `OsmandSettings`.
- **Item 7 & 8: Performance**:
    - Optimize coordinate conversion in `onDraw` by calculating `x, y` once and using them for both culling and path building.
    - Ensure path rebuilding is efficient.
- **Item 12: Lifecycle**: Improve engine subscription in `initLayer` to handle cases where the engine is null initially (e.g., using a flow or checking periodically).

## Verification Plan

### Automated Tests
- I will look for existing tests for `SignalKEngine` and `NauticalMapLayer` and add/update them if possible.
- Run `./gradlew :OsmAnd:testDebugUnitTest --tests net.osmand.plus.plugins.nautical.*` (if tests exist).

### Manual Verification
- Deploy to a device/emulator.
- Enable trajectory.
- Move simulated vessel and verify real-time updates.
- Verify trajectory persists across app restarts.
- Export trajectory and verify `<time>` tags in the GPX file.
- Change trajectory color/thickness in settings and verify on map.
- Test crossing the 180th meridian (e.g., around Fiji or Aleutian Islands) in simulation.
- Verify "Clear Trajectory" action works.
- Verify "Export" invokes the Share Sheet.
