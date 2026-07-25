# Walkthrough - Hardware Data Ingestion Subsystem Hardening

I have completed the hardening of the OsmAnd Nautical plugin's data ingestion layer, focusing on physical hardware reliability (USB/Serial, TCP/UDP) and data integrity (Checksums, Source Priority).

## Changes

### 1. USB Serial Support
- **[NEW] [UsbNmeaClient.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/connection/UsbNmeaClient.kt)**: Implemented a native Android USB Serial client using `UsbManager`. It supports common marine baud rates (4800, 38400) and CDC-ACM chipsets.
- **[NEW] [UsbConnectionReceiver.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/connection/UsbConnectionReceiver.kt)**: Added a broadcast receiver to detect OTG cable plug/unplug events dynamically.
- **[MODIFY] [AndroidManifest.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/AndroidManifest.xml)**: Registered the USB host feature and intent filters for device attachment.

### 2. Stream Robustness
- **[MODIFY] [AisMessageListener.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd-shared/src/commonMain/kotlin/net/osmand/shared/aistracker/AisMessageListener.kt)**: Implemented a `StringBuilder` line buffer for UDP listeners. This ensures that NMEA sentences fragmented across multiple network packets are correctly reassembled before parsing.
- **[MODIFY] [NmeaSentenceParser.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/parser/NmeaSentenceParser.kt)**: Added XOR checksum (`*HH`) validation. Corrupted serial data is now discarded immediately, preventing invalid state updates. Added support for `!` prefixed sentences (AIS).

### 3. Multi-Source Conflict Resolution
- **[MODIFY] [SailingDataAggregator.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/service/SailingDataAggregator.kt)**: Implemented a source priority system. High-priority hardware data (`direct-nmea`) will now override fresh lower-priority data (e.g., internal tablet GPS), while stale hardware data is automatically ignored.
- **[MODIFY] [DirectNmeaMultiplexer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/multiplexer/DirectNmeaMultiplexer.kt)**: Updated to support multiple concurrent `NmeaClient` instances (e.g., a boat using both a USB AIS receiver and a Bluetooth wind sensor).

## Verification Results

### Automated Tests
- **[NmeaSentenceParserTest.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/test/java/net/osmand/plus/plugins/nautical/nmea/parser/NmeaSentenceParserTest.kt)**: Verified that corrupted checksums result in `null` deltas and that `!` sentences are recognized.
- **[SailingDataAggregatorTest.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/test/java/net/osmand/plus/plugins/nautical/service/SailingDataAggregatorTest.kt)**: Verified that `internal` GPS data does not overwrite fresh `direct-nmea` data.

### Manual Verification
> [!NOTE]
> Physical USB OTG testing requires a device with a connected serial bridge.
> 1. Plug in a USB-to-Serial adapter (FTDI/CH340).
> 2. System should prompt for USB permission for OsmAnd.
> 3. Upon acceptance, NMEA sentences from the adapter will flow into the `SailingDataAggregator`.
> 4. Unplugging the cable will trigger a clean teardown of the ingestion thread without crashing the app.
