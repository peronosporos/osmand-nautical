# Implementation Plan - Phase 8.0P: Accessibility, RTL & Offline Resilience

Fix RTL layout collapses, colorblind accessibility gaps, infinite network timeouts, and offline startup ANR vulnerabilities.

## User Review Required

> [!IMPORTANT]
> The "EXPIRED FORECAST" banner will be displayed if the GRIB data's latest timestep is older than 24 hours from the current device time.
> Geometric hatching will be used for Urgent NAVTEX messages to distinguish them from regular ones without relying solely on color.

## Proposed Changes

### 1. RTL & Accessibility

#### [MODIFY] [nautical_pilot_bottom_sheet.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/nautical_pilot_bottom_sheet.xml)
-   Audit and replace any remaining `layout_marginLeft/Right` with `layout_marginStart/End`.

#### [MODIFY] [bottom_sheet_nautical_data.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/bottom_sheet_nautical_data.xml)
-   Replace `paddingLeft/Right` with `paddingStart/End`.

#### [MODIFY] [NavtexMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/ui/NavtexMapLayer.kt)
-   Implement `hatchPaint` with a `BitmapShader` for geometric hatching of urgent hazard polygons.
-   Update `drawWarningMarker` to add an exclamation badge (`!`) inside or beside the triangle for urgent markers.
-   Use different hatching angles or densities for different hazard subjects to further support colorblind operators.

---

### 2. Network Timeouts & Expired Forecast Banners

#### [MODIFY] [SailingDependencyContainer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/di/SailingDependencyContainer.kt)
-   Update `okHttpClient` to have 10s read timeout (keep 5s connect timeout).

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
-   Ensure `createHttpClient` uses 5s connect and 10s read timeouts.
-   Wrap `startEngine` and other network-dependent starts in `withTimeoutOrNull(5000)`.

#### [MODIFY] [OceanographicGribMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/map/layers/OceanographicGribMapLayer.kt)
-   Add logic in `onDraw` to check the age of the loaded GRIB data.
-   Render a high-contrast "EXPIRED FORECAST" banner if the data is older than 24 hours.

---

### 3. Asynchronous Offline Startup

#### [MODIFY] [SignalKDiscoveryManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/discovery/SignalKDiscoveryManager.kt)
-   Move `nsdManager.discoverServices` and resolution logic to execute with a 5s timeout bound to prevent blocking during startup in offline scenarios.

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
-   Audit `resolveSelfIdentity` and ensure it has a strict timeout.
-   Ensure all `InetAddress.getByName` (if any added) are wrapped in `Dispatchers.IO` with timeouts.

---

## Verification Plan

### Automated Tests
-   Verify build succeeds after XML and Kotlin changes.

### Manual Verification
-   Switch device language to an RTL language (e.g., Arabic) and verify nautical bottom sheets mirror correctly.
-   Test with simulated expired GRIB data to verify the "EXPIRED FORECAST" banner.
-   Test app startup with WiFi/Data disabled to ensure no ANRs occur during SignalK discovery.
