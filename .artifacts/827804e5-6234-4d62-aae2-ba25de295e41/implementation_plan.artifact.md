# Implementation Plan - Comprehensive Signal K Feature Set

This plan outlines the final phase of Signal K integration, implementing all candidate plugins and completing partial implementations. The focus is on seamless integration with existing OsmAnd layers (Raster, AIS, Widgets) to avoid code duplication and fragmentation.

## User Review Required

> [!WARNING]
> **Video Streaming**: Implementing the ONVIF camera overlay requires significant battery and data usage. We will restrict this to "Manual Activation" only.
>
> **Control Safety**: Windlass control will be implemented with a "Hold-to-Action" safety mechanism to prevent accidental anchoring at speed.

## Proposed Changes

### 1. Unified Raster & Radar Overlay
Instead of separate layers, we will implement a configurable Signal K Raster provider.

#### [NEW] [SignalKRasterLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/raster/SignalKRasterLayer.kt)
- A dynamic layer that can switch between **Radar Sweeps**, **Rain Radar**, and **Server Charts**.
- Fetches tiles from Signal K REST endpoints (`/signalk/v2/api/resources/charts/{id}`).
- Integrates with the "Configure Map" menu under a new "Signal K Overlays" section.

---

### 2. Vessel Logbook & Targets

#### [NEW] [SignalKLogbookLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/SignalKLogbookLayer.kt)
- Displays server-side log entries (`meri-imperiumi/logbook`) on the map.
- Uses a distinct "Log" icon. Tapping entries opens the OsmAnd context menu with the log description.

#### [MODIFY] [NauticalAisLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/NauticalAisLayer.kt)
- Enhance the layer to support "Virtual AIS Targets" from `signalk-vessels-to-ais`.
- These targets will be rendered with a "Virtual" badge or dashed outline to distinguish them from local VHF AIS.

---

### 3. Astro & Environmental Data

#### [MODIFY] [SunriseSunsetWidget.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/SunriseSunsetWidget.java)
- If the Nautical plugin is active and Signal K has `environment.moon.phase`, the widget will cycle through: Sunrise -> Sunset -> Moon Phase.

---

### 4. Control & Safety Systems

#### [MODIFY] [NauticalPilotBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotBottomSheet.kt)
- Add a **"Systems"** tab.
- Implement **Windlass Control**: "Up" and "Down" buttons with hold-to-confirm logic.
- Implement **Checklist View**: A button to open the server-side safety checklists.

#### [NEW] [NauticalChecklistFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/NauticalChecklistFragment.kt)
- A dedicated fragment to display and check off items in Signal K checklists.

---

### 5. Floating Camera HUD

#### [NEW] [NauticalCameraWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalCameraWidget.kt)
- A specialized map widget that opens a small PIP (Picture-in-Picture) window.
- Connects to ONVIF/RTSP streams provided by `signalk-onvif-camera`.

---

## Verification Plan

### Automated Tests
- **SignalKRestServiceTest**: Add tests for `/resources/logbook` and `/resources/checklists`.
- **RasterProviderTest**: Mock Signal K tile responses and verify `SignalKRasterLayer` handles zoom levels correctly.

### Manual Verification
1. **Radar Overlay**: Enable "Signal K Radar" in Map Configuration and verify sweep tiles appear.
2. **Windlass**: Long-press "Anchor Down" and verify the `PUT` request is sent to `electrical.switches.windlass.down`.
3. **Camera**: Trigger the Camera Widget and verify it opens a placeholder (or stream if available).
4. **Logbook**: Create a log entry on the server and verify it appears at the correct coordinate in OsmAnd.
