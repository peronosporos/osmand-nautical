# Implementation Plan - 100% Professional Nautical Plugin (Revised)

This plan outlines the steps required to elevate the OsmAnd Nautical Plugin to a 100% professional benchmark. This revision incorporates user feedback: maintaining support for non-secure (HTTP/WS) connections while adding professional-grade integrity and logging.

## User Review Required

> [!NOTE]
> **Legacy Support Maintained**
> Strict TLS enforcement has been removed. All state mutation commands (Autopilot, Switching) will continue to work over non-encrypted connections to ensure compatibility with standard on-board Signal K setups.

> [!TIP]
> **Dependency Integration**
> We will add `jose4j` for robust JWT handling. This library is lightweight and will not impact runtime performance (CPU/RAM) as it is only invoked during authentication handshakes and command dispatch.

## Proposed Changes

### 1. Security & Integrity Handening
Focusing on reliability and forensic traceability without breaking existing setups.

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Upgrade `validateJwtToken` using `jose4j` for structural and expiration validation.
- Implement high-precision ISO-8601 parsing for server-provided timestamps to ensure sensor data alignment.

#### [MODIFY] [NauticalLog.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/utils/NauticalLog.kt)
- Implement a persistent **Audit Log** for all `dispatchCommand` calls. This records *what* was sent and *when*, essential for professional insurance requirements.

---

### 2. Temporal & Movement Resilience
Ensuring the UI remains fluid even during network jitter or sensor failure.

#### [MODIFY] [SignalKDataBroker.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKDataBroker.kt)
- Implement a **Dead Reckoning Bridge**: If the WebSocket stream drops for <3 seconds, interpolate the vessel's position on the map using the last known COG and SOG.

---

### 3. Professional Safety & Hardware Logic
Finalizing specialized maritime algorithms.

#### [MODIFY] [AnchorDriftWatchdog.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/AnchorDriftWatchdog.kt)
- **TASK-110**: Implement scope-aware drag detection. Adjust the alarm radius based on the ratio of rode length to (water depth + freeboard).

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- **TASK-301/302**: Finalize hardware volume button mapping for **Physical MOB** and **Alarm Acknowledgment**.
- **TASK-047**: Integrate **Workflow Touch Lock** into the `ManeuverManager` to prevent accidental map shifts during critical docking/tacking procedures.

#### [MODIFY] [AnchorWatchDialogFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/anchor/AnchorWatchDialogFragment.kt)
- **TASK-049**: Add UI capability to manually adjust the anchor "drop point" by dragging a marker on the map.

---

### 4. Interoperability & Standards
Ensuring the plugin plays well with the professional ecosystem.

#### [MODIFY] [GpxStreamer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/GpxStreamer.kt)
- **TASK-052**: Implement the full maritime XML extension for GPX exports (preserving depth, wind, and water temperature).

#### [MODIFY] [LogbookCsvExporter.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/logbook/export/LogbookCsvExporter.kt)
- **TASK-024/095/098**: Finalize CSV formatting with UTF-8 BOM and locale-aware decimal handling for Excel compatibility.

#### [MODIFY] [SignalKResourceManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKResourceManager.kt)
- **TASK-03.4**: Implement two-way route synchronization between Signal K server resources and OsmAnd's internal route helper.

---

### 5. Tactical Refinement
Optimizing core sailing math.

#### [MODIFY] [PolarDiagram.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/PolarDiagram.kt)
- **TASK-012**: Implement **Golden Section search** refinement for polar speed calculations to find the true optimal VMG angle.

#### [MODIFY] [SignalKRasterLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/raster/SignalKRasterLayer.kt)
- **TASK-006**: Implement **Double-Buffering** for the raster engine to eliminate flickering during rapid zooming.

## Verification Plan

### Automated Tests
- `SignalKDataBrokerDeadReckoningTest.kt`: Verify interpolation logic during mock drops.
- `PolarMathTest.kt`: Compare Golden Section results against sampled polar data.
- `AuditLogIntegrityTest.kt`: Ensure logs are correctly written to internal storage.

### Manual Verification
1.  Verify Physical MOB trigger (Vol Up) while in Boat mode.
2.  Test CSV export opening correctly in Microsoft Excel (BOM check).
3.  Observe zero-flicker map panning on the Signal K raster layer.
