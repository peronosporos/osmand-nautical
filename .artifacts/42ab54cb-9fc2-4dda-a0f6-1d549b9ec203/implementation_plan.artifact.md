# PHASE 8.0D: CORE SAFETY, MATHEMATICAL & INFRASTRUCTURE REMEDIATION

This phase focuses on critical remediation across multiple nautical components, addressing architectural flaws in I/O, autopilot reconciliation, hardware identification, mathematical corrections, and visual smoothing for WearOS.

## User Review Required

> [!IMPORTANT]
> - **Autopilot Reconciliation**: Commands will now enter a `PENDING_RECONCILIATION` state. If Signal K does not confirm the change within 3000ms, the UI will revert and an audible alarm will sound.
> - **Hardware ID (S-63)**: The HWID generation is being moved to Android KeyStore to ensure it survives app data wipes, which is critical for S-63 license persistence.
> - **NMEA Checksum Enforcement**: SENTENCES WITHOUT CHECKSUMS WILL BE REJECTED. Ensure all connected hardware provides valid checksums.

## Proposed Changes

### 1. Navtex & Native VHF Layer Optimization

#### [MODIFY] [VhfPoiSearchLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/poi/ui/VhfPoiSearchLayer.kt)
- Move `searchMapIndex()` out of `onDraw()` to a background coroutine on `Dispatchers.IO`.
- Implement a cache (`vhfObjectsCached`) to avoid redundant disk I/O.
- Add an interactive click listener to the VHF context menu item to copy the channel to the clipboard.

#### [MODIFY] [NavtexRepository.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/data/NavtexRepository.kt)
- Wrap database operations in `upsertMessage` inside a transaction (`beginTransaction`, `setTransactionSuccessful`, `endTransaction`) to prevent UI stuttering during message bursts.

#### [MODIFY] [NavtexMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/ui/NavtexMapLayer.kt)
- Implement viewport bounding-box clipping in `onDraw()` so polygons and markers are only processed if they are visible on the screen.

---

### 2. Closed-Loop Autopilot Reconciliation & Configurable Actuator Limit

#### [MODIFY] [AutopilotController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotController.kt)
- Track pending heading and mode commands.
- Implement 3000ms timeout logic for confirmation deltas.
- Integrate with `NauticalAudioArbiter` to fire "Autopilot Command Rejected" alerts on timeout.

#### [MODIFY] [ActuatorLoadWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/ActuatorLoadWidget.kt)
- Bind the visual alarm threshold to the `NAUTICAL_ACTUATOR_ALARM_THRESHOLD` setting.

---

### 3. Persistent Hardware ID for S-63 Licensing

#### [MODIFY] [S63PermitGenerator.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/s63/crypto/S63PermitGenerator.kt)
- Refactor `generateHWID()` to use a KeyStore-backed unique ID that survives app data wipes and cache clears.

---

### 4. Mathematical Corrections & Connection Resiliency

#### [MODIFY] [LaylineMathEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/laylines/engine/LaylineMathEngine.kt)
- Update `calculateApparentLaylines` to apply `magneticVariation` from `MarineState` when calculating tactical angles.

#### [MODIFY] [SignalKWebSocketClient.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/network/SignalKWebSocketClient.kt)
- Implement exponential backoff for reconnection on connection failures (1s to 30s ceiling).

#### [MODIFY] [NmeaSentenceParser.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/nmea/parser/NmeaSentenceParser.kt)
- Enforce strict checksum validation: reject sentences with missing or invalid checksums.

---

### 5. Visual Smoothing & WearOS AMOLED Protection

#### [MODIFY] [HeadingArcView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/HeadingArcView.kt)
- Implement EMA (Exponential Moving Average) filtering for `actualHeading` and `windAngleApparent` to eliminate needle jitter.

#### [NEW] [NauticalCompassWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalCompassWidget.kt)
- Create a standalone nautical compass widget with smoothed needle movement and adaptive UI.

#### [MODIFY] [WearOsNauticalManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/WearOsNauticalManager.kt)
- Implement `AmbientCallback` handling to switch to high-contrast B&W mode during Always-On Display.

#### [MODIFY] [HeartbeatHudView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/HeartbeatHudView.kt)
- Add support for pure black-and-white, non-animated rendering in ambient mode.

## Verification Plan

### Automated Tests
- `NavtexRepositoryTest`: Verify transaction atomicity and performance during bursts.
- `LaylineMathEngineTest`: Verify magnetic declination corrections.
- `NmeaSentenceParserTest`: Verify strict checksum enforcement.

### Manual Verification
- Deploy to WearOS and verify Ambient Mode transition (Display switches to B&W).
- Verify Autopilot command timeout by disabling Signal K deltas.
- Verify VHF POI search responsiveness on map pan.
- Verify S-63 permit persistence after app data clear (requires KeyStore mock or device testing).
