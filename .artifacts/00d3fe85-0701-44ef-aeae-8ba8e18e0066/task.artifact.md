# Task List - Hardware Data Ingestion Subsystem Hardening

- [x] Hardening `NmeaSentenceParser.kt`
    - [x] Implement XOR checksum validation.
    - [x] Support `!` prefix for AIS.
- [x] Hardening `AisMessageListener.kt` (KMP)
    - [x] Implement line buffering for UDP to handle fragmented sentences.
- [x] Hardening `SailingDataAggregator.kt`
    - [x] Implement source priority mapping.
    - [x] Update `handleDelta` to respect priority and staleness.
- [x] Update `DirectNmeaMultiplexer.kt`
    - [x] Support multiple active clients.
- [x] Implement USB Serial Infrastructure
    - [x] Create `UsbNmeaClient.kt`.
    - [x] Add USB intent filters to `AndroidManifest.xml`.
- [x] Verification
    - [x] Add unit tests for NMEA parsing and Aggregator priority.
