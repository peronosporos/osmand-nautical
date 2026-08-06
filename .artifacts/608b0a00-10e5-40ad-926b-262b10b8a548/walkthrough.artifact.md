# Walkthrough - Architectural Fixes & Network Consolidation

I have implemented critical architectural fixes, completed several functional stubs, and consolidated network communication for the OsmAnd Nautical plugin.

## Key Changes

### 1. Network & Telemetry Consolidation
- **Single WebSocket Source**: Removed the duplicate WebSocket connection from `SailingPerformanceRepository`. All Signal K telemetry now flows through the central `SignalKEngine` and is dispatched via `SignalKDataBroker`.
- **Dynamic Self-Identity**: `SignalKEngine` now dynamically resolves the own-ship identity (MMSI/Name) using the Signal K REST API if not provided in the initial handshake.
- **Connection Feedback**: Added `CONNECTING` status to `ConnectionStatus` and ensured it is reflected in the HUD during initialization or reconnect attempts.

### 2. Functional Wiring & UI Integration
- **Weather Optimized Routing**: Users can now trigger a Weather Route calculation from the Map Context Menu. The result is displayed on the map via a new `WeatherRoutingMapLayer` (via `SailingMapLayerController`).
- **Automated Workflows**: Implemented `WorkflowHeaderView` and bound it to `SailingWorkflowEngine`. Users are prompted with a HUD banner to confirm automated mode transitions (e.g., Tactical Passage to Close-Quarters).
- **Background Watchdog Sync**: `NauticalAnchorQuickAction` now forces an immediate update of the `NavigationService` watchdog, ensuring the anchor alarm is active even if the app is put into the background immediately after setting the anchor.

### 3. UX & Interaction Fixes
- **Map Touch Dead Zones**: Refactored `NavtexMapLayer` to implement `IContextMenuProvider`. This prevents Navtex markers and polygons from consuming all touch events, allowing users to select underlying S-57 chart objects or other POIs.
- **Resource Management**: Implemented explicit `clear()` methods on `LaylineViewModel`, `DeadReckoningViewModel`, and `NavtexViewModel`. These are called when features are toggled off or the plugin is disabled, ensuring background coroutines are properly cancelled.

## Verification Results

### Telemetry Flow
Verified that `SailingPerformanceRepository` correctly receives STW, TWS, and Polar data from the `SignalKDataBroker` without maintaining its own socket.

### Context Menu Interop
Verified that long-pressing a Navtex polygon now brings up the OsmAnd object selection sheet, listing both the Navtex message and any underlying S-57 features.

### Workflow Automation
Verified that the `SailingWorkflowEngine` emits a proposal when conditions change (simulated telemetry), and the "Confirm" button in the HUD correctly applies camera automation.

> [!TIP]
> To test weather routing, ensure a GRIB file is loaded and a polar profile is active in the Sailing Performance settings.
