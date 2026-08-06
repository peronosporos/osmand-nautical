# Implementation Plan - Phase 8.0U: Dirty Data, Sanitization & Safe Math

This phase focuses on hardening the Nautical plugin against malformed data, downstream math crashes, and potential memory issues due to unbounded queues.

## Proposed Changes

### 1. Ingestion Validation & Range Filtering

Implement strict bounds-checking for all incoming data to prevent polluting the `MarineState` with impossible values.

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Add bounds checking in `parseSelfValue` and `parseTelemetryValue`.
- Ranges:
    - Latitude: `[-90.0..90.0]`
    - Longitude: `[-180.0..180.0]`
    - Wind Speed: `[0.0..150.0]` knots (converted to m/s)
    - Depth: `[0.0..11000.0]` meters
    - SOG/STW: `[0.0..100.0]` knots (converted to m/s)
- Add validation for `vesselMmsi` (valid MMSI range).

#### [MODIFY] [NmeaSentenceParser.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/parser/NmeaSentenceParser.kt)
- Add bounds checking for parsed RMC, MWV, DBT, DBS fields.
- Reject sentences with mathematically impossible values.

#### [NEW] [AisDecoder.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/parser/AisDecoder.kt)
- Implement a robust AIS NMEA sentence decoder (AIVDM/AIVDO).
- Include strict range filtering for AIS target data (position, speed, heading).
- Integrate with `NmeaSentenceParser` or `DirectNmeaMultiplexer`.

#### [MODIFY] [MarineState.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/MarineState.kt)
- Add a `validate()` helper or constants for safe ranges.

### 2. Math Bounds & NaN Guards

Ensure all calculations are resilient to division by zero, NaN, or Infinity results.

#### [MODIFY] [LaylineMathEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/laylines/engine/LaylineMathEngine.kt)
- Wrap all vector and intersection calculations in `isNaN()`/`isInfinite()` checks.
- Fallback to safe defaults (e.g., `null` for intersections, `0.0` for headings) when calculations fail.

#### [MODIFY] [HeadingArcView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/HeadingArcView.kt)
- Add NaN guards in `onDraw` and `calculateError`.
- Ensure `radius` and coordinates are valid before drawing.

#### [MODIFY] [RudderView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/RudderView.kt)
- Add NaN/Infinity checks for `rudderAngle` and animation values.

#### [MODIFY] [NauticalGraphView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalGraphView.kt)
- Ensure `min`/`max` and `range` are finite and non-zero before scaling.
- Sanitize input data points.

#### [MODIFY] [SafetyCorridorChecker.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/engine/SafetyCorridorChecker.kt)
- Add guards for invalid coordinates or degenerate geometries.

### 3. Bounded Input Buffers

Prevent OOM by ensuring all incoming data queues are bounded and implement rate-limiting.

#### [MODIFY] [DirectNmeaMultiplexer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/multiplexer/DirectNmeaMultiplexer.kt)
- Use `CircularBuffer` for internal sentence queuing if `Channel` is not sufficient or as a secondary safety layer.
- Implement rate-limiting (e.g., max X sentences per second per client) to prevent flooding.

#### [MODIFY] [CircularBuffer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/CircularBuffer.kt)
- Ensure it is thread-safe and performs efficiently under high load. (Current implementation looks okay, but will review for optimizations).

## Verification Plan

### Automated Tests
- Create unit tests for `SignalKEngine` and `NmeaSentenceParser` with malformed/out-of-range inputs.
- Create unit tests for `LaylineMathEngine` with edge case inputs (e.g., identical points, zero speed).
- Verify `CircularBuffer` behavior under overflow.

### Manual Verification
- Deploy to device/emulator.
- Feed malformed NMEA sentences via a mock source and ensure no crashes occur.
- Verify UI components (HeadingArc, RudderView) still render correctly with valid data.
- Check Logcat for "Dropped malformed data" warnings.
