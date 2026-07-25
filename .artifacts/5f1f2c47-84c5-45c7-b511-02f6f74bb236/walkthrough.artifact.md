# Walkthrough - NMEA Telemetry Logging and Replay Engine

I have implemented the NMEA Telemetry Logging and Replay Engine, enabling skippers to record and replay raw NMEA data.

## Key Components

### Replay & Logging Engine
- **[NmeaStreamRecorder.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/replay/NmeaStreamRecorder.kt)**:
    - Uses `okio` to stream raw NMEA sentences to disk.
    - Prepends each sentence with a UTC timestamp for accurate playback.
    - Files are saved as `.nmea.log` in the internal `nautical/replays` directory.
- **[NmeaPlaybackEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/replay/NmeaPlaybackEngine.kt)**:
    - Implements `NmeaClient` to act as a virtual hardware source.
    - Parses recorded timestamps to emulate real-time data delivery.
    - Supports playback speeds of 1x, 2x, and 5x.

### UI & Integration
- **[NmeaPlaybackControlBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/replay/NmeaPlaybackControlBottomSheet.kt)**:
    - Provides a timeline view with play/pause/stop and speed controls.
    - Includes a file selection dialog to choose from past recordings.
- **[DirectNmeaMultiplexer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/multiplexer/DirectNmeaMultiplexer.kt)**:
    - Integrated hooks to capture live data streams into the recorder.
- **Context Menu Integration**:
    - Added "NMEA Replay Engine" and "Record/Stop NMEA" options to the Nautical map context menu.

## Verification Results

### Automated Tests
- The replay engine correctly calculates deltas between timestamps and respects the speed multiplier.
- `okio` buffered sinks ensure minimal impact on app performance during recording.

### Manual Verification
- Recording can be toggled from the map context menu.
- Replay bottom sheet allows selecting recorded logs and observing the nautical dashboard updating in real-time.
- Playback speed and timeline seeking work as intended.
