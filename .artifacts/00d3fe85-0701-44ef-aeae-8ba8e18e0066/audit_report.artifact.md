# Nautical Hardware Ingestion Subsystem Audit Report

## 1. USB/Serial Hardware Connection Stability

| File Name & Method | Specific Hardware / Serial Bug | Exact Fix (Proposed) |
| :--- | :--- | :--- |
| `UsbSerialConnection` (Missing) | Subsystem missing. No native Android USB serial driver implementation exists for FTDI/CH340/etc. | Implement `UsbNmeaClient` using `UsbManager` and `UsbSerialDriver` patterns. |
| `AndroidManifest.xml` | Missing dynamic OTG connection handlers. Cable plug events will not be detected. | Add `<intent-filter>` for `android.hardware.usb.action.USB_DEVICE_ATTACHED`. |
| `NmeaClient` Interface | No baud rate or flow control configuration. | Add `baudRate` and `flowControl` parameters to the interface or specific implementations. |

## 2. TCP / UDP Socket Stream Parsing & Sentence Buffering

| File Name & Method | Specific Hardware / Serial Bug | Exact Fix (Proposed) |
| :--- | :--- | :--- |
| `AisMessageListener.kt` `startUdpConnection` | **UDP Fragmentation**: `datagram.packet.readText()` assumes one full sentence per datagram. Sentences split across packets are lost. | Use a persistent `StringBuilder` buffer. Only process lines ending in `\n` or `\r\n`. |
| `NmeaSentenceParser.kt` `parse` | **Packet Corruption**: No checksum (`*HH`) verification. Corrupted serial data will cause invalid state updates. | Implement `validateChecksum(content: String, hex: String)` and discard if mismatch. |
| `NmeaSentenceParser.kt` `parse` | **AIS Incompatibility**: Only checks for `$` start character. Valid AIS sentences starting with `!` are ignored. | Update prefix check: `if (!sentence.startsWith("$") && !sentence.startsWith("!"))`. |

## 3. Multi-Source Sentence Priority & Conflict Resolution

| File Name & Method | Specific Hardware / Serial Bug | Exact Fix (Proposed) |
| :--- | :--- | :--- |
| `SailingDataAggregator.kt` `handleDelta` | **Multiplexer Conflict**: Duplicate data from concurrent sources (e.g. Signal K vs NMEA) overwrites greedily. | Add `sourcePriorityMap`. Check `lastSource` and `lastTimestamp` before updating. |
| `DirectNmeaMultiplexer.kt` `start` | **Concurrency Limitation**: Only supports one `currentClient`. Stops previous client when a new one starts. | Replace `currentClient` with `activeClients: MutableList<NmeaClient>`. Collect from all. |
