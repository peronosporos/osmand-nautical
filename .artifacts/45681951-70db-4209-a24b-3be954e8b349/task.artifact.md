# Task List - Fix Nautical Plugin Errors and Warnings

- [x] Fix Nautical Plugin Core
    - [x] `S57SpatialIndex.kt`: Add `close()` method
    - [x] `NauticalPlugin.kt`: Replace `clearCache()` with `close()`
- [x] Fix Nautical Engine
    - [x] `MarineState.kt`: Remove unused property and add trailing comma
    - [x] `SignalKDataBroker.kt`: Clean up types and expressions
    - [x] `SailingWorkflowEngine.kt`: Restore logic and modernize
    - [x] `OkHttpSignalKConnection.kt`: Restore `isConnecting()`
    - [x] `SailingDataAggregator.kt`: Add trailing comma and clarifying parentheses
- [x] Restore UI & ViewModels
    - [x] `RoutingViewModel.kt`: Restore weather routing logic
    - [x] `SailingMapLayerController.kt`: Restore `setWeatherRoute`
    - [x] `SignalKRestService.kt`: Restore `getSelfIdentity`
- [x] Verification
    - [x] Analyze fixed files
