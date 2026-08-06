# Implementation Plan - Architectural Fixes & Integration Completion

This plan addresses critical architectural issues, completes stubs, and ensures proper resource management across the OsmAnd Nautical plugin.

## User Review Required

> [!IMPORTANT]
> **Network Consolidation**: Consolidating all WebSocket communication into `SignalKEngine` will reduce battery usage and network overhead. `SailingPerformanceRepository` will now act as a consumer of the central engine's data instead of maintaining its own connection.

> [!WARNING]
> **Map Touch Dead Zones**: Refactoring `NavtexMapLayer` to use `IContextMenuProvider` is a breaking change to its interaction model but is necessary to allow users to select underlying S-57 objects and other POIs.

## Proposed Changes

### 1. Network Consolidation & Telemetry Refactoring

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Update `handleIncomingMessage` to dynamically resolve self-identity via `SignalKRestService.getSelfIdentity()` if MMSI is unknown.
- Ensure all performance paths (STW, TWS, TWA, etc.) are correctly routed to `SignalKDataBroker`.

#### [MODIFY] [SignalKDataBroker.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKDataBroker.kt)
- Add StateFlows for all telemetry required by `SailingPerformanceRepository` (STW, TWS, TWA, SOG, COG, Polar Ratio).

#### [MODIFY] [SailingPerformanceRepository.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/repository/SailingPerformanceRepository.kt)
- **DELETE** the internal `SignalKWebSocketClient` usage.
- Update `startListening()` to observe `SignalKDataBroker` flows and update `_livePerformanceData`.

---

### 2. Stub Completion & Feature Wiring

#### [MODIFY] [OkHttpSignalKConnection.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/OkHttpSignalKConnection.kt)
- Ensure `isConnecting` state is accurately reflected and exposed.

#### [MODIFY] [RoutingViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/viewmodel/RoutingViewModel.kt)
- Wire `calculateWeatherRoute` to be triggered from UI actions.

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Initialize `SailingWorkflowEngine`.
- Bind `confirmPendingWorkflow` to a HUD banner notification.
- Implement explicit `clear()` calls for nautical ViewModels to prevent coroutine leaks.

---

### 3. UX & Layout Improvements

#### [MODIFY] [NavtexMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/hazard/ui/NavtexMapLayer.kt)
- Implement `IContextMenuProvider`.
- Remove `onSingleTap` consumption to prevent "dead zones" on the map.

#### [MODIFY] [NauticalAnchorQuickAction.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/quickaction/NauticalAnchorQuickAction.kt)
- Invoke `updateNauticalBackgroundService()` upon toggling the anchor to ensure immediate watchdog activation.

## Verification Plan

### Automated Tests
- Verify that `SignalKEngine` correctly parses and dispatches all telemetry paths to the broker.
- Verify that `SailingPerformanceRepository` updates its state when the broker emits new data.

### Manual Verification
- Deploy to device and verify "Connecting..." status in HUD during initialization.
- Test long-press on Navtex polygons to ensure both Navtex details and underlying map objects are selectable.
- Verify that disabling Nautical features in settings instantly kills associated background coroutines (checked via logs/debugger).
- Trigger an anchor watch via QuickAction and verify that the `NavigationService` starts immediately.
