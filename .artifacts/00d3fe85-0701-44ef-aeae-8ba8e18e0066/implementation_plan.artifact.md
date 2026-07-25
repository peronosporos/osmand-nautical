# Implementation Plan - Hardware Data Ingestion Subsystem Audit & Hardening

This plan addresses defects found during the audit of the USB/Serial, TCP/UDP, and NMEA parsing subsystems in the OsmAnd Nautical plugin.

## User Review Required

> [!IMPORTANT]
> The audit revealed that several requested classes (`UsbSerialConnection`, `SerialPortManager`, `NmeaUdpListener`) are currently missing from the codebase. This plan focuses on hardening the existing `TcpNmeaClient`, `AisMessageListener`, and `NmeaSentenceParser`, and implementing the missing USB Serial driver infrastructure.

> [!WARNING]
> Implementing USB Serial drivers requires dynamic permission handling (`ACTION_USB_PERMISSION`). Users will see a system prompt when connecting hardware.

## Proposed Changes

### 1. USB/Serial Hardware Driver & OTG Connection Stability

#### [NEW] [UsbNmeaClient](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/connection/UsbNmeaClient.kt)
- Implement `NmeaClient` using Android `UsbManager`.
- Add support for common chipsets (FTDI, CP2102, CH340, PL2303).
- Implement configurable baud rate (4800, 38400) and flow control.
- Add lifecycle management to handle dynamic unplugs without crashing.

#### [MODIFY] [AndroidManifest.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/AndroidManifest.xml)
- Register `ACTION_USB_DEVICE_ATTACHED` and `ACTION_USB_DEVICE_DETACHED` intent filters to trigger reconnection.

### 2. TCP / UDP Socket Stream Parsing & Sentence Multiplexing

#### [MODIFY] [AisMessageListener.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd-shared/src/commonMain/kotlin/net/osmand/shared/aistracker/AisMessageListener.kt)
- **Fix UDP Fragmentation**: Update `startUdpConnection` to buffer incoming datagram fragments. Use a line-terminator buffer to ensure full sentences are processed even if split across UDP packets.
- **Fix TCP Cleanup**: Ensure `socket.close()` is called in a way that doesn't leak file descriptors on reconnect.

#### [MODIFY] [NmeaSentenceParser.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/parser/NmeaSentenceParser.kt)
- **Implement Checksum Verification**: Add XOR-based checksum validation (`*HH`) before parsing data. Malformed packets must be discarded.
- **Support AIS Sentences**: Update to recognize sentences starting with `!` as valid.

### 3. Multi-Source Sentence Priority & Conflict Resolution

#### [MODIFY] [SailingDataAggregator.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/service/SailingDataAggregator.kt)
- **Implement Source Priority**: Add a `priorityMap` (e.g., `External USB > Bluetooth > Internal GPS`).
- **Conflict Resolution**: Only update a value if the new source has a higher or equal priority than the last source that provided that specific path within a 5-second window.

#### [MODIFY] [DirectNmeaMultiplexer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/multiplexer/DirectNmeaMultiplexer.kt)
- **Support Multiple Clients**: Update to allow multiple `NmeaClient` instances to run concurrently, passing their data to the `SailingDataAggregator`.

## Verification Plan

### Automated Tests
- `NmeaSentenceParserTest`: Add cases for fragmented sentences, malformed checksums, and AIS (`!`) sentences.
- `SailingDataAggregatorTest`: Verify that lower-priority data is ignored if higher-priority data is fresh.

### Manual Verification
- Deploy to device and connect a USB-Serial OTG cable.
- Dynamically unplug and re-plug the cable to verify stability and automatic reconnection.
- Send fragmented UDP datagrams to verify reassembly.
