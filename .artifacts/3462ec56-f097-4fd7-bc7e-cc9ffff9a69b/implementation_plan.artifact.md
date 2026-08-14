# Implementation Plan - 100% AIS & GPS Functionality

This plan addresses the remaining gaps in AIS and GPS integration to achieve full professional-grade functionality without the legacy `AisTrackerPlugin`.

## User Review Required

> [!IMPORTANT]
> I will be enhancing the NMEA parser to extract vessel metadata (names/types) and GPS quality metrics (accuracy/altitude). This ensures the "Nautical" plugin is a complete replacement for the legacy "AIS Tracker" plugin.

## Proposed Changes

### 1. AIS Metadata Enrichment (NMEA)
The `AisDecoder` currently only extracts position data. I will add support for static data messages to identify vessels by name and type.

#### [MODIFY] [AisDecoder.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/parser/AisDecoder.kt)
- Support `AISMessage05` (Static Data): Name, IMO, Callsign, Vessel Type, Dimensions, Draught, Destination.
- Support `AISMessage24` (Static Data Report): Name, Callsign, Vessel Type, Dimensions.
- Support `AISMessage19` (Extended Class B): Name, Vessel Type, Dimensions.
- Support `AISMessage21` (AtoN): Name, Aid Type, Dimensions.
- Support `AISMessage09` (SAR Aircraft): Altitude.
- Map extracted values to standard Signal K paths (e.g., `name`, `design.type`, `navigation.position.altitude`).

---

### 2. GPS Quality & Altitude Support (NMEA)
Extract accuracy and altitude from standard NMEA sentences to replace hardcoded values.

#### [MODIFY] [NmeaSentenceParser.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/parser/NmeaSentenceParser.kt)
- Implement `parseGGA`: Altitude, Geoid Separation, HDOP, Satellites.
- Implement `parseGNS`: HDOP, Satellites.
- Update `parseRMC`: Extract Magnetic Variation (field 10/11) to feed the boat's variation setting.
- Update `parseHeading`: Extract Magnetic Variation from `HDG` sentences.

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Add parsing for `navigation.gnss.antennaAltitude` to update `MarineState.altitude`.
- Add `navigation.gnss.horizontalDilution` support in `parseNavigationValue`.

---

### 3. Professional Location & Safety Loops
Bridge the new quality metrics to the system location and optimize collision checks.

#### [MODIFY] [NauticalLocationProvider.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/NauticalLocationProvider.kt)
- Inject `altitude` into the `Location` object.
- Calculate `accuracy` from `horizontalDilution` (HDOP) or use `horizontalAccuracy` if available, instead of a hardcoded 1.0m.

#### [MODIFY] [NauticalAisManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/NauticalAisManager.kt)
- Implement an **Adaptive CPA Loop**:
    - Vessel speed > 5 kts: Update every 5s.
    - Vessel speed > 2 kts: Update every 10s.
    - Stationary: Update every 20s.

## Verification Plan

### Automated Tests
- Extend `NmeaSentenceParserTest.kt` to verify GGA/GNS parsing and variation extraction.
- Add unit tests for `AisDecoder` with sample Message 5 and 24 sentences.

### Manual Verification
- **AIS Labels**: Verify that vessels on the map now show their names and correct icons (e.g., Cargo vs Sailing) when using Bluetooth NMEA.
- **GPS Info**: Check that Altitude and Satellite count are displayed correctly in map widgets.
- **Accuracy**: Verify that the blue GPS circle expands/contracts based on the NMEA HDOP value.
