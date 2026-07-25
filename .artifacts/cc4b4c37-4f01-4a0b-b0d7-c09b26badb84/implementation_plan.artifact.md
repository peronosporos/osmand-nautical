# Implementation Plan - Signal K Delta Ingestion Audit & Fixes

Audit and remediate defects in the Signal K delta ingestion pipeline to ensure spec compliance, unit accuracy, and handling of custom telemetry paths.

## User Review Required

> [!IMPORTANT]
> - `SignalKUnitConverter` will be introduced as a centralized utility for all SI to OsmAnd unit conversions.
> - `MarineState` will now include a `customValues` map to support dynamic Signal K paths.
> - `SignalKDataBroker`'s `angleThreshold` will be corrected to use Radians, as Signal K transmits heading in Radians.

## Proposed Changes

### [Nautical Plugin Engine]

#### [MODIFY] [SignalKDataBroker.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKDataBroker.kt)
- Fix `angleThreshold` bug by converting the degree threshold (2.0) to radians.

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Enhance `trueSelfContext` initialization to handle cases where `self` identifier arrives late or is missing.
- Implement `meta` field processing to capture unit and display hints from Signal K.
- Update `parseTelemetryValue` to catch unknown/custom paths and store them in `MarineState.customValues`.

#### [MODIFY] [MarineState.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/MarineState.kt)
- Add `customValues: Map<String, Double>` to store telemetry from non-hardcoded Signal K paths.
- Add `pathMeta: Map<String, Map<String, Any>>` to store Signal K metadata.

#### [NEW] [SignalKUnitConverter.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKUnitConverter.kt)
- Centralized utility for converting SI units (Signal K standard) to user-preferred OsmAnd units.

### [Nautical UI]

#### [MODIFY] [MarineTextWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/MarineTextWidget.kt)
- Refactor `updateSimpleWidgetInfo` to use `SignalKUnitConverter` instead of hardcoded math.
- Add support for displaying custom paths from `MarineState.customValues`.

#### [MODIFY] [NauticalDataBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalDataBottomSheet.kt)
- Update `updateGraphData` to use `SignalKUnitConverter`.

## Verification Plan

### Automated Tests
- Create unit tests in `SignalKRepositoryTest.kt` to verify:
  - Custom path ingestion into `customValues`.
  - SI to OsmAnd unit conversion accuracy.
  - Context filtering for `self` vs other vessels.

### Manual Verification
- Deploy to device/emulator.
- Simulate Signal K delta stream with custom paths (e.g., `electrical.batteries.house.voltage`).
- Verify that the custom data appears in widgets and respects unit changes in OsmAnd settings.
