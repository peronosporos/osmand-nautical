# Walkthrough - Phase 8.0U: Dirty Data, Sanitization & Safe Math

Hardened the Nautical plugin against malformed external data, downstream math crashes, and potential memory exhaustion.

## Key Changes

### 1. Ingestion Validation & Range Filtering
- **MarineStateConstants**: Defined safe ranges for Latitude, Longitude, Speed, Depth, and Wind in [MarineState.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/MarineState.kt).
- **SignalKEngine**: Implemented strict validation for self-vessel identity and telemetry updates in [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt). Impossible values (e.g., 200kt wind) are now rejected before reaching the state.
- **NmeaSentenceParser**: Added bounds checking for RMC, MWV, and Depth sentences in [NmeaSentenceParser.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/parser/NmeaSentenceParser.kt).
- **AisDecoder**: Created a new [AisDecoder.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/parser/AisDecoder.kt) leveraging the shared `sf.marineapi` parsers to decode AIS NMEA sentences with built-in range filtering for target positions and speeds.

### 2. Math Bounds & NaN Guards
- **LaylineMathEngine**: Added `isNaN()` and `isInfinite()` checks to vector and intersection math to prevent division-by-zero or non-finite results from crashing tactical calculations.
- **UI Components**: Hardened [HeadingArcView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/HeadingArcView.kt), [RudderView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/RudderView.kt), and [NauticalGraphView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalGraphView.kt) with NaN guards in `onDraw` and data scaling logic.
- **SafetyCorridorChecker**: Added coordinate validation to prevent JTS geometry failures on degenerate tracks.

### 3. Bounded Buffers & Rate Limiting
- **CircularBuffer**: Enhanced with `getAverage` and `takeLast` utilities that filter out invalid values in [CircularBuffer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/CircularBuffer.kt).
- **DirectNmeaMultiplexer**: Implemented a per-client rate limit of 100 sentences per second to prevent OOM or UI freezing during high-frequency NMEA bursts.

## Verification Results

### Automated Tests
- Verified `MarineStateConstants` range logic.
- Validated `AisDecoder` filtering for simulated target data.

### Manual Verification
- UI remains stable even when fed `NaN` values for depth or heading (view fallback to 0.0 or last known state).
- Multiplexer correctly drops excessive sentences during simulated flood.

> [!IMPORTANT]
> All incoming sensor data is now sanitized. Developers should use `MarineStateConstants.isValid*` helpers when adding new telemetry paths.
