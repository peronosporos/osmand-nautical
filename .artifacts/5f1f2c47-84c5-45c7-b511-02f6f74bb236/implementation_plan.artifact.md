# Implementation Plan - NMEA Telemetry Logging and Replay Engine

Implement a system to record and replay raw NMEA telemetry data, allowing for post-trip analysis and tactical simulation.

## User Review Required

> [!IMPORTANT]
> The playback engine will simulate real-time data delivery by delaying sentence emission based on recorded timestamps. High-speed playback (e.g., 5x) may increase CPU load due to rapid parsing.

## Proposed Changes

### NMEA Replay & Logging
`net.osmand.plus.plugins.nautical.replay`

#### [NEW] [NmeaStreamRecorder.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/replay/NmeaStreamRecorder.kt)
- Background utility to record raw NMEA sentences to `.nmea.log` files.
- Uses `okio` for buffered, low-overhead I/O.
- Formats each line with a UTC timestamp: `[timestamp] $sentence`.

#### [NEW] [NmeaPlaybackEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/replay/NmeaPlaybackEngine.kt)
- Implements `NmeaClient` to plug into `DirectNmeaMultiplexer`.
- Emulates real-time timing by calculating deltas between recorded timestamps.
- Supports `play`, `pause`, `seek`, and variable playback speeds (1x, 2x, 5x).

#### [NEW] [NmeaPlaybackControlBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/replay/NmeaPlaybackControlBottomSheet.kt)
- Bottom sheet UI for managing playback state and file selection.
- Timeline progress bar and speed controls.

#### [MODIFY] [DirectNmeaMultiplexer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/multiplexer/DirectNmeaMultiplexer.kt)
- Add hooks to allow `NmeaStreamRecorder` to capture incoming live data.

#### [MODIFY] [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml)
- Add UI strings for recording and playback controls.

## Verification Plan

### Automated Tests
- Unit tests for `NmeaPlaybackEngine` to verify timing logic and speed multipliers.
- Test `NmeaStreamRecorder` for correct file formatting and `okio` sink handling.

### Manual Verification
- Start a live NMEA recording.
- Verify `.nmea.log` file is created in `nautical/replays/`.
- Load the recorded file into the Playback Engine.
- Verify the app's nautical dashboard updates as if receiving live data.
- Test Seek and Speed controls.
