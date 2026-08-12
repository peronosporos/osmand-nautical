# SignalK "Full Frontend" Completion Plan

Final functional enhancements to achieve 100% "Full Frontend" and "Orchestrator" capability. This plan focuses on dynamic data refresh, weather-integrated routing, and advanced management UI.

## User Review Required

> [!IMPORTANT]
> The Orchestrator Dashboard will now include a **WebView fallback**. This allows users to access complex SignalK plugin settings (like advanced weather routing parameters) natively within OsmAnd using the server's web interface.

## Proposed Changes

### [Backend/Routing] Weather-Integrated Pathfinding

#### [MODIFY] [SignalKRouteProvider.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/routing/SignalKRouteProvider.kt)
- **Enhanced GeoJSON Extraction:** Parse `properties` from the SignalK LineString to extract per-waypoint weather (TWS, Wave height, Wind Angle).
- **GPX Extension Injection:** Store these metrics as custom extensions in the OsmAnd `WptPt` list.

#### [MODIFY] [RoutingModels.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/routing/model/RoutingModels.kt)
- Add `windSpeedMs`, `windAngleRad`, and `waveHeightM` fields to `PassagePlanLeg`.

### [Backend] Live Resource Sync

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- **Post-Action Refresh:** Automatically trigger `capabilityManager.probe()` and `refreshIsochrones()` after a routing calculation is successfully triggered.
- **Delta Resource Listener:** Monitor the WebSocket delta stream for `resources.*` updates to refresh map layers without manual interaction.

### [Frontend] Advanced Orchestrator Dashboard

#### [MODIFY] [SignalKOrchestratorFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/SignalKOrchestratorFragment.kt)
- **Active Polar Selector:** Add a "Change Polar" option that fetches available polars from `/resources/polars` and allows switching the active profile.
- **WebView Integration:** For plugins with complex UIs (like RouteIQ or Winga), add a "Configure" button that opens a native OsmAnd `WebViewEx` pointing to the plugin's local URL on the SignalK server.
- **Stability Monitoring:** Display a "Recording Stability" badge with real-time data from `performance.recordingStability`.

#### [MODIFY] [NauticalRouteSummaryFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/routing/ui/NauticalRouteSummaryFragment.kt)
- **Conditions Display:** Add new columns/icons to the passage plan table to show forecasted Wind and Waves for each leg.

---

## Verification Plan

### Automated Tests
- `WeatherGpxTest`: Verify that weather properties in GeoJSON correctly map to `WptPt` extensions.

### Manual Verification
- Plan a route and open "Route Info" (Passage Plan) to see weather conditions for each leg.
- Trigger a RouteIQ scan from the Dashboard and verify the isochrones appear on the map automatically.
- Open the "Configure" WebView for a plugin and verify it loads the SignalK plugin UI correctly.
