# Implementation Plan - Direct Bluetooth and TCP NMEA Fallback Driver

Implement a Direct NMEA connection system for the OsmAnd Nautical plugin, allowing data ingestion from raw NMEA 0183 hardware via Bluetooth or TCP when Signal K is unavailable.

## User Review Required

> [!IMPORTANT]
> - I will extend the `LivePerformanceData` model and `SailingDataAggregator` to support SOG, COG, and Depth, as these are provided by the requested NMEA sentences ($GPRMC, $SDDBT).
> - Bluetooth connection requires `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN` permissions on Android 12+. I assume these are already handled by the app's permission manifest or will be requested by the system.

## Proposed Changes

### [Resources]
#### [MODIFY] [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml)
- Add NMEA connection and status strings.

### [Nautical Models & Aggregator]
#### [MODIFY] [SignalKModels.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/network/SignalKModels.kt)
- Add SOG, COG, and Depth fields to `LivePerformanceData`.
- Add corresponding Signal K paths to `LivePerformanceData.companion`.

#### [MODIFY] [SailingDataAggregator.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/service/SailingDataAggregator.kt)
- Update `handleDelta` to process SOG, COG, and Depth.

### [NMEA Parser]
#### [NEW] [NmeaSentenceParser.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/parser/NmeaSentenceParser.kt)
- Lightweight parser for `$GPRMC`, `$WIMWV`, and `$SDDBT`.
- Converts NMEA values to `DeltaMessage` updates.

### [NMEA Connections]
#### [NEW] [NmeaClient.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/connection/NmeaClient.kt)
- Interface for NMEA data sources.

#### [NEW] [BluetoothNmeaClient.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/connection/BluetoothNmeaClient.kt)
- Implementation for Bluetooth SPP connections.

#### [NEW] [TcpNmeaClient.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/connection/TcpNmeaClient.kt)
- Implementation for raw TCP socket connections.

### [Multiplexer Bridge]
#### [NEW] [DirectNmeaMultiplexer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/multiplexer/DirectNmeaMultiplexer.kt)
- Orchestrates connection lifecycle and routes data to `SailingDataAggregator`.

## Verification Plan

### Automated Tests
- Create unit tests for `NmeaSentenceParser` to verify correct mapping of NMEA sentences to `DeltaMessage`.
- Mock socket/Bluetooth streams to verify reconnection logic in `BluetoothNmeaClient` and `TcpNmeaClient`.

### Manual Verification
- Deploy to device and test with a TCP NMEA simulator (e.g., NMEA Simulator app or `nc -l -p 10110`).
- Verify that sailing widgets update when NMEA data is flowing.
