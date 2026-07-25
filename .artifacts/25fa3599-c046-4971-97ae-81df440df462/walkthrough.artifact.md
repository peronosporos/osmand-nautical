# Walkthrough - Direct Bluetooth and TCP NMEA Fallback Driver

Implemented a robust fallback system for direct NMEA 0183 data ingestion from marine hardware via Bluetooth Serial (SPP) and TCP Network Streams.

## Changes Made

### 1. Resources & Models
- Added localized strings for direct NMEA connection management in [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml).
- Extended [LivePerformanceData](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/network/SignalKModels.kt) with SOG (Speed Over Ground), COG (Course Over Ground), and Depth below transducer.
- Updated [SailingDataAggregator](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/service/SailingDataAggregator.kt) to process these new fields and reset them if data becomes stale.

### 2. NMEA Parsing
- Created [NmeaSentenceParser](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/parser/NmeaSentenceParser.kt), a lightweight parser for:
    - `$GPRMC` / `$GNRMC`: Extracts SOG and COG.
    - `$WIMWV` / `$IIMWV`: Extracts True Wind Speed and Angle.
    - `$SDDBT` / `$IIDBS`: Extracts Water Depth.
- Mapped these sentences to the existing `DeltaMessage` flow, allowing seamless integration with the Signal K-based aggregator.

### 3. Direct Connection Clients
- Implemented [TcpNmeaClient](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/connection/TcpNmeaClient.kt) for raw TCP socket connections with exponential backoff reconnection logic.
- Implemented [BluetoothNmeaClient](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/connection/BluetoothNmeaClient.kt) for Bluetooth SPP (RFCOMM) connections to marine hardware.

### 4. Multiplexer Bridge
- Created [DirectNmeaMultiplexer](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/multiplexer/DirectNmeaMultiplexer.kt) to orchestrate the lifecycle of NMEA connections and route parsed updates to the aggregator.

## Verification Results

### Automated Tests
- Created [NmeaSentenceParserTest](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/test/java/net/osmand/plus/plugins/nautical/nmea/parser/NmeaSentenceParserTest.kt) which verifies:
    - Correct parsing and unit conversion (knots to m/s, degrees to radians) for RMC and MWV sentences.
    - Correct depth extraction from DBT sentences.
    - Graceful handling of invalid or unsupported sentences.

### Manual Verification Path
- The system is designed to be triggered when Signal K is unavailable.
- Connection state is exposed via `isConnected` flows in both clients and the multiplexer, which can be bound to UI indicators.
- Logging via `PlatformUtil` ensures that connection failures or malformed sentences can be debugged in the field.
