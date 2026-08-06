# Implementation Plan: Merge AIS Tracker into Nautical Plugin

The goal is to merge the AIS tracking functionality from the "AIS Tracker" plugin into the "Nautical" plugin, using Kotlin and SignalK as the primary data source. This will provide a fully functional AIS tracking experience within the Nautical plugin without requiring the AIS Tracker plugin to be activated.

## User Review Required

> [!IMPORTANT]
> The AIS Tracker plugin logic is primarily based on NMEA sentences. The Nautical plugin's AIS logic will transition to using SignalK data as the primary source, as requested. I will ensure that the SignalK AIS data is correctly mapped to the `AisObject` structure used for rendering and CPA calculations.

> [!WARNING]
> This plan involves moving and refactoring code from the `net.osmand.plus.plugins.aistracker` package to `net.osmand.plus.plugins.nautical`. The original AIS Tracker plugin will be effectively superseded for Nautical users.

## Proposed Changes

### [Nautical Plugin Engine]

#### [NEW] [NauticalAisManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/NauticalAisManager.kt)
- Port `AisTrackerPlugin.AisDataManager` logic to Kotlin.
- Manage `AisObject` collection.
- Implement cleanup and CPA calculation timers.
- Handle audio alerts using a ported `NauticalAisAudioAlertManager`.

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Integrate `NauticalAisManager`.
- Update `processUpdates` and `updateAisTarget` to populate `AisObject`s in `NauticalAisManager` instead of the local `aisCache` / `AisTarget`.
- Use SignalK's `navigation.closestApproach` if available, or fall back to local CPA calculations in `NauticalAisManager`.

#### [MODIFY] [MarineState.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/MarineState.kt)
- Remove `AisTarget` class if it's no longer used, or keep it as a simple data holder if necessary. Prefer `AisObject`.

### [Nautical Plugin UI & Rendering]

#### [NEW] [NauticalAisLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/NauticalAisLayer.kt)
- Port `AisTrackerLayer` to Kotlin.
- Render `AisObject`s from `NauticalAisManager`.
- Integrate with Nautical-specific tactical dimming if applicable.

#### [NEW] [NauticalAisObjectDrawable.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/NauticalAisObjectDrawable.kt)
- Port `AisObjectDrawable` to Kotlin.
- Handle icon selection, rotation, and predictor lines.

#### [MODIFY] [AisTargetBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/AisTargetBottomSheet.kt)
- Update to use the new `NauticalAisManager` for retrieving AIS information.

### [Nautical Plugin Core]

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Manage the lifecycle of `NauticalAisManager`.
- Register `NauticalAisLayer`.
- Add AIS-related preferences (CPA distance, warning time, own MMSI, etc.).

### [Cleanup]

#### [DELETE] [AisCollisionBridge.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/bridge/AisCollisionBridge.kt)
- Redundant once AIS logic is fully integrated into the engine and manager.

#### [DELETE] [AisEncoder.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AisEncoder.kt) & [AisDecoder.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/parser/AisDecoder.kt)
- If NMEA to SignalK delta conversion is no longer desired in Nautical, these can be removed as requested.

## Verification Plan

### Automated Tests
- Since I cannot run full instrumentation tests easily, I will verify the logic by:
  - Checking compilation (if possible via `analyze_file` on critical parts).
  - Verifying SignalK parsing logic with unit tests (if applicable/existing).

### Manual Verification
- Deploy to device/emulator.
- Connect to a SignalK server (or use simulated data).
- Verify that AIS targets appear on the map within the Nautical plugin.
- Verify that AIS settings are available in Nautical plugin settings.
- Verify that CPA warnings work as expected.
