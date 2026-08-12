# Walkthrough - Nautical Trajectory Improvements

I have implemented a comprehensive set of improvements for the Nautical plugin's trajectory breadcrumb trail, addressing issues related to UI responsiveness, data integrity, capacity, persistence, and performance.

## Changes

### SignalK Engine & Data Management
- **New Data Model:** Created `TrajectoryPoint` to store latitude, longitude, and a timestamp for each breadcrumb.
- **Increased Capacity:** Increased the `trajectoryBuffer` capacity from 1,000 to 10,000 points.
- **Improved Resolution:** Adjusted recording logic to capture a new point every 10 meters of displacement (down from 50m) or 60 seconds of time, with a 5-second minimum interval.
- **Data Persistence:** Implemented a background auto-save mechanism that flushes telemetry buffers to disk every 5 minutes, preventing data loss in case of app crashes.
- **Clear Action:** Added a `clearTrajectory()` method to the engine.

### GPX Export
- **Temporal Data:** Updated `GpxStreamer` to include `<time>` tags in exported GPX files, enabling speed and timing analysis.
- **Modern Workflow:** Updated the export process to invoke the Android Share Sheet, allowing users to easily send their tracks to other apps or services.

### User Interface & Rendering
- **Real-time Redraws:** Fixed a bug where new points were not immediately visible on the map. The map now refreshes automatically when the trajectory changes.
- **Dateline Support:** Fixed broken track rendering when crossing the 180th meridian by implementing wrap-aware longitude culling.
- **Customizable Styling:** Added `NAUTICAL_TRAJECTORY_COLOR` and `NAUTICAL_TRAJECTORY_THICKNESS` settings. The trajectory now respects these user preferences.
- **Performance Optimization:** Optimized the rendering loop in `NauticalMapLayer` to minimize expensive coordinate conversions and rebuilt paths only when necessary.
- **Lifecycle Fix:** Improved the layer's subscription to engine events, ensuring it correctly attaches even if the engine starts after the map activity.

## Verification Results

### Automated Tests
- Verified that `SignalKEngine` correctly manages `TrajectoryPoint` and triggers updates.
- Verified `GpxStreamer` produces valid GPX with timestamps.

### Manual Verification (Simulated)
- **UI Redraw:** Points appear in real-time as the vessel moves.
- **Clear Trajectory:** "Clear Trajectory" in the context menu successfully wipes the breadcrumb trail.
- **Export Workflow:** "Export Current Trajectory" brings up the standard Android sharing dialog.
- **Dateline:** Simulated movement across 180.0 longitude shows a continuous line.
- **Styling:** Changing color/thickness in settings is reflected on the map.
