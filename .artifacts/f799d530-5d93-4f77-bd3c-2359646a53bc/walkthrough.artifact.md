# Walkthrough: AIS Tracking Integration in Nautical Plugin

I have merged the AIS tracking functionality into the Nautical plugin, transitioning the logic to Kotlin and utilizing SignalK as the primary data source. This allows for a fully independent and modern AIS experience within the Nautical plugin.

## Changes

### Core Engine & Logic
- **[NEW] [NauticalAisManager](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/NauticalAisManager.kt)**: A Kotlin port of the AIS data management logic. It handles target collection, cleanup of lost objects, and CPA (Closest Point of Approach) calculations.
- **[MODIFY] [SignalKEngine](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)**: Updated to populate `AisObject` structures directly from incoming SignalK updates. It now serves as the primary source for AIS targets.
- **[MODIFY] [NauticalAudioArbiter](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/audio/NauticalAudioArbiter.kt)**: Added `COLLISION_DANGER` alarm type to handle AIS collision alerts natively.

### UI & Rendering
- **[NEW] [NauticalAisLayer](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/NauticalAisLayer.kt)**: A Kotlin implementation of the map layer for AIS targets, supporting native rendering via `MapMarkersCollection`.
- **[NEW] [NauticalAisObjectDrawable](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/NauticalAisObjectDrawable.kt)**: Handles the visual representation of individual AIS targets, including vessel icons, rotation, and predictor lines.
- **[MODIFY] [AisTargetBottomSheet](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/AisTargetBottomSheet.kt)**: Updated to retrieve information from the new `NauticalAisManager`.

### Configuration
- **[MODIFY] [NauticalSettingsFragment](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalSettingsFragment.kt)**: Integrated AIS settings (CPA warning distance/time, own MMSI, timeouts) directly into the Nautical plugin settings.
- **[MODIFY] [nautical_settings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/xml/nautical_settings.xml)**: Added the corresponding UI elements for AIS configuration.

### Cleanup
- **[DELETE] AisCollisionBridge.kt**: Removed the redundant bridge between plugins.
- **[DELETE] AisUdpEmitter.kt**: Removed the NMEA broadcast mechanism as Nautical now handles tracking internally.
- **[DELETE] AisEncoder.kt**: Removed as it was no longer needed for SignalK-to-NMEA conversion in this context.
- **[MODIFY] [MarineState.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/MarineState.kt)**: Removed the legacy `AisTarget` class in favor of the shared `AisObject`.

## Verification Results

### Logic & Integration
- Verified that `SignalKEngine` correctly parses AIS-related paths from SignalK deltas and notifies the `NauticalAisManager`.
- Verified that `NauticalAisManager` implements the same cleanup and CPA logic as the original plugin, but using Kotlin-friendly constructs.
- Verified that settings are correctly registered and handled in the Nautical plugin's lifecycle.

### UI & UX
- The new `NauticalAisLayer` is registered with a higher priority (3.5f) to ensure AIS targets are visible above the map but below critical tactical overlays.
- Tactical dimming for non-danger targets is supported in "Close Quarters" workflow mode.
- Bottom sheet displays enriched SignalK information when available.
